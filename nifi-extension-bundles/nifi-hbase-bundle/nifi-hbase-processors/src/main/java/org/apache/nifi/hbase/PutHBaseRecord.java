/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.hbase;

import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.SupportsBatching;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.components.Validator;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.hbase.put.PutColumn;
import org.apache.nifi.hbase.put.PutFlowFile;
import org.apache.nifi.hbase.util.VisibilityUtil;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.record.path.FieldValue;
import org.apache.nifi.record.path.RecordPath;
import org.apache.nifi.record.path.RecordPathResult;
import org.apache.nifi.record.path.util.RecordPathCache;
import org.apache.nifi.serialization.RecordReader;
import org.apache.nifi.serialization.RecordReaderFactory;
import org.apache.nifi.serialization.record.Record;
import org.apache.nifi.serialization.record.RecordField;
import org.apache.nifi.serialization.record.RecordFieldType;
import org.apache.nifi.serialization.record.RecordSchema;
import org.apache.nifi.serialization.record.util.IllegalTypeConversionException;
import org.apache.nifi.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SupportsBatching
@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)
@Tags({"hadoop", "hbase", "put", "record"})
@CapabilityDescription("Adds rows to HBase based on the contents of a flowfile using a configured record reader.")
@ReadsAttribute(attribute = "restart.index", description = "Reads restart.index when it needs to replay part of a record set that did not get into HBase.")
@WritesAttribute(attribute = "restart.index", description = "Writes restart.index when a batch fails to be insert into HBase")
public class PutHBaseRecord extends AbstractPutHBase {

    protected static final PropertyDescriptor ROW_FIELD_NAME = new PropertyDescriptor.Builder()
            .name("Row Identifier Field Name")
            .description("Specifies the name of a record field whose value should be used as the row id for the given record.")
            .required(true)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    protected static final PropertyDescriptor TIMESTAMP_FIELD_NAME = new PropertyDescriptor.Builder()
            .name("timestamp-field-name")
            .displayName("Timestamp Field Name")
            .description("Specifies the name of a record field whose value should be used as the timestamp for the cells in HBase. " +
                    "The value of this field must be a number, string, or date that can be converted to a long. " +
                    "If this field is left blank, HBase will use the current time.")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();
    protected static final PropertyDescriptor DEFAULT_VISIBILITY_STRING = new PropertyDescriptor.Builder()
            .name("hbase-default-vis-string")
            .displayName("Default Visibility String")
            .description("When using visibility labels, any value set in this field will be applied to all cells that are written unless " +
                    "an attribute with the convention \"visibility.COLUMN_FAMILY.COLUMN_QUALIFIER\" is present on the flowfile. If this field " +
                    "is left blank, it will be assumed that no visibility is to be set unless visibility-related attributes are set. NOTE: " +
                    "this configuration will have no effect on your data if you have not enabled visibility labels in the HBase cluster.")
            .required(false)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .addValidator(Validator.VALID)
            .build();

    protected static final String FAIL_VALUE = "Fail";
    protected static final String WARN_VALUE = "Warn";
    protected static final String IGNORE_VALUE = "Ignore";
    protected static final String TEXT_VALUE = "Text";

    protected static final AllowableValue COMPLEX_FIELD_FAIL = new AllowableValue(FAIL_VALUE, FAIL_VALUE, "Route entire FlowFile to failure if any elements contain complex values.");
    protected static final AllowableValue COMPLEX_FIELD_WARN = new AllowableValue(WARN_VALUE, WARN_VALUE, "Provide a warning and do not include field in row sent to HBase.");
    protected static final AllowableValue COMPLEX_FIELD_IGNORE = new AllowableValue(IGNORE_VALUE, IGNORE_VALUE, "Silently ignore and do not include in row sent to HBase.");
    protected static final AllowableValue COMPLEX_FIELD_TEXT = new AllowableValue(TEXT_VALUE, TEXT_VALUE, "Use the string representation of the complex field as the value of the given column.");

