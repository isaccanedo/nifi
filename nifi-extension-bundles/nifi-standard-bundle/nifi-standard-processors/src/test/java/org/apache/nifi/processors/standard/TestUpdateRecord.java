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

package org.apache.nifi.processors.standard;

import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.json.JsonRecordSetWriter;
import org.apache.nifi.json.JsonTreeReader;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.schema.access.SchemaAccessUtils;
import org.apache.nifi.schema.inference.SchemaInferenceUtil;
import org.apache.nifi.serialization.record.MockRecordParser;
import org.apache.nifi.serialization.record.MockRecordWriter;
import org.apache.nifi.serialization.record.RecordFieldType;
import org.apache.nifi.util.LogMessage;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "Test only runs on *nix")
public class TestUpdateRecord {

    private TestRunner runner;
    private MockRecordParser readerService;
    private MockRecordWriter writerService;

    @BeforeEach
    public void setup() throws InitializationException {
        readerService = new MockRecordParser();
        writerService = new MockRecordWriter("header", false);

        runner = TestRunners.newTestRunner(UpdateRecord.class);
        runner.addControllerService("reader", readerService);
        runner.enableControllerService(readerService);
        runner.addControllerService("writer", writerService);
        runner.enableControllerService(writerService);

        runner.setProperty(UpdateRecord.RECORD_READER, "reader");
        runner.setProperty(UpdateRecord.RECORD_WRITER, "writer");

        readerService.addSchemaField("name", RecordFieldType.STRING);
        readerService.addSchemaField("age", RecordFieldType.INT);
    }


