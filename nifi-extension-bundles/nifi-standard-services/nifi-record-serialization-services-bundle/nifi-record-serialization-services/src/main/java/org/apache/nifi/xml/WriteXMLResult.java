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

package org.apache.nifi.xml;

import javanet.staxutils.IndentingXMLStreamWriter;
import org.apache.nifi.NullSuppression;
import org.apache.nifi.schema.access.SchemaAccessWriter;
import org.apache.nifi.serialization.AbstractRecordSetWriter;
import org.apache.nifi.serialization.RecordSetWriter;
import org.apache.nifi.serialization.WriteResult;
import org.apache.nifi.serialization.record.DataType;
import org.apache.nifi.serialization.record.RawRecordWriter;
import org.apache.nifi.serialization.record.Record;
import org.apache.nifi.serialization.record.RecordField;
import org.apache.nifi.serialization.record.RecordFieldType;
import org.apache.nifi.serialization.record.RecordSchema;
import org.apache.nifi.serialization.record.field.FieldConverter;
import org.apache.nifi.serialization.record.field.StandardFieldConverterRegistry;
import org.apache.nifi.serialization.record.type.ArrayDataType;
import org.apache.nifi.serialization.record.type.ChoiceDataType;
import org.apache.nifi.serialization.record.type.MapDataType;
import org.apache.nifi.serialization.record.type.RecordDataType;
import org.apache.nifi.serialization.record.util.DataTypeUtils;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static org.apache.nifi.xml.XMLRecordSetWriter.RECORD_TAG_NAME;
import static org.apache.nifi.xml.XMLRecordSetWriter.ROOT_TAG_NAME;


public class WriteXMLResult extends AbstractRecordSetWriter implements RecordSetWriter, RawRecordWriter {
    private static final Pattern TAG_NAME_CHARS_TO_STRIP = Pattern.compile("[/<>!&'\"]");

    private static final FieldConverter<Object, String> STRING_FIELD_CONVERTER = StandardFieldConverterRegistry.getRegistry().getFieldConverter(String.class);

    private final RecordSchema recordSchema;
    private final SchemaAccessWriter schemaAccess;
    private final XMLStreamWriter writer;
    private final NullSuppression nullSuppression;
    private final boolean omitDeclaration;
    private final ArrayWrapping arrayWrapping;
    private final String arrayTagName;
    private final String recordTagName;
    private final String rootTagName;
    private final boolean allowWritingMultipleRecords;
    private boolean hasWrittenRecord;

    private final String dateFormat;
    private final String timeFormat;
    private final String timestampFormat;