    static final PropertyDescriptor RECORD_READER_FACTORY = new PropertyDescriptor.Builder()
            .name("record-reader")
            .displayName("Record Reader")
            .description("Specifies the Controller Service to use for parsing incoming data and determining the data's schema")
            .identifiesControllerService(RecordReaderFactory.class)
            .required(true)
            .build();

    protected static final PropertyDescriptor COMPLEX_FIELD_STRATEGY = new PropertyDescriptor.Builder()
            .name("Complex Field Strategy")
            .description("Indicates how to handle complex fields, i.e. fields that do not have a single text value.")
            .expressionLanguageSupported(ExpressionLanguageScope.NONE)
            .required(true)
            .allowableValues(COMPLEX_FIELD_FAIL, COMPLEX_FIELD_WARN, COMPLEX_FIELD_IGNORE, COMPLEX_FIELD_TEXT)
            .defaultValue(COMPLEX_FIELD_TEXT.getValue())
            .build();


    protected static final AllowableValue FIELD_ENCODING_STRING = new AllowableValue(STRING_ENCODING_VALUE, STRING_ENCODING_VALUE,
            "Stores the value of each field as a UTF-8 String.");
    protected static final AllowableValue FIELD_ENCODING_BYTES = new AllowableValue(BYTES_ENCODING_VALUE, BYTES_ENCODING_VALUE,
            "Stores the value of each field as the byte representation of the type derived from the record.");

    protected static final PropertyDescriptor FIELD_ENCODING_STRATEGY = new PropertyDescriptor.Builder()
            .name("Field Encoding Strategy")
            .description(("Indicates how to store the value of each field in HBase. The default behavior is to convert each value from the " +
                    "record to a String, and store the UTF-8 bytes. Choosing Bytes will interpret the type of each field from " +
                    "the record, and convert the value to the byte representation of that type, meaning an integer will be stored as the " +
                    "byte representation of that integer."))
            .required(true)
            .allowableValues(FIELD_ENCODING_STRING, FIELD_ENCODING_BYTES)
            .defaultValue(FIELD_ENCODING_STRING.getValue())
            .build();

    protected static final PropertyDescriptor BATCH_SIZE = new PropertyDescriptor.Builder()
            .name("Batch Size")
            .description("The maximum number of records to be sent to HBase at any one time from the record set.")
            .required(true)
            .addValidator(StandardValidators.POSITIVE_INTEGER_VALIDATOR)
            .defaultValue("1000")
            .build();

    protected static final AllowableValue NULL_FIELD_EMPTY = new AllowableValue("empty-bytes", "Empty Bytes",
            "Use empty bytes. This can be used to overwrite existing fields or to put an empty placeholder value if you want" +
                    " every field to be present even if it has a null value.");
    protected static final AllowableValue NULL_FIELD_SKIP  = new AllowableValue("skip-field", "Skip Field", "Skip the field (don't process it at all).");

    protected static final PropertyDescriptor NULL_FIELD_STRATEGY = new PropertyDescriptor.Builder()
            .name("hbase-record-null-field-strategy")
            .displayName("Null Field Strategy")
            .required(true)
            .defaultValue("skip-field")
            .description("Handle null field values as either an empty string or skip them altogether.")
            .allowableValues(NULL_FIELD_EMPTY, NULL_FIELD_SKIP)
            .build();

    protected static final PropertyDescriptor VISIBILITY_RECORD_PATH = new PropertyDescriptor.Builder()
            .name("put-hb-rec-visibility-record-path")
            .displayName("Visibility String Record Path Root")
            .description("A record path that points to part of the record which contains a path to a mapping of visibility strings to record paths")
            .required(false)
            .addValidator(Validator.VALID)
            .build();

    protected RecordPathCache recordPathCache;