    @Test
    public void testLiteralReplacementValue() {
        runner.setProperty("/name", "Jane Doe");
        runner.enqueue("");

        readerService.addRecord("John Doe", 35);
        runner.run();

        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0);
        out.assertContentEquals("header\nJane Doe,35\n");
    }

    @Test
    public void testLiteralReplacementValueExpressionLanguage() {
        runner.setProperty("/name", "${newName}");
        runner.enqueue("", Collections.singletonMap("newName", "Jane Doe"));

        readerService.addRecord("John Doe", 35);
        runner.run();

        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0);
        out.assertContentEquals("header\nJane Doe,35\n");
    }

    @Test
    public void testRecordPathReplacementValue() {
        runner.setProperty("/name", "/age");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.RECORD_PATH_VALUES.getValue());
        runner.enqueue("");

        readerService.addRecord("John Doe", 35);
        runner.run();

        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0);
        out.assertContentEquals("header\n35,35\n");
    }

    @Test
    public void testRecordPathReplacementWithFilterFunctionCall() {
        runner.setProperty("/hasAge", "not(isEmpty(/age))");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.RECORD_PATH_VALUES.getValue());
        runner.enqueue("");

        readerService.addRecord("John Doe", 35);
        runner.run();

        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0);
        out.assertContentEquals("header\nJohn Doe,35,true\n");

    }

    @Test
    public void testInvalidRecordPathUsingExpressionLanguage() {
        runner.setProperty("/name", "${recordPath}");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.RECORD_PATH_VALUES.getValue());
        runner.enqueue("", Collections.singletonMap("recordPath", "hello"));

        readerService.addRecord("John Doe", 35);
        runner.run();

        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_FAILURE, 1);
    }

    @Test
    public void testLiteralReplacementRowIndexValueExpressionLanguage() throws InitializationException {
        readerService = new MockRecordParser();
        readerService.addSchemaField("id", RecordFieldType.LONG);
        readerService.addSchemaField("name", RecordFieldType.STRING);
        readerService.addSchemaField("age", RecordFieldType.INT);
        runner.addControllerService("reader", readerService);
        runner.enableControllerService(readerService);

        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.LITERAL_VALUES);
        runner.setProperty("/id", "${record.index}");

        runner.enqueue("");

        readerService.addRecord(null, "John Doe", 35);
        readerService.addRecord(null, "Jane Doe", 36);
        readerService.addRecord(null, "John Smith", 37);
        readerService.addRecord(null, "Jane Smith", 38);
        runner.run();

        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0);
        out.assertContentEquals("header\n1,John Doe,35\n2,Jane Doe,36\n3,John Smith,37\n4,Jane Smith,38\n");
    }

    @Test
    public void testReplaceWithMissingRecordPath() throws InitializationException {
        readerService = new MockRecordParser();
        readerService.addSchemaField("name", RecordFieldType.STRING);
        readerService.addSchemaField("siblings", RecordFieldType.ARRAY);
        runner.addControllerService("reader", readerService);
        runner.enableControllerService(readerService);

        runner.setProperty("/name", "/siblings[0]/name");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.RECORD_PATH_VALUES.getValue());

        runner.enqueue("", Collections.singletonMap("recordPath", "hello"));

        readerService.addRecord("John Doe", null);
        runner.run();

        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        final MockFlowFile mff = runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0);
        mff.assertContentEquals("header\n,\n");
    }

    @Test
    public void testRelativePath() throws InitializationException {
        readerService = new MockRecordParser();
        readerService.addSchemaField("name", RecordFieldType.STRING);
        readerService.addSchemaField("nickname", RecordFieldType.STRING);
        runner.addControllerService("reader", readerService);
        runner.enableControllerService(readerService);

        runner.setProperty("/name", "../nickname");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.RECORD_PATH_VALUES.getValue());

        runner.enqueue("", Collections.singletonMap("recordPath", "hello"));

        readerService.addRecord("John Doe", "Johnny");
        runner.run();

        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        final MockFlowFile mff = runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0);
        mff.assertContentEquals("header\nJohnny,Johnny\n");
    }

    @Test
    public void testConcatWithArrayInferredSchema() throws InitializationException, IOException {
        final JsonTreeReader jsonReader = new JsonTreeReader();
        runner.addControllerService("reader", jsonReader);

        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaInferenceUtil.INFER_SCHEMA);
        runner.enableControllerService(jsonReader);

        final JsonRecordSetWriter jsonWriter = new JsonRecordSetWriter();
        runner.addControllerService("writer", jsonWriter);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.INHERIT_RECORD_SCHEMA);
        runner.setProperty(jsonWriter, "Pretty Print JSON", "true");
        runner.setProperty(jsonWriter, "Schema Write Strategy", "full-schema-attribute");
        runner.enableControllerService(jsonWriter);

        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/addresses.json"));
        runner.setProperty("/addresses[*]/full", "concat(../street, ' ', ../city, ' ', ../state)");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.RECORD_PATH_VALUES);

        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        final String expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/output/full-addresses.json")));
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
    }

    @Test
    public void testChangingSchema() throws InitializationException, IOException {
        final JsonTreeReader jsonReader = new JsonTreeReader();
        runner.addControllerService("reader", jsonReader);

        final String inputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/person-with-name-record.avsc")));
        final String outputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/person-with-name-string.avsc")));

        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_TEXT, inputSchemaText);
        runner.enableControllerService(jsonReader);

        final JsonRecordSetWriter jsonWriter = new JsonRecordSetWriter();
        runner.addControllerService("writer", jsonWriter);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_TEXT, outputSchemaText);
        runner.setProperty(jsonWriter, "Pretty Print JSON", "true");
        runner.setProperty(jsonWriter, "Schema Write Strategy", "full-schema-attribute");
        runner.enableControllerService(jsonWriter);

        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/person.json"));
        runner.setProperty("/name", "/name/first");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.RECORD_PATH_VALUES);

        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        final String expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/output/person-with-firstname.json")));
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
    }

    @Test
    public void testUpdateInArray() throws InitializationException, IOException {
        final JsonTreeReader jsonReader = new JsonTreeReader();
        runner.addControllerService("reader", jsonReader);

        final String inputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/person-with-address.avsc")));
        final String outputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/person-with-address.avsc")));

        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_TEXT, inputSchemaText);
        runner.enableControllerService(jsonReader);

        final JsonRecordSetWriter jsonWriter = new JsonRecordSetWriter();
        runner.addControllerService("writer", jsonWriter);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_TEXT, outputSchemaText);
        runner.setProperty(jsonWriter, "Pretty Print JSON", "true");
        runner.setProperty(jsonWriter, "Schema Write Strategy", "full-schema-attribute");
        runner.enableControllerService(jsonWriter);

        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/person-address.json"));
        runner.setProperty("/address[*]/city", "newCity");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.LITERAL_VALUES);

        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        final String expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/output/person-with-new-city.json")));
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
    }

    @Test
    public void testUpdateInNullArray() throws InitializationException, IOException {
        final JsonTreeReader jsonReader = new JsonTreeReader();
        runner.addControllerService("reader", jsonReader);

        final String inputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/person-with-address.avsc")));
        final String outputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/person-with-address.avsc")));

        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_TEXT, inputSchemaText);
        runner.enableControllerService(jsonReader);

        final JsonRecordSetWriter jsonWriter = new JsonRecordSetWriter();
        runner.addControllerService("writer", jsonWriter);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_TEXT, outputSchemaText);
        runner.setProperty(jsonWriter, "Pretty Print JSON", "true");
        runner.setProperty(jsonWriter, "Schema Write Strategy", "full-schema-attribute");
        runner.enableControllerService(jsonWriter);

        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/person-with-null-array.json"));
        runner.setProperty("/address[*]/city", "newCity");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.LITERAL_VALUES);

        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        final String expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/output/person-with-null-array.json")));
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
    }

    @Test
    public void testAddFieldNotInInputRecord() throws InitializationException, IOException {
        final JsonTreeReader jsonReader = new JsonTreeReader();
        runner.addControllerService("reader", jsonReader);

        final String inputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/person-with-name-record.avsc")));
        final String outputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/person-with-name-string-fields.avsc")));

        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_TEXT, inputSchemaText);
        runner.enableControllerService(jsonReader);

        final JsonRecordSetWriter jsonWriter = new JsonRecordSetWriter();
        runner.addControllerService("writer", jsonWriter);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_TEXT, outputSchemaText);
        runner.setProperty(jsonWriter, "Pretty Print JSON", "true");
        runner.setProperty(jsonWriter, "Schema Write Strategy", "full-schema-attribute");
        runner.enableControllerService(jsonWriter);

        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/person.json"));
        runner.setProperty("/firstName", "/name/first");
        runner.setProperty("/lastName", "/name/last");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.RECORD_PATH_VALUES);

        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        final String expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/output/person-with-firstname-lastname.json")));
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
    }

    @Test
    public void testFieldValuesInEL() throws InitializationException, IOException {
        final JsonTreeReader jsonReader = new JsonTreeReader();
        runner.addControllerService("reader", jsonReader);

        final String inputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/person-with-name-record.avsc")));
        final String outputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/person-with-name-record.avsc")));

        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_TEXT, inputSchemaText);
        runner.enableControllerService(jsonReader);

        final JsonRecordSetWriter jsonWriter = new JsonRecordSetWriter();
        runner.addControllerService("writer", jsonWriter);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_TEXT, outputSchemaText);
        runner.setProperty(jsonWriter, "Pretty Print JSON", "true");
        runner.setProperty(jsonWriter, "Schema Write Strategy", "full-schema-attribute");
        runner.enableControllerService(jsonWriter);

        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/person.json"));
        runner.setProperty("/name/last", "${field.value:toUpper()}");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.LITERAL_VALUES);

        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        final String expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/output/person-with-capital-lastname.json")));
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
    }

    @Test
    public void testSetRootPathAbsoluteWithMultipleValues() throws InitializationException, IOException {
        final JsonTreeReader jsonReader = new JsonTreeReader();
        runner.addControllerService("reader", jsonReader);

        final String inputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/person-with-name-record.avsc")));
        final String outputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/name-fields-only.avsc")));

        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_TEXT, inputSchemaText);
        runner.enableControllerService(jsonReader);

        final JsonRecordSetWriter jsonWriter = new JsonRecordSetWriter();
        runner.addControllerService("writer", jsonWriter);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_TEXT, outputSchemaText);
        runner.setProperty(jsonWriter, "Pretty Print JSON", "true");
        runner.setProperty(jsonWriter, "Schema Write Strategy", "full-schema-attribute");
        runner.enableControllerService(jsonWriter);

        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/person.json"));
        runner.setProperty("/", "/name/*");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.RECORD_PATH_VALUES);

        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        final String expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/output/name-fields-only.json")));
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
    }

    @Test
    public void testSetRootPathAbsoluteWithSingleValue() throws InitializationException, IOException {
        final JsonTreeReader jsonReader = new JsonTreeReader();
        runner.addControllerService("reader", jsonReader);

        final String inputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/person-with-name-record.avsc")));
        final String outputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/name-fields-only.avsc")));

        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_TEXT, inputSchemaText);
        runner.enableControllerService(jsonReader);

        final JsonRecordSetWriter jsonWriter = new JsonRecordSetWriter();
        runner.addControllerService("writer", jsonWriter);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_TEXT, outputSchemaText);
        runner.setProperty(jsonWriter, "Pretty Print JSON", "true");
        runner.setProperty(jsonWriter, "Schema Write Strategy", "full-schema-attribute");
        runner.enableControllerService(jsonWriter);

        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/person.json"));
        runner.setProperty("/", "/name");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.RECORD_PATH_VALUES);

        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        final String expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/output/name-fields-only.json")));
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
    }

    @Test
    public void testSetRootWithUnescapeJsonCall() throws InitializationException, IOException {
        final JsonTreeReader jsonReader = new JsonTreeReader();
        runner.addControllerService("reader", jsonReader);

        final String inputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/person-with-stringified-name.avsc")));
        final String outputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/name-fields-only.avsc")));

        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_TEXT, inputSchemaText);
        runner.enableControllerService(jsonReader);

        final JsonRecordSetWriter jsonWriter = new JsonRecordSetWriter();
        runner.addControllerService("writer", jsonWriter);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_TEXT, outputSchemaText);
        runner.setProperty(jsonWriter, "Pretty Print JSON", "true");
        runner.setProperty(jsonWriter, "Schema Write Strategy", "full-schema-attribute");
        runner.enableControllerService(jsonWriter);

        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/person-stringified-name.json"));
        runner.setProperty("/", "unescapeJson(/stringified_name, 'true')");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.RECORD_PATH_VALUES);

        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        final String expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/output/name-fields-only.json")));
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
    }

    @Test
    public void testSetNestedRootWithUnescapeJsonCall() throws InitializationException, IOException {
        final JsonTreeReader jsonReader = new JsonTreeReader();
        runner.addControllerService("reader", jsonReader);

        final String inputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/organisation-with-departments-string.avsc")));
        final String outputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/organisation-with-departments.avsc")));

        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_TEXT, inputSchemaText);
        runner.enableControllerService(jsonReader);

        final JsonRecordSetWriter jsonWriter = new JsonRecordSetWriter();
        runner.addControllerService("writer", jsonWriter);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_TEXT, outputSchemaText);
        runner.setProperty(jsonWriter, "Pretty Print JSON", "true");
        runner.setProperty(jsonWriter, "Schema Write Strategy", "full-schema-attribute");
        runner.enableControllerService(jsonWriter);

        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.RECORD_PATH_VALUES);
        final Path input = Paths.get("src/test/resources/TestUpdateRecord/input/organisation.json");

        // recursive conversion
        runner.enqueue(input);
        runner.setProperty("/", "unescapeJson(/record_json, 'true', 'true')");

        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        String expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/output/organisation.json")));
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).getFirst().assertContentEquals(expectedOutput);
        assertTrue(runner.getLogger().getErrorMessages().isEmpty());

        // no conversion
        runner.clearTransferState();
        runner.enqueue(input);
        runner.setProperty("/", "unescapeJson(/record_json)");

        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        expectedOutput = """
                [ {
                  "name" : null,
                  "departments" : null,
                  "address" : null
                } ]""";
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).getFirst().assertContentEquals(expectedOutput);
        assertTrue(runner.getLogger().getErrorMessages().isEmpty());

        // non-recursive conversion
        runner.clearTransferState();
        runner.enqueue(input);
        runner.setProperty("/", "unescapeJson(/record_json, 'true')");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.RECORD_PATH_VALUES);

        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_FAILURE, 1);
        final LogMessage errorMessage = runner.getLogger().getErrorMessages().getFirst();
        assertTrue(errorMessage.getMsg().contains("ClassCastException"));
        assertTrue(errorMessage.getMsg().contains("Record"));
    }

    @Test
    public void testSetFieldWithUnescapeJsonCall() throws InitializationException, IOException {
        final JsonTreeReader jsonReader = new JsonTreeReader();
        runner.addControllerService("reader", jsonReader);

        final String inputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/person-with-stringified-name.avsc")));
        final String outputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/person-with-name-record.avsc")));

        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_TEXT, inputSchemaText);
        runner.enableControllerService(jsonReader);

        final JsonRecordSetWriter jsonWriter = new JsonRecordSetWriter();
        runner.addControllerService("writer", jsonWriter);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_TEXT, outputSchemaText);
        runner.setProperty(jsonWriter, "Pretty Print JSON", "true");
        runner.setProperty(jsonWriter, "Schema Write Strategy", "full-schema-attribute");
        runner.enableControllerService(jsonWriter);

        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/person-stringified-name.json"));
        runner.setProperty("/name", "unescapeJson(/stringified_name)");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.RECORD_PATH_VALUES);

        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        final String expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/output/person-with-name.json")));
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
    }

    @Test
    public void testSetNestedRecordWithUnescapeJsonCall() throws InitializationException, IOException {
        final JsonTreeReader jsonReader = new JsonTreeReader();
        runner.addControllerService("reader", jsonReader);

        final String schemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/embedded-record.avsc")));

        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_TEXT, schemaText);
        runner.enableControllerService(jsonReader);

        final JsonRecordSetWriter jsonWriter = new JsonRecordSetWriter();
        runner.addControllerService("writer", jsonWriter);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_TEXT, schemaText);
        runner.setProperty(jsonWriter, "Pretty Print JSON", "true");
        runner.setProperty(jsonWriter, "Schema Write Strategy", "full-schema-attribute");
        runner.enableControllerService(jsonWriter);

        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/embedded-string.json"));
        runner.setProperty("/embedded", "unescapeJson(/str)");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.RECORD_PATH_VALUES);

        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        final String expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/output/embedded-record.json")));
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
    }

    @Test
    public void testSetRootPathRelativeWithMultipleValues() throws InitializationException, IOException {
        final JsonTreeReader jsonReader = new JsonTreeReader();
        runner.addControllerService("reader", jsonReader);

        final String inputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/person-with-name-record.avsc")));
        final String outputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/name-fields-only.avsc")));

        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_TEXT, inputSchemaText);
        runner.enableControllerService(jsonReader);

        final JsonRecordSetWriter jsonWriter = new JsonRecordSetWriter();
        runner.addControllerService("writer", jsonWriter);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_TEXT, outputSchemaText);
        runner.setProperty(jsonWriter, "Pretty Print JSON", "true");
        runner.setProperty(jsonWriter, "Schema Write Strategy", "full-schema-attribute");
        runner.enableControllerService(jsonWriter);

        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/person.json"));
        runner.setProperty("/name/..", "/name/*");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.RECORD_PATH_VALUES);

        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        final String expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/output/name-fields-only.json")));
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
    }

    @Test
    public void testSetRootPathRelativeWithSingleValue() throws InitializationException, IOException {
        final JsonTreeReader jsonReader = new JsonTreeReader();
        runner.addControllerService("reader", jsonReader);

        final String inputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/person-with-name-record.avsc")));
        final String outputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/name-fields-only.avsc")));

        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_TEXT, inputSchemaText);
        runner.enableControllerService(jsonReader);

        final JsonRecordSetWriter jsonWriter = new JsonRecordSetWriter();
        runner.addControllerService("writer", jsonWriter);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_TEXT, outputSchemaText);
        runner.setProperty(jsonWriter, "Pretty Print JSON", "true");
        runner.setProperty(jsonWriter, "Schema Write Strategy", "full-schema-attribute");
        runner.enableControllerService(jsonWriter);

        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/person.json"));
        runner.setProperty("/name/..", "/name");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.RECORD_PATH_VALUES);

        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        final String expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/output/name-fields-only.json")));
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
    }

    @Test
    public void testSetAbsolutePathWithAnotherRecord() throws InitializationException, IOException {
        final JsonTreeReader jsonReader = new JsonTreeReader();
        runner.addControllerService("reader", jsonReader);

        final String inputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/person-with-name-and-mother.avsc")));
        final String outputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/person-with-name-and-mother.avsc")));

        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_TEXT, inputSchemaText);
        runner.enableControllerService(jsonReader);

        final JsonRecordSetWriter jsonWriter = new JsonRecordSetWriter();
        runner.addControllerService("writer", jsonWriter);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_TEXT, outputSchemaText);
        runner.setProperty(jsonWriter, "Pretty Print JSON", "true");
        runner.setProperty(jsonWriter, "Schema Write Strategy", "full-schema-attribute");
        runner.enableControllerService(jsonWriter);

        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/person.json"));
        runner.setProperty("/name", "/mother");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.RECORD_PATH_VALUES);

        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        final String expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/output/name-and-mother-same.json")));
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
    }

    @Test
    public void testUpdateSimpleArray() throws InitializationException, IOException {
        final JsonTreeReader jsonReader = new JsonTreeReader();
        runner.addControllerService("reader", jsonReader);

        final String inputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/multi-arrays.avsc")));
        final String outputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/multi-arrays.avsc")));

        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_TEXT, inputSchemaText);
        runner.enableControllerService(jsonReader);

        final JsonRecordSetWriter jsonWriter = new JsonRecordSetWriter();
        runner.addControllerService("writer", jsonWriter);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_TEXT, outputSchemaText);
        runner.setProperty(jsonWriter, "Pretty Print JSON", "true");
        runner.setProperty(jsonWriter, "Schema Write Strategy", "full-schema-attribute");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.LITERAL_VALUES);
        runner.enableControllerService(jsonWriter);

        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/multi-arrays.json"));
        runner.setProperty("/numbers[*]", "8");
        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        String expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/input/multi-arrays.json")));
        expectedOutput = expectedOutput.replaceFirst("1, null, 4", "8, 8, 8");
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
        runner.removeProperty("/numbers[*]");

        runner.clearTransferState();
        runner.enqueue("{\"numbers\":null}");
        runner.setProperty("/numbers[*]", "8");
        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        String content = new String(runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).toByteArray());
        assertTrue(content.contains("\"numbers\" : null"));
        runner.removeProperty("/numbers[*]");

        runner.clearTransferState();
        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/multi-arrays.json"));
        runner.setProperty("/numbers[1]", "8");
        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/input/multi-arrays.json")));
        expectedOutput = expectedOutput.replaceFirst("1, null, 4", "1, 8, 4");
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
        runner.removeProperty("/numbers[1]");

        runner.clearTransferState();
        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/multi-arrays.json"));
        runner.setProperty("/numbers[0..1]", "8");
        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/input/multi-arrays.json")));
        expectedOutput = expectedOutput.replaceFirst("1, null, 4", "8, 8, 4");
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
        runner.removeProperty("/numbers[0..1]");

        runner.clearTransferState();
        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/multi-arrays.json"));
        runner.setProperty("/numbers[0,2]", "8");
        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/input/multi-arrays.json")));
        expectedOutput = expectedOutput.replaceFirst("1, null, 4", "8, null, 8");
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
        runner.removeProperty("/numbers[0,2]");

        runner.clearTransferState();
        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/multi-arrays.json"));
        runner.setProperty("/numbers[0,1..2]", "8");
        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/input/multi-arrays.json")));
        expectedOutput = expectedOutput.replaceFirst("1, null, 4", "8, 8, 8");
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
        runner.removeProperty("/numbers[0,1..2]");

        runner.clearTransferState();
        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/multi-arrays.json"));
        runner.setProperty("/numbers[0..-1][. = 4]", "8");
        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/input/multi-arrays.json")));
        expectedOutput = expectedOutput.replaceFirst("1, null, 4", "1, null, 8");
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
        runner.removeProperty("/numbers[0..-1][. = 4]");
    }

    @Test
    public void testUpdateComplexArrays() throws InitializationException, IOException {
        final JsonTreeReader jsonReader = new JsonTreeReader();
        runner.addControllerService("reader", jsonReader);

        final String inputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/multi-arrays.avsc")));
        final String outputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/schema/multi-arrays.avsc")));

        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonReader, SchemaAccessUtils.SCHEMA_TEXT, inputSchemaText);
        runner.enableControllerService(jsonReader);

        final JsonRecordSetWriter jsonWriter = new JsonRecordSetWriter();
        runner.addControllerService("writer", jsonWriter);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_TEXT, outputSchemaText);
        runner.setProperty(jsonWriter, "Pretty Print JSON", "true");
        runner.setProperty(jsonWriter, "Schema Write Strategy", "full-schema-attribute");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.RECORD_PATH_VALUES);
        runner.enableControllerService(jsonWriter);

        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/multi-arrays.json"));
        runner.setProperty("/peoples[*]", "/peoples[3]");
        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        String content = new String(runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).toByteArray());
        int count = StringUtils.countMatches(content, "Mary Doe");
        assertEquals(4, count);
        runner.removeProperty("/peoples[*]");

        runner.clearTransferState();
        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/multi-arrays.json"));
        runner.setProperty("/peoples[1]", "/peoples[3]");
        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        content = new String(runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).toByteArray());
        count = StringUtils.countMatches(content, "Mary Doe");
        assertEquals(2, count);
        runner.removeProperty("/peoples[1]");

        runner.clearTransferState();
        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/multi-arrays.json"));
        runner.setProperty("/peoples[0..1]", "/peoples[3]");
        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        String expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/output/updateArrays/multi-arrays-0and1.json")));
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
        runner.removeProperty("/peoples[0..1]");

        runner.clearTransferState();
        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/multi-arrays.json"));
        runner.setProperty("/peoples[0,2]", "/peoples[3]");
        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/output/updateArrays/multi-arrays-0and2.json")));
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
        runner.removeProperty("/peoples[0,2]");

        runner.clearTransferState();
        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/multi-arrays.json"));
        runner.setProperty("/peoples[0,1..2]", "/peoples[3]");
        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        content = new String(runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).toByteArray());
        count = StringUtils.countMatches(content, "Mary Doe");
        assertEquals(4, count);
        runner.removeProperty("/peoples[0,1..2]");

        runner.clearTransferState();
        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/multi-arrays.json"));
        runner.setProperty("/peoples[0..-1][./name != 'Mary Doe']", "/peoples[3]");
        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        content = new String(runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).toByteArray());
        count = StringUtils.countMatches(content, "Mary Doe");
        assertEquals(4, count);
        runner.removeProperty("/peoples[0..-1][./name != 'Mary Doe']");

        runner.clearTransferState();
        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/multi-arrays.json"));
        runner.setProperty("/peoples[0..-1][./name != 'Mary Doe']/addresses[*]", "/peoples[3]/addresses[0]");
        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        content = new String(runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).toByteArray());
        count = StringUtils.countMatches(content, "1 nifi road");
        assertEquals(13, count);
        runner.removeProperty("/peoples[0..-1][./name != 'Mary Doe']/addresses[*]");

        runner.clearTransferState();
        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/multi-arrays.json"));
        runner.setProperty("/peoples[0..-1][./name != 'Mary Doe']/addresses[0,1..2]", "/peoples[3]/addresses[0]");
        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        expectedOutput = new String(Files.readAllBytes(Paths.get("src/test/resources/TestUpdateRecord/output/updateArrays/multi-arrays-streets.json")));
        runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).assertContentEquals(expectedOutput);
        runner.removeProperty("/peoples[0..-1][./name != 'Mary Doe']/addresses[0,1..2]");

        runner.clearTransferState();
        runner.enqueue(Paths.get("src/test/resources/TestUpdateRecord/input/multi-arrays.json"));
        runner.setProperty("/peoples[0..-1][./name != 'Mary Doe']/addresses[0,1..2]/city", "newCity");
        runner.setProperty(UpdateRecord.REPLACEMENT_VALUE_STRATEGY, UpdateRecord.LITERAL_VALUES);
        runner.run();
        runner.assertAllFlowFilesTransferred(UpdateRecord.REL_SUCCESS, 1);
        content = new String(runner.getFlowFilesForRelationship(UpdateRecord.REL_SUCCESS).get(0).toByteArray());
        count = StringUtils.countMatches(content, "newCity");
        assertEquals(9, count);
        runner.removeProperty("/peoples[0..-1][./name != 'Mary Doe']/addresses[0,1..2]/city");
    }

}