    public WriteXMLResult(final RecordSchema recordSchema, final SchemaAccessWriter schemaAccess, final OutputStream out, final boolean prettyPrint, final boolean omitDeclaration,
                          final NullSuppression nullSuppression, final ArrayWrapping arrayWrapping, final String arrayTagName, final String rootTagName, final String recordTagName,
                          final String charSet, final String dateFormat, final String timeFormat, final String timestampFormat) throws IOException {

        super(out);

        this.recordSchema = recordSchema;
        this.schemaAccess = schemaAccess;
        this.nullSuppression = nullSuppression;

        this.omitDeclaration = omitDeclaration;

        this.arrayWrapping = arrayWrapping;
        this.arrayTagName = arrayTagName;

        this.rootTagName = rootTagName;

        if (recordTagName != null) {
            this.recordTagName = recordTagName;
        } else {
            Optional<String> recordTagNameOptional = recordSchema.getSchemaName().isPresent() ? recordSchema.getSchemaName() : recordSchema.getIdentifier().getName();
            if (recordTagNameOptional.isPresent()) {
                this.recordTagName = recordTagNameOptional.get();
            } else {
                final String message = "The property '" + RECORD_TAG_NAME.getDisplayName() +
                    "' has not been set and the writer does not find a record name in the schema.";
                throw new IOException(message);
            }
        }

        this.allowWritingMultipleRecords = !(this.rootTagName == null);
        hasWrittenRecord = false;

        this.dateFormat = dateFormat;
        this.timeFormat = timeFormat;
        this.timestampFormat = timestampFormat;

        try {
            XMLOutputFactory factory = XMLOutputFactory.newInstance();

            if (prettyPrint) {
                writer = new IndentingXMLStreamWriter(factory.createXMLStreamWriter(out, charSet));
            } else {
                writer = factory.createXMLStreamWriter(out, charSet);
            }

        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    protected void onBeginRecordSet() throws IOException {

        final OutputStream out = getOutputStream();
        schemaAccess.writeHeader(recordSchema, out);

        try {
            if (!omitDeclaration) {
                writer.writeStartDocument();
            }

            if (allowWritingMultipleRecords) {
                writer.writeStartElement(rootTagName);
            }

        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    protected Map<String, String> onFinishRecordSet() throws IOException {

        try {
            if (allowWritingMultipleRecords) {
                writer.writeEndElement();
            }

            writer.writeEndDocument();
        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage());
        }
        return schemaAccess.getAttributes(recordSchema);
    }

    @Override
    public void close() throws IOException {

        try {
            writer.close();

        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage());
        }

        super.close();
    }

    @Override
    public void flush() throws IOException {

        try {
            writer.flush();

        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage());
        }
    }

    private void checkWritingMultipleRecords() throws IOException {
        if (!allowWritingMultipleRecords && hasWrittenRecord) {
            final String message = "The writer attempts to write multiple record although property \'" + ROOT_TAG_NAME.getDisplayName() +
                "\' has not been set. If the XMLRecordSetWriter is supposed to write multiple records into one FlowFile, this property is required to be configured.";
            throw new IOException(message);
        }
    }

    @Override
    protected Map<String, String> writeRecord(Record record) throws IOException {

        if (!isActiveRecordSet()) {
            schemaAccess.writeHeader(recordSchema, getOutputStream());
        }

        checkWritingMultipleRecords();

        Deque<String> tagsToOpen = new ArrayDeque<>();

        try {
            tagsToOpen.addLast(recordTagName);

            boolean closingTagRequired = iterateThroughRecordUsingSchema(tagsToOpen, record, recordSchema);
            if (closingTagRequired) {
                writer.writeEndElement();
                hasWrittenRecord = true;
            }

        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage());
        }
        return schemaAccess.getAttributes(recordSchema);
    }

    private boolean iterateThroughRecordUsingSchema(Deque<String> tagsToOpen, Record record, RecordSchema schema) throws XMLStreamException {

        boolean loopHasWritten = false;
        for (RecordField field : schema.getFields()) {

            String fieldName = field.getFieldName();
            DataType dataType = field.getDataType();
            Object value = record.getValue(field);

            final DataType chosenDataType = dataType.getFieldType() == RecordFieldType.CHOICE ? DataTypeUtils.chooseDataType(value, (ChoiceDataType) dataType) : dataType;
            final Object coercedValue = DataTypeUtils.convertType(
                    value, chosenDataType, Optional.ofNullable(dateFormat), Optional.ofNullable(timeFormat), Optional.ofNullable(timestampFormat), fieldName
            );

            if (coercedValue != null) {
                boolean hasWritten = writeFieldForType(tagsToOpen, coercedValue, chosenDataType, fieldName);
                if (hasWritten) {
                    loopHasWritten = true;
                }

            } else {
                if (nullSuppression.equals(NullSuppression.NEVER_SUPPRESS) || nullSuppression.equals(NullSuppression.SUPPRESS_MISSING) && recordHasField(field, record)) {
                    writeAllTags(tagsToOpen, fieldName);
                    writer.writeEndElement();
                    loopHasWritten = true;
                }
            }
        }

        return loopHasWritten;
    }