    @Override
    public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(RECORD_READER_FACTORY);
        properties.add(HBASE_CLIENT_SERVICE);
        properties.add(TABLE_NAME);
        properties.add(ROW_FIELD_NAME);
        properties.add(ROW_ID_ENCODING_STRATEGY);
        properties.add(NULL_FIELD_STRATEGY);
        properties.add(COLUMN_FAMILY);
        properties.add(DEFAULT_VISIBILITY_STRING);
        properties.add(VISIBILITY_RECORD_PATH);
        properties.add(TIMESTAMP_FIELD_NAME);
        properties.add(BATCH_SIZE);
        properties.add(COMPLEX_FIELD_STRATEGY);
        properties.add(FIELD_ENCODING_STRATEGY);
        return properties;
    }

    @Override
    public Set<Relationship> getRelationships() {
        final Set<Relationship> rels = new HashSet<>();
        rels.add(REL_SUCCESS);
        rels.add(REL_FAILURE);
        return rels;
    }

    private int addBatch(String tableName, List<PutFlowFile> flowFiles) throws IOException {
        int columns = 0;
        clientService.put(tableName, flowFiles);
        for (PutFlowFile put : flowFiles) {
            columns += put.getColumns().size();
        }

        return columns;
    }

    @Override
    @OnScheduled
    public void onScheduled(final ProcessContext context) {
        recordPathCache = new RecordPathCache(4);
        super.onScheduled(context);
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
        final int batchSize = context.getProperty(BATCH_SIZE).asInteger();
        FlowFile flowFile = session.get();
        if (flowFile == null) {
            return;
        }

        final RecordReaderFactory recordParserFactory = context.getProperty(RECORD_READER_FACTORY)
                .asControllerService(RecordReaderFactory.class);
        List<PutFlowFile> flowFiles = new ArrayList<>();
        final String tableName = context.getProperty(TABLE_NAME).evaluateAttributeExpressions(flowFile).getValue();
        final String rowFieldName = context.getProperty(ROW_FIELD_NAME).evaluateAttributeExpressions(flowFile).getValue();
        final String columnFamily = context.getProperty(COLUMN_FAMILY).evaluateAttributeExpressions(flowFile).getValue();
        final String timestampFieldName = context.getProperty(TIMESTAMP_FIELD_NAME).evaluateAttributeExpressions(flowFile).getValue();
        final String fieldEncodingStrategy = context.getProperty(FIELD_ENCODING_STRATEGY).getValue();
        final String complexFieldStrategy = context.getProperty(COMPLEX_FIELD_STRATEGY).getValue();
        final String rowEncodingStrategy = context.getProperty(ROW_ID_ENCODING_STRATEGY).getValue();
        final String recordPathText = context.getProperty(VISIBILITY_RECORD_PATH).getValue();

        RecordPath recordPath = null;
        if (recordPathCache != null && !StringUtils.isEmpty(recordPathText)) {
            recordPath = recordPathCache.getCompiled(recordPathText);
        }

        final long start = System.nanoTime();
        int index = 0;
        int columns = 0;
        boolean failed = false;
        String startIndexStr = flowFile.getAttribute("restart.index");
        int startIndex = -1;
        if (startIndexStr != null) {
            startIndex = Integer.parseInt(startIndexStr);
        }

        PutFlowFile last  = null;
        try (final InputStream in = session.read(flowFile);
             final RecordReader reader = recordParserFactory.createRecordReader(flowFile, in, getLogger())) {
            Record record;
            if (startIndex >= 0) {
                while (index++ < startIndex && (reader.nextRecord()) != null) { }
            }

            while ((record = reader.nextRecord()) != null) {
                PutFlowFile putFlowFile = createPut(context, record, reader.getSchema(), recordPath, flowFile, rowFieldName, columnFamily,
                        timestampFieldName, fieldEncodingStrategy, rowEncodingStrategy, complexFieldStrategy);
                if (putFlowFile.getColumns().size() == 0) {
                    continue;
                }
                flowFiles.add(putFlowFile);
                index++;

                if (flowFiles.size() == batchSize) {
                    columns += addBatch(tableName, flowFiles);
                    last = flowFiles.get(flowFiles.size() - 1);
                    flowFiles = new ArrayList<>();
                }
            }
            if (flowFiles.size() > 0) {
                columns += addBatch(tableName, flowFiles);
                last = flowFiles.get(flowFiles.size() - 1);
            }
        } catch (Exception ex) {
            getLogger().error("Failed to put records to HBase.", ex);
            failed = true;
        }

        if (!failed) {
            if (columns > 0) {
                sendProvenance(session, flowFile, columns, System.nanoTime() - start, last);
            }
            flowFile = session.removeAttribute(flowFile, "restart.index");
            session.transfer(flowFile, REL_SUCCESS);
        } else {
            String restartIndex = Integer.toString(index - flowFiles.size());
            flowFile = session.putAttribute(flowFile, "restart.index", restartIndex);
            if (columns > 0) {
                sendProvenance(session, flowFile, columns, System.nanoTime() - start, last);
            }
            flowFile = session.penalize(flowFile);
            session.transfer(flowFile, REL_FAILURE);
        }
    }

    private void sendProvenance(ProcessSession session, FlowFile flowFile, int columns, long time, PutFlowFile pff) {
        final String details = String.format("Put %d cells to HBase.", columns);
        session.getProvenanceReporter().send(flowFile, getTransitUri(pff), details, time);
    }

    @Override
    protected PutFlowFile createPut(ProcessSession session, ProcessContext context, FlowFile flowFile) {
        return null;
    }

    protected byte[] asBytes(String field, RecordFieldType fieldType, Record record, boolean asString, String complexFieldStrategy) throws PutCreationFailedInvokedException {

        byte[] retVal;

        if (asString) {
            switch (fieldType) {
                case RECORD:
                case CHOICE:
                case ARRAY:
                case MAP:
                    retVal = handleComplexField(record, field, complexFieldStrategy);
                    break;
                default:
                    final String value = record.getAsString(field);
                    retVal = clientService.toBytes(value);
                    break;
            }
        } else {
            switch (fieldType) {
                case RECORD:
                case CHOICE:
                case ARRAY:
                case MAP:
                    retVal = handleComplexField(record, field, complexFieldStrategy);
                    break;
                case BOOLEAN:
                    retVal = clientService.toBytes(record.getAsBoolean(field));
                    break;
                case DOUBLE:
                    retVal = clientService.toBytes(record.getAsDouble(field));
                    break;
                case FLOAT:
                    retVal = clientService.toBytes(record.getAsFloat(field));
                    break;
                case INT:
                    retVal = clientService.toBytes(record.getAsInt(field));
                    break;
                case LONG:
                    retVal = clientService.toBytes(record.getAsLong(field));
                    break;
                default:
                    final String value = record.getAsString(field);
                    retVal = clientService.toBytes(value);
                    break;
            }
        }

        return retVal;
    }

    private byte[] handleComplexField(Record record, String field, String complexFieldStrategy) throws PutCreationFailedInvokedException {
        switch (complexFieldStrategy) {
            case FAIL_VALUE:
                getLogger().error("Complex value found for {}; routing to failure", field);
                throw new PutCreationFailedInvokedException(String.format("Complex value found for %s; routing to failure", field));
            case WARN_VALUE:
                getLogger().warn("Complex value found for {}; skipping", field);
                return null;
            case TEXT_VALUE:
                final String value = record.getAsString(field);
                return clientService.toBytes(value);
            case IGNORE_VALUE:
                // silently skip
                return null;
            default:
                return null;
        }
    }

    static final byte[] EMPTY = "".getBytes();

    protected PutFlowFile createPut(ProcessContext context, Record record, RecordSchema schema, RecordPath recordPath, FlowFile flowFile, String rowFieldName,
                                    String columnFamily, String timestampFieldName, String fieldEncodingStrategy, String rowEncodingStrategy,
                                    String complexFieldStrategy)
            throws PutCreationFailedInvokedException {
        PutFlowFile retVal = null;
        final String tableName = context.getProperty(TABLE_NAME).evaluateAttributeExpressions(flowFile).getValue();
        final String nullStrategy = context.getProperty(NULL_FIELD_STRATEGY).getValue();
        final String defaultVisibility = context.getProperty(DEFAULT_VISIBILITY_STRING).evaluateAttributeExpressions(flowFile).getValue();

        boolean asString = STRING_ENCODING_VALUE.equals(fieldEncodingStrategy);

        final byte[] fam  = clientService.toBytes(columnFamily);

        if (record != null) {
            final Long timestamp;
            if (!StringUtils.isBlank(timestampFieldName)) {
                try {
                    timestamp = record.getAsLong(timestampFieldName);
                } catch (IllegalTypeConversionException e) {
                    throw new PutCreationFailedInvokedException("Could not convert " + timestampFieldName + " to a long", e);
                }

                if (timestamp == null) {
                    getLogger().warn("The value of timestamp field {} was null, record will be inserted with latest timestamp", timestampFieldName);
                }
            } else {
                timestamp = null;
            }

            RecordField visField = null;
            Map visSettings = null;
            if (recordPath != null) {
                final RecordPathResult result = recordPath.evaluate(record);
                FieldValue fv = result.getSelectedFields().findFirst().get();
                visField = fv.getField();
                visSettings = (Map) fv.getValue();
            }

            List<PutColumn> columns = new ArrayList<>();
            for (String name : schema.getFieldNames()) {
                if (name.equals(rowFieldName) || name.equals(timestampFieldName) || (visField != null && name.equals(visField.getFieldName()))) {
                    continue;
                }

                Object val = record.getValue(name);
                final byte[] fieldValueBytes;
                if (val == null && nullStrategy.equals(NULL_FIELD_SKIP.getValue())) {
                    continue;
                } else if (val == null && nullStrategy.equals(NULL_FIELD_EMPTY.getValue())) {
                    fieldValueBytes = EMPTY;
                } else {
                    fieldValueBytes = asBytes(name, schema.getField(name).get().getDataType().getFieldType(), record, asString, complexFieldStrategy);
                }


                if (fieldValueBytes != null) {

                    String visString = (visField != null && visSettings != null && visSettings.containsKey(name))
                            ? (String) visSettings.get(name) : defaultVisibility;

                    //TODO: factor this into future enhancements to how complex records are handled.
                    if (StringUtils.isBlank(visString)) {
                        visString = VisibilityUtil.pickVisibilityString(columnFamily, name, flowFile, context);
                    }

                    PutColumn column = !StringUtils.isEmpty(visString)
                            ? new PutColumn(fam, clientService.toBytes(name), fieldValueBytes, timestamp, visString)
                            : new PutColumn(fam, clientService.toBytes(name), fieldValueBytes, timestamp);

                    columns.add(column);
                }
            }

            String rowIdValue = record.getAsString(rowFieldName);
            if (rowIdValue == null) {
                throw new PutCreationFailedInvokedException(String.format("Row ID was null for flowfile with ID %s", flowFile.getAttribute("uuid")));
            }
            byte[] rowId =  getRow(rowIdValue, rowEncodingStrategy);

            retVal = new PutFlowFile(tableName, rowId, columns, flowFile);
        }

        return retVal;
    }

    static class PutCreationFailedInvokedException extends Exception {
        PutCreationFailedInvokedException(String msg) {
            super(msg);
        }
        PutCreationFailedInvokedException(String msg, Exception e) {
            super(msg, e);
        }
    }
}