    private boolean writeFieldForType(Deque<String> tagsToOpen, Object coercedValue, DataType dataType, String fieldName) throws XMLStreamException {
        switch (dataType.getFieldType()) {
            case BOOLEAN:
            case BYTE:
            case CHAR:
            case DECIMAL:
            case DOUBLE:
            case FLOAT:
            case INT:
            case LONG:
            case SHORT:
            case UUID:
            case STRING: {
                writeAllTags(tagsToOpen, fieldName);
                writer.writeCharacters(coercedValue.toString());
                writer.writeEndElement();
                return true;
            }
            case DATE: {
                writeAllTags(tagsToOpen, fieldName);
                final String stringValue = STRING_FIELD_CONVERTER.convertField(coercedValue, Optional.ofNullable(dateFormat), fieldName);
                writer.writeCharacters(stringValue);
                writer.writeEndElement();
                return true;
            }
            case TIME: {
                writeAllTags(tagsToOpen, fieldName);
                final String stringValue = STRING_FIELD_CONVERTER.convertField(coercedValue, Optional.ofNullable(timeFormat), fieldName);
                writer.writeCharacters(stringValue);
                writer.writeEndElement();
                return true;
            }
            case TIMESTAMP: {
                writeAllTags(tagsToOpen, fieldName);
                final String stringValue = STRING_FIELD_CONVERTER.convertField(coercedValue, Optional.ofNullable(timestampFormat), fieldName);
                writer.writeCharacters(stringValue);
                writer.writeEndElement();
                return true;
            }
            case RECORD: {
                final Record record = (Record) coercedValue;
                final RecordDataType recordDataType = (RecordDataType) dataType;
                final RecordSchema childSchema = recordDataType.getChildSchema();
                tagsToOpen.addLast(fieldName);

                boolean hasWritten = iterateThroughRecordUsingSchema(tagsToOpen, record, childSchema);

                if (hasWritten) {
                    writer.writeEndElement();
                    return true;
                } else {

                    if (nullSuppression.equals(NullSuppression.NEVER_SUPPRESS) || nullSuppression.equals(NullSuppression.SUPPRESS_MISSING)) {
                        writeAllTags(tagsToOpen);
                        writer.writeEndElement();
                        return true;
                    } else {
                        tagsToOpen.removeLast();
                        return false;
                    }
                }
            }
            case ARRAY: {
                final Object[] arrayValues;
                if (coercedValue instanceof Object[]) {
                    arrayValues = (Object[]) coercedValue;
                } else {
                    arrayValues = new Object[]{coercedValue.toString()};
                }

                final ArrayDataType arrayDataType = (ArrayDataType) dataType;
                final DataType elementType = arrayDataType.getElementType();

                final String elementName;
                final String wrapperName;
                if (arrayWrapping.equals(ArrayWrapping.USE_PROPERTY_FOR_ELEMENTS)) {
                    elementName = arrayTagName;
                    wrapperName = fieldName;
                } else if (arrayWrapping.equals(ArrayWrapping.USE_PROPERTY_AS_WRAPPER)) {
                    elementName = fieldName;
                    wrapperName = arrayTagName;
                } else {
                    elementName = fieldName;
                    wrapperName = null;
                }

                if (wrapperName != null) {
                    tagsToOpen.addLast(wrapperName);
                }

                boolean loopHasWritten = false;
                for (Object element : arrayValues) {

                    final DataType chosenDataType = elementType.getFieldType() == RecordFieldType.CHOICE ? DataTypeUtils.chooseDataType(element, (ChoiceDataType) elementType) : elementType;
                    final Object coercedElement = DataTypeUtils.convertType(
                            element, chosenDataType, Optional.ofNullable(dateFormat), Optional.ofNullable(timeFormat), Optional.ofNullable(timestampFormat), fieldName
                    );

                    if (coercedElement != null) {
                        boolean hasWritten = writeFieldForType(tagsToOpen, coercedElement, elementType, elementName);

                        if (hasWritten) {
                            loopHasWritten = true;
                        }

                    } else {
                        if (nullSuppression.equals(NullSuppression.NEVER_SUPPRESS) || nullSuppression.equals(NullSuppression.SUPPRESS_MISSING)) {
                            writeAllTags(tagsToOpen, fieldName);
                            writer.writeEndElement();
                            loopHasWritten = true;
                        }
                    }
                }

                if (wrapperName != null) {
                    if (loopHasWritten) {
                        writer.writeEndElement();
                        return true;
                    } else {
                        if (nullSuppression.equals(NullSuppression.NEVER_SUPPRESS) || nullSuppression.equals(NullSuppression.SUPPRESS_MISSING)) {
                            writeAllTags(tagsToOpen);
                            writer.writeEndElement();
                            return true;
                        } else {
                            tagsToOpen.removeLast();
                            return false;
                        }
                    }
                } else {
                    return loopHasWritten;
                }
            }
            case MAP: {
                final MapDataType mapDataType = (MapDataType) dataType;
                final DataType valueDataType = mapDataType.getValueType();
                final Map<String, ?> map = (Map<String, ?>) coercedValue;

                tagsToOpen.addLast(fieldName);
                boolean loopHasWritten = false;

                for (Map.Entry<String, ?> entry : map.entrySet()) {

                    final String key = entry.getKey();

                    final DataType chosenDataType = valueDataType.getFieldType() == RecordFieldType.CHOICE ? DataTypeUtils.chooseDataType(entry.getValue(),
                            (ChoiceDataType) valueDataType) : valueDataType;
                    final Object coercedElement = DataTypeUtils.convertType(
                            entry.getValue(), chosenDataType, Optional.ofNullable(dateFormat), Optional.ofNullable(timeFormat), Optional.ofNullable(timestampFormat), fieldName
                    );

                    if (coercedElement != null) {
                        boolean hasWritten = writeFieldForType(tagsToOpen, entry.getValue(), valueDataType, key);

                        if (hasWritten) {
                            loopHasWritten = true;
                        }
                    } else {
                        if (nullSuppression.equals(NullSuppression.NEVER_SUPPRESS) || nullSuppression.equals(NullSuppression.SUPPRESS_MISSING)) {
                            writeAllTags(tagsToOpen, key);
                            writer.writeEndElement();
                            loopHasWritten = true;
                        }
                    }
                }

                if (loopHasWritten) {
                    writer.writeEndElement();
                    return true;
                } else {
                    if (nullSuppression.equals(NullSuppression.NEVER_SUPPRESS) || nullSuppression.equals(NullSuppression.SUPPRESS_MISSING)) {
                        writeAllTags(tagsToOpen);
                        writer.writeEndElement();
                        return true;
                    } else {
                        tagsToOpen.removeLast();
                        return false;
                    }
                }
            }
            case CHOICE:
            default: {
                return writeUnknownField(tagsToOpen, coercedValue, fieldName);
            }
        }
    }

    private void writeAllTags(Deque<String> tagsToOpen, String fieldName) throws XMLStreamException {
        tagsToOpen.addLast(fieldName);
        writeAllTags(tagsToOpen);
    }

    private String escapeTagName(final String tagName) {
        return TAG_NAME_CHARS_TO_STRIP.matcher(tagName).replaceAll("");
    }

    private void writeAllTags(Deque<String> tagsToOpen) throws XMLStreamException {
        for (String tagName : tagsToOpen) {
            writer.writeStartElement(escapeTagName(tagName));
        }
        tagsToOpen.clear();
    }

    @Override
    public WriteResult writeRawRecord(Record record) throws IOException {

        if (!isActiveRecordSet()) {
            schemaAccess.writeHeader(recordSchema, getOutputStream());
        }

        checkWritingMultipleRecords();

        Deque<String> tagsToOpen = new ArrayDeque<>();

        try {
            tagsToOpen.addLast(recordTagName);

            boolean closingTagRequired = iterateThroughRecordWithoutSchema(tagsToOpen, record);
            if (closingTagRequired) {
                writer.writeEndElement();
                hasWrittenRecord = true;
            }

        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage());
        }

        final Map<String, String> attributes = schemaAccess.getAttributes(recordSchema);
        return WriteResult.of(incrementRecordCount(), attributes);
    }

    private boolean iterateThroughRecordWithoutSchema(Deque<String> tagsToOpen, Record record) throws XMLStreamException {

        boolean loopHasWritten = false;

        for (String fieldName : record.getRawFieldNames()) {
            Object value = record.getValue(fieldName);

            if (value != null) {
                boolean hasWritten = writeUnknownField(tagsToOpen, value, fieldName);

                if (hasWritten) {
                    loopHasWritten = true;
                }
            } else {
                if (nullSuppression.equals(NullSuppression.NEVER_SUPPRESS) || nullSuppression.equals(NullSuppression.SUPPRESS_MISSING)) {
                    writeAllTags(tagsToOpen, fieldName);
                    writer.writeEndElement();
                    loopHasWritten = true;
                }
            }
        }

        return loopHasWritten;
    }

    private boolean writeUnknownField(Deque<String> tagsToOpen, Object value, String fieldName) throws XMLStreamException {

        if (value instanceof Record) {
            Record valueAsRecord = (Record) value;
            tagsToOpen.addLast(fieldName);

            boolean hasWritten = iterateThroughRecordWithoutSchema(tagsToOpen, valueAsRecord);

            if (hasWritten) {
                writer.writeEndElement();
                return true;
            } else {
                if (nullSuppression.equals(NullSuppression.NEVER_SUPPRESS) || nullSuppression.equals(NullSuppression.SUPPRESS_MISSING)) {
                    writeAllTags(tagsToOpen);
                    writer.writeEndElement();
                    return true;
                } else {
                    tagsToOpen.removeLast();
                    return false;
                }
            }
        }

        if (value instanceof Object[]) {
            Object[] valueAsArray = (Object[]) value;

            final String elementName;
            final String wrapperName;
            if (arrayWrapping.equals(ArrayWrapping.USE_PROPERTY_FOR_ELEMENTS)) {
                elementName = arrayTagName;
                wrapperName = fieldName;
            } else if (arrayWrapping.equals(ArrayWrapping.USE_PROPERTY_AS_WRAPPER)) {
                elementName = fieldName;
                wrapperName = arrayTagName;
            } else {
                elementName = fieldName;
                wrapperName = null;
            }

            if (wrapperName != null) {
                tagsToOpen.addLast(wrapperName);
            }

            boolean loopHasWritten = false;

            for (Object element : valueAsArray) {
                if (element != null) {
                    boolean hasWritten = writeUnknownField(tagsToOpen, element, elementName);

                    if (hasWritten) {
                        loopHasWritten = true;
                    }

                } else {
                    if (nullSuppression.equals(NullSuppression.NEVER_SUPPRESS) || nullSuppression.equals(NullSuppression.SUPPRESS_MISSING)) {
                        writeAllTags(tagsToOpen, fieldName);
                        writer.writeEndElement();
                        loopHasWritten = true;
                    }
                }
            }

            if (wrapperName != null) {
                if (loopHasWritten) {
                    writer.writeEndElement();
                    return true;
                } else {
                    if (nullSuppression.equals(NullSuppression.NEVER_SUPPRESS) || nullSuppression.equals(NullSuppression.SUPPRESS_MISSING)) {
                        writeAllTags(tagsToOpen);
                        writer.writeEndElement();
                        return true;
                    } else {
                        tagsToOpen.removeLast();
                        return false;
                    }
                }
            } else {
                return loopHasWritten;
            }
        }

        if (value instanceof Map) {
            Map<String, ?> valueAsMap = (Map<String, ?>) value;

            tagsToOpen.addLast(fieldName);
            boolean loopHasWritten = false;

            for (Map.Entry<String, ?> entry : valueAsMap.entrySet()) {

                final String key = entry.getKey();
                final Object entryValue = entry.getValue();

                if (entryValue != null) {
                    boolean hasWritten = writeUnknownField(tagsToOpen, entry.getValue(), key);

                    if (hasWritten) {
                        loopHasWritten = true;
                    }
                } else {
                    if (nullSuppression.equals(NullSuppression.NEVER_SUPPRESS) || nullSuppression.equals(NullSuppression.SUPPRESS_MISSING)) {
                        writeAllTags(tagsToOpen, key);
                        writer.writeEndElement();
                        loopHasWritten = true;
                    }
                }

            }

            if (loopHasWritten) {
                writer.writeEndElement();
                return true;
            } else {
                if (nullSuppression.equals(NullSuppression.NEVER_SUPPRESS) || nullSuppression.equals(NullSuppression.SUPPRESS_MISSING)) {
                    writeAllTags(tagsToOpen);
                    writer.writeEndElement();
                    return true;
                } else {
                    tagsToOpen.removeLast();
                    return false;
                }
            }
        }

        writeAllTags(tagsToOpen, fieldName);
        writer.writeCharacters(value.toString());
        writer.writeEndElement();
        return true;
    }


    @Override
    public String getMimeType() {
        return "application/xml";
    }

    private boolean recordHasField(RecordField field, Record record) {
        Set<String> recordFieldNames = record.getRawFieldNames();
        if (recordFieldNames.contains(field.getFieldName())) {
            return true;
        }

        for (String alias : field.getAliases()) {
            if (recordFieldNames.contains(alias)) {
                return true;
            }
        }

        return false;
    }
}
