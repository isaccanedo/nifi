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

import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.serialization.SimpleRecordSchema;
import org.apache.nifi.serialization.record.CommaSeparatedRecordReader;
import org.apache.nifi.serialization.record.MockRecordWriter;
import org.apache.nifi.serialization.record.RecordField;
import org.apache.nifi.serialization.record.RecordFieldType;
import org.apache.nifi.serialization.record.RecordSchema;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestMergeRecord {
    private TestRunner runner;
    private CommaSeparatedRecordReader readerService;
    private MockRecordWriter writerService;

    @BeforeEach
    public void setup() throws InitializationException {
        runner = TestRunners.newTestRunner(new MergeRecord());

        readerService = new CommaSeparatedRecordReader();
        writerService = new MockRecordWriter("header", false, true);

        runner.addControllerService("reader", readerService);

        runner.enableControllerService(readerService);

        runner.addControllerService("writer", writerService);
        runner.enableControllerService(writerService);

        runner.setProperty(MergeRecord.RECORD_READER, "reader");
        runner.setProperty(MergeRecord.RECORD_WRITER, "writer");
    }

    @Test
    public void testSmallOutputIsFlushed() {
        runner.setProperty(MergeRecord.MIN_RECORDS, "1");
        runner.setProperty(MergeRecord.MAX_RECORDS, "1");

        runner.enqueue("Name, Age\nJohn, 35\nJane, 34");

        runner.run(1);
        runner.assertTransferCount(MergeRecord.REL_MERGED, 1);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 1);

        final MockFlowFile mff = runner.getFlowFilesForRelationship(MergeRecord.REL_MERGED).get(0);
        mff.assertAttributeEquals("record.count", "2");
        mff.assertContentEquals("header\nJohn,35\nJane,34\n");

        runner.getFlowFilesForRelationship(MergeRecord.REL_ORIGINAL).forEach(
            ff -> assertEquals(mff.getAttribute(CoreAttributes.UUID.key()), ff.getAttribute(MergeRecord.MERGE_UUID_ATTRIBUTE)));
    }

    @Test
    public void testMergeSimple() {
        runner.enqueue("Name, Age\nJohn, 35");
        runner.enqueue("Name, Age\nJane, 34");

        runner.run(2);
        runner.assertTransferCount(MergeRecord.REL_MERGED, 1);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 2);

        final MockFlowFile mff = runner.getFlowFilesForRelationship(MergeRecord.REL_MERGED).get(0);
        mff.assertAttributeEquals("record.count", "2");
        mff.assertContentEquals("header\nJohn,35\nJane,34\n");

        runner.getFlowFilesForRelationship(MergeRecord.REL_ORIGINAL).forEach(
                ff -> assertEquals(mff.getAttribute(CoreAttributes.UUID.key()), ff.getAttribute(MergeRecord.MERGE_UUID_ATTRIBUTE)));
    }

    @Test
    public void testMergeSimpleDifferentWriteSchema() throws InitializationException {
        // Exclude Age field
        List<RecordField> writeFields = Collections.singletonList(
          new RecordField("Name", RecordFieldType.STRING.getDataType())
        );
        RecordSchema writeSchema = new SimpleRecordSchema(writeFields);
        writerService = new MockRecordWriter("header", false, -1, true, writeSchema);
        runner.addControllerService("differentWriter", writerService);
        runner.enableControllerService(writerService);

        runner.setProperty(MergeRecord.RECORD_READER, "reader");
        runner.setProperty(MergeRecord.RECORD_WRITER, "differentWriter");
        runner.enqueue("Name, Age\nJohn, 35");
        runner.enqueue("Name, Age\nJane, 34");

        runner.run(2);
        runner.assertTransferCount(MergeRecord.REL_MERGED, 1);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 2);

        final MockFlowFile mff = runner.getFlowFilesForRelationship(MergeRecord.REL_MERGED).get(0);
        mff.assertAttributeEquals("record.count", "2");
        mff.assertContentEquals("header\nJohn\nJane\n");

        runner.getFlowFilesForRelationship(MergeRecord.REL_ORIGINAL).forEach(
                ff -> assertEquals(mff.getAttribute(CoreAttributes.UUID.key()), ff.getAttribute(MergeRecord.MERGE_UUID_ATTRIBUTE)));
    }


    // Verify that FlowFiles are grouped with like schemas.
    @Test
    public void testDifferentSchema() {
        runner.setProperty(MergeRecord.MIN_RECORDS, "2");

        runner.enqueue("Name, Age\nJohn, 35");
        runner.enqueue("Name, Color\nJane, Red");

        runner.run(1, false, true);

        runner.assertTransferCount(MergeRecord.REL_MERGED, 0);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 0);

        runner.enqueue("Name, Age\nJane, 34");
        runner.enqueue("Name, Color\nJohn, Blue");

        runner.run(1, false, false);
        runner.run(1, true, false);

        runner.assertTransferCount(MergeRecord.REL_MERGED, 2);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 4);

        final List<MockFlowFile> mffs = runner.getFlowFilesForRelationship(MergeRecord.REL_MERGED);
        assertEquals(1L, mffs.stream()
            .filter(ff -> "2".equals(ff.getAttribute("record.count")))
            .filter(ff -> "header\nJohn,35\nJane,34\n".equals(new String(ff.toByteArray())))
            .count());

        assertEquals(1L, mffs.stream()
            .filter(ff -> "2".equals(ff.getAttribute("record.count")))
            .filter(ff -> "header\nJane,Red\nJohn,Blue\n".equals(new String(ff.toByteArray())))
            .count());
    }

    @Test
    public void testFailureToParse() {
        runner.setProperty(MergeRecord.MIN_RECORDS, "3");

        readerService.failAfter(2);

        runner.enqueue("Name, Age\nJohn, 35");
        runner.enqueue("Name, Age\nJane, 34");
        runner.enqueue("Name, Age\nJake, 3");

        runner.run();

        // We have to route all of the FlowFiles in the same bin to the 'failure' relationship.
        // Otherwise, we may have read some of the records from the failing FlowFile and then
        // routed it to failure, which would result in some of its records moving on and others not.
        // This, in turn, would result in the same records being added to potentially many FlowFiles.
        runner.assertAllFlowFilesTransferred(MergeRecord.REL_FAILURE, 3);
    }

    @Test
    public void testDefragment() {
        runner.setProperty(MergeRecord.MERGE_STRATEGY, MergeRecord.MERGE_STRATEGY_DEFRAGMENT);

        final Map<String, String> attr1 = new HashMap<>();
        attr1.put(MergeRecord.FRAGMENT_COUNT_ATTRIBUTE, "2");
        attr1.put(MergeRecord.FRAGMENT_ID_ATTRIBUTE, "1");
        attr1.put(MergeRecord.FRAGMENT_INDEX_ATTRIBUTE, "0");

        final Map<String, String> attr2 = new HashMap<>();
        attr2.put(MergeRecord.FRAGMENT_COUNT_ATTRIBUTE, "2");
        attr2.put(MergeRecord.FRAGMENT_ID_ATTRIBUTE, "1");
        attr2.put(MergeRecord.FRAGMENT_INDEX_ATTRIBUTE, "1");

        final Map<String, String> attr3 = new HashMap<>();
        attr3.put(MergeRecord.FRAGMENT_COUNT_ATTRIBUTE, "2");
        attr3.put(MergeRecord.FRAGMENT_ID_ATTRIBUTE, "2");
        attr3.put(MergeRecord.FRAGMENT_INDEX_ATTRIBUTE, "0");

        final Map<String, String> attr4 = new HashMap<>();
        attr4.put(MergeRecord.FRAGMENT_COUNT_ATTRIBUTE, "2");
        attr4.put(MergeRecord.FRAGMENT_ID_ATTRIBUTE, "3");
        attr4.put(MergeRecord.FRAGMENT_INDEX_ATTRIBUTE, "0");

        final Map<String, String> attr5 = new HashMap<>();
        attr5.put(MergeRecord.FRAGMENT_COUNT_ATTRIBUTE, "2");
        attr5.put(MergeRecord.FRAGMENT_ID_ATTRIBUTE, "3");
        attr5.put(MergeRecord.FRAGMENT_INDEX_ATTRIBUTE, "1");

        runner.enqueue("Name, Age\nJohn, 35", attr1);
        runner.enqueue("Name, Age\nJane, 34", attr2);

        runner.enqueue("Name, Age\nJay, 24", attr3);

        runner.enqueue("Name, Age\nJake, 3", attr4);
        runner.enqueue("Name, Age\nJan, 2", attr5);

        runner.run(1);

        assertEquals(1, runner.getQueueSize().getObjectCount(), "Fragment id=2 should remain in the incoming connection");
        runner.assertTransferCount(MergeRecord.REL_MERGED, 2);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 4);

        final List<MockFlowFile> mffs = runner.getFlowFilesForRelationship(MergeRecord.REL_MERGED);
        assertEquals(1L, mffs.stream()
            .filter(ff -> "2".equals(ff.getAttribute("record.count")))
            .filter(ff -> "header\nJohn,35\nJane,34\n".equals(new String(ff.toByteArray())))
            .count());

        assertEquals(1L, mffs.stream()
            .filter(ff -> "2".equals(ff.getAttribute("record.count")))
            .filter(ff -> "header\nJake,3\nJan,2\n".equals(new String(ff.toByteArray())))
            .count());
    }

    @Test
    public void testDefragmentOverMultipleCalls() {
        runner.setProperty(MergeRecord.MERGE_STRATEGY, MergeRecord.MERGE_STRATEGY_DEFRAGMENT);

        final Map<String, String> attr1 = new HashMap<>();
        attr1.put(MergeRecord.FRAGMENT_COUNT_ATTRIBUTE, "2");
        attr1.put(MergeRecord.FRAGMENT_ID_ATTRIBUTE, "1");
        attr1.put(MergeRecord.FRAGMENT_INDEX_ATTRIBUTE, "0");

        runner.enqueue("Name, Age\nJohn, 35", attr1);
        runner.run(2);

        assertEquals(1, runner.getQueueSize().getObjectCount(), "Fragment should remain in the incoming connection");
        runner.assertTransferCount(MergeRecord.REL_MERGED, 0);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 0);
        runner.assertTransferCount(MergeRecord.REL_FAILURE, 0);

        final Map<String, String> attr2 = new HashMap<>();
        attr2.put(MergeRecord.FRAGMENT_COUNT_ATTRIBUTE, "2");
        attr2.put(MergeRecord.FRAGMENT_ID_ATTRIBUTE, "1");
        attr2.put(MergeRecord.FRAGMENT_INDEX_ATTRIBUTE, "1");

        runner.enqueue("Name, Age\nJane, 34", attr2);
        runner.run(1);

        assertEquals(0, runner.getQueueSize().getObjectCount(), "Fragments should merge");
        runner.assertTransferCount(MergeRecord.REL_MERGED, 1);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 2);
        runner.assertTransferCount(MergeRecord.REL_FAILURE, 0);

        final List<MockFlowFile> mffs = runner.getFlowFilesForRelationship(MergeRecord.REL_MERGED);
        assertEquals(1L, mffs.stream()
                .filter(ff -> "2".equals(ff.getAttribute("record.count")))
                .filter(ff -> "header\nJohn,35\nJane,34\n".equals(new String(ff.toByteArray())))
                .count());
    }

    @Test
    public void testDefragmentWithMultipleRecords() {
        runner.setProperty(MergeRecord.MERGE_STRATEGY, MergeRecord.MERGE_STRATEGY_DEFRAGMENT);

        final Map<String, String> attr1 = new HashMap<>();
        attr1.put(MergeRecord.FRAGMENT_COUNT_ATTRIBUTE, "2");
        attr1.put(MergeRecord.FRAGMENT_ID_ATTRIBUTE, "1");
        attr1.put(MergeRecord.FRAGMENT_INDEX_ATTRIBUTE, "0");
        attr1.put("record.count", "2");

        final Map<String, String> attr2 = new HashMap<>();
        attr2.put(MergeRecord.FRAGMENT_COUNT_ATTRIBUTE, "2");
        attr2.put(MergeRecord.FRAGMENT_ID_ATTRIBUTE, "1");
        attr2.put(MergeRecord.FRAGMENT_INDEX_ATTRIBUTE, "1");
        attr2.put("record.count", "2");

        final Map<String, String> attr3 = new HashMap<>();
        attr3.put(MergeRecord.FRAGMENT_COUNT_ATTRIBUTE, "2");
        attr3.put(MergeRecord.FRAGMENT_ID_ATTRIBUTE, "2");
        attr3.put(MergeRecord.FRAGMENT_INDEX_ATTRIBUTE, "0");
        attr3.put("record.count", "2");

        runner.enqueue("Name, Age\nJohn, 35\nJane, 34", attr1);

        runner.enqueue("Name, Age\nJake, 3\nJan, 2", attr2);

        runner.enqueue("Name, Age\nJay, 24\nJade, 28", attr3);

        runner.run(1);

        assertEquals(1, runner.getQueueSize().getObjectCount(), "Fragment id=2 should remain in the incoming connection");
        runner.assertTransferCount(MergeRecord.REL_MERGED, 1);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 2);

        final List<MockFlowFile> mffs = runner.getFlowFilesForRelationship(MergeRecord.REL_MERGED);
        assertEquals(1L, mffs.stream()
            .filter(ff -> "4".equals(ff.getAttribute("record.count")))
            .filter(ff -> "header\nJohn,35\nJane,34\nJake,3\nJan,2\n".equals(new String(ff.toByteArray())))
            .count());

    }

    @Test
    public void testDefragmentWithMultipleRecordsOverMultipleCalls() {
        runner.setProperty(MergeRecord.MERGE_STRATEGY, MergeRecord.MERGE_STRATEGY_DEFRAGMENT);

        final Map<String, String> attr1 = new HashMap<>();
        attr1.put(MergeRecord.FRAGMENT_COUNT_ATTRIBUTE, "2");
        attr1.put(MergeRecord.FRAGMENT_ID_ATTRIBUTE, "1");
        attr1.put(MergeRecord.FRAGMENT_INDEX_ATTRIBUTE, "0");
        attr1.put("record.count", "2");

        final Map<String, String> attr2 = new HashMap<>();
        attr2.put(MergeRecord.FRAGMENT_COUNT_ATTRIBUTE, "2");
        attr2.put(MergeRecord.FRAGMENT_ID_ATTRIBUTE, "1");
        attr2.put(MergeRecord.FRAGMENT_INDEX_ATTRIBUTE, "1");
        attr2.put("record.count", "2");

        runner.enqueue("Name, Age\nJohn, 35\nJane, 34", attr1);

        runner.run(2);

        assertEquals(1, runner.getQueueSize().getObjectCount(), "Fragment id=1 should remain in the incoming connection");
        runner.assertTransferCount(MergeRecord.REL_MERGED, 0);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 0);

        runner.enqueue("Name, Age\nJake, 3\nJan, 2", attr2);

        runner.run(1);

        assertEquals(0, runner.getQueueSize().getObjectCount(), "Fragment id=1 should be merged");
        runner.assertTransferCount(MergeRecord.REL_MERGED, 1);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 2);

        final List<MockFlowFile> mffs = runner.getFlowFilesForRelationship(MergeRecord.REL_MERGED);
        assertEquals(1L, mffs.stream()
            .filter(ff -> "4".equals(ff.getAttribute("record.count")))
            .filter(ff -> "header\nJohn,35\nJane,34\nJake,3\nJan,2\n".equals(new String(ff.toByteArray())))
            .count());

    }


    @Test
    public void testMinSize() {
        runner.setProperty(MergeRecord.MIN_RECORDS, "2");
        runner.setProperty(MergeRecord.MIN_SIZE, "500 B");

        runner.enqueue("Name, Age\nJohn, 35");
        runner.enqueue("Name, Age\nJane, 34");

        runner.run();
        runner.assertTransferCount(MergeRecord.REL_MERGED, 0);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 0);

        final StringBuilder sb = new StringBuilder("Name, Age\n");
        for (int i = 0; i < 100; i++) {
            sb.append("Person " + i + ", " + i + "\n");
        }
        runner.enqueue(sb.toString());

        runner.run(2);

        runner.assertTransferCount(MergeRecord.REL_MERGED, 1);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 3);
    }

    @Test
    public void testValidation() {
        runner.setProperty(MergeRecord.MIN_RECORDS, "103");
        runner.setProperty(MergeRecord.MAX_RECORDS, "2");
        runner.setProperty(MergeRecord.MIN_SIZE, "500 B");

        runner.assertNotValid();

        runner.setProperty(MergeRecord.MIN_RECORDS, "2");
        runner.setProperty(MergeRecord.MAX_RECORDS, "103");
        runner.assertValid();
    }

    @Test
    public void testMinRecords() {
        runner.setProperty(MergeRecord.MIN_RECORDS, "103");
        runner.setProperty(MergeRecord.MIN_SIZE, "500 B");

        runner.enqueue("Name, Age\nJohn, 35");
        runner.enqueue("Name, Age\nJane, 34");

        final StringBuilder sb = new StringBuilder("Name, Age\n");
        for (int i = 0; i < 100; i++) {
            sb.append("Person " + i + ", " + i + "\n");
        }
        runner.enqueue(sb.toString());

        runner.run();
        runner.assertTransferCount(MergeRecord.REL_MERGED, 0);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 0);

        runner.enqueue("Name, Age\nJohn, 35");
        runner.run(2);
        runner.assertTransferCount(MergeRecord.REL_MERGED, 1);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 4);
    }

    @Test
    public void testMaxRecords() {
        runner.setProperty(MergeRecord.MIN_RECORDS, "5");
        runner.setProperty(MergeRecord.MAX_RECORDS, "10");

        for (int i = 0; i < 34; i++) {
            runner.enqueue("Name, Age\nJohn, 35");
        }

        runner.run();
        runner.assertTransferCount(MergeRecord.REL_MERGED, 3);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 30);

        assertEquals(4, runner.getQueueSize().getObjectCount());

        runner.getFlowFilesForRelationship(MergeRecord.REL_MERGED).stream().forEach(ff -> ff.assertAttributeEquals("record.count", "10"));
    }

    @Test
    public void testMaxSize() {
        runner.setProperty(MergeRecord.MIN_RECORDS, "5");
        runner.setProperty(MergeRecord.MAX_SIZE, "100 B");

        for (int i = 0; i < 36; i++) {
            runner.enqueue("Name, Age\nJohnny, 5");
        }

        runner.run();
        runner.assertTransferCount(MergeRecord.REL_MERGED, 3);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 33);

        assertEquals(3, runner.getQueueSize().getObjectCount());
    }

    @Test
    @EnabledIfSystemProperty(
            named = "nifi.test.performance",
            matches = "true",
            disabledReason = "This unit test depends on timing and could potentially cause problems in an automated build environment. However, it can be useful for manual testing"
    )
    public void testTimeout() throws InterruptedException {
        runner.setProperty(MergeRecord.MIN_RECORDS, "500");
        runner.setProperty(MergeRecord.MAX_BIN_AGE, "500 millis");

        for (int i = 0; i < 100; i++) {
            runner.enqueue("Name, Age\nJohnny, 5");
        }

        runner.run(1, false);
        runner.assertTransferCount(MergeRecord.REL_MERGED, 0);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 0);

        Thread.sleep(750);
        runner.run(1, true, false);

        runner.assertTransferCount(MergeRecord.REL_MERGED, 1);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 100);
    }


    @Test
    public void testBinCount() {
        runner.setProperty(MergeRecord.MIN_RECORDS, "5");
        runner.setProperty(MergeRecord.MAX_RECORDS, "10");
        runner.setProperty(MergeRecord.MAX_BIN_COUNT, "5");
        runner.setProperty(MergeRecord.CORRELATION_ATTRIBUTE_NAME, "correlationId");

        final Map<String, String> attrs = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            attrs.put("correlationId", String.valueOf(i));
            runner.enqueue("Name, Age\nJohn, 3" + i, attrs);
        }

        runner.run(1, false);

        runner.assertTransferCount(MergeRecord.REL_MERGED, 0);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 0);
        runner.assertTransferCount(MergeRecord.REL_FAILURE, 0);

        attrs.put("correlationId", "5");
        runner.enqueue("Name, Age\nJohn, 35", attrs);
        assertEquals(5, ((MergeRecord) runner.getProcessor()).getBinCount());
        runner.run(1, false, false);

        runner.assertTransferCount(MergeRecord.REL_MERGED, 1);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 1);
        runner.assertTransferCount(MergeRecord.REL_FAILURE, 0);
        assertEquals(5, ((MergeRecord) runner.getProcessor()).getBinCount());
    }

    @Test
    public void testDefragmentOldestBinFailsWhenTooManyBins() {
        runner.setProperty(MergeRecord.MAX_BIN_COUNT, "5");
        runner.setProperty(MergeRecord.MERGE_STRATEGY, MergeRecord.MERGE_STRATEGY_DEFRAGMENT);

        final Map<String, String> attrs = new HashMap<>();
        attrs.put(MergeRecord.FRAGMENT_COUNT_ATTRIBUTE, "5");
        for (int i = 0; i < 5; i++) {
            attrs.put(MergeRecord.FRAGMENT_ID_ATTRIBUTE, String.valueOf(i));
            runner.enqueue("Name, Age\nJohn, 3" + i, attrs);
        }

        runner.run(1, false);

        runner.assertTransferCount(MergeRecord.REL_MERGED, 0);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 0);
        runner.assertTransferCount(MergeRecord.REL_FAILURE, 0);

        attrs.put(MergeRecord.FRAGMENT_ID_ATTRIBUTE, "5");
        runner.enqueue("Name, Age\nJohn, 35", attrs);
        assertEquals(5, ((MergeRecord) runner.getProcessor()).getBinCount());
        runner.run(1, false, false);

        runner.assertTransferCount(MergeRecord.REL_MERGED, 0);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 0);
        runner.assertTransferCount(MergeRecord.REL_FAILURE, 1);
        assertEquals(5, ((MergeRecord) runner.getProcessor()).getBinCount());
    }

    @Test
    public void testDefragmentExpiredBinFailsOnTimeout() throws InterruptedException {
        runner.setProperty(MergeRecord.MAX_BIN_COUNT, "5");
        runner.setProperty(MergeRecord.MERGE_STRATEGY, MergeRecord.MERGE_STRATEGY_DEFRAGMENT);
        runner.setProperty(MergeRecord.MAX_BIN_AGE, "1 millis");

        final Map<String, String> attrs = new HashMap<>();
        attrs.put(MergeRecord.FRAGMENT_COUNT_ATTRIBUTE, "5");
        attrs.put(MergeRecord.FRAGMENT_ID_ATTRIBUTE, "0");
        runner.enqueue("Name, Age\nJohn, 30", attrs);

        runner.run(1, false);

        Thread.sleep(50L);
        runner.run(1, false, false);
        runner.run(1, true, false);

        runner.assertTransferCount(MergeRecord.REL_MERGED, 0);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 0);
        runner.assertTransferCount(MergeRecord.REL_FAILURE, 1);
    }

    @Test
    public void testMergeWithMinRecordsFromVariableRegistry() {
        runner.setEnvironmentVariableValue("min_records", "3");
        runner.setEnvironmentVariableValue("max_records", "3");
        runner.setValidateExpressionUsage(true);

        // Test MIN_RECORDS
        runner.setProperty(MergeRecord.MIN_RECORDS, "${min_records}");
        runner.setProperty(MergeRecord.MAX_RECORDS, "3");

        runner.enqueue("Name, Age\nJohn, 35");
        runner.enqueue("Name, Age\nJane, 34");
        runner.enqueue("Name, Age\nAlex, 28");

        runner.run(1);
        runner.assertTransferCount(MergeRecord.REL_MERGED, 1);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 3);

        final MockFlowFile mff = runner.getFlowFilesForRelationship(MergeRecord.REL_MERGED).get(0);
        mff.assertAttributeEquals("record.count", "3");
        mff.assertContentEquals("header\nJohn,35\nJane,34\nAlex,28\n");
        runner.clearTransferState();

        // Test MAX_RECORDS
        runner.setProperty(MergeRecord.MIN_RECORDS, "1");
        runner.setProperty(MergeRecord.MAX_RECORDS, "${max_records}");

        runner.enqueue("Name, Age\nJohn, 35");
        runner.enqueue("Name, Age\nJane, 34");
        runner.enqueue("Name, Age\nAlex, 28");
        runner.enqueue("Name, Age\nDonna, 48");
        runner.enqueue("Name, Age\nJoey, 45");

        runner.run(2);
        runner.assertTransferCount(MergeRecord.REL_MERGED, 2);
        runner.assertTransferCount(MergeRecord.REL_ORIGINAL, 5);

        final MockFlowFile mff1 = runner.getFlowFilesForRelationship(MergeRecord.REL_MERGED).get(0);
        mff1.assertAttributeEquals("record.count", "3");
        mff1.assertContentEquals("header\nJohn,35\nJane,34\nAlex,28\n");

        final MockFlowFile mff2 = runner.getFlowFilesForRelationship(MergeRecord.REL_MERGED).get(1);
        mff2.assertAttributeEquals("record.count", "2");
        mff2.assertContentEquals("header\nDonna,48\nJoey,45\n");
        runner.clearTransferState();

        runner.removeProperty("min_records");
        runner.removeProperty("max_records");
    }

    @Test
    public void testNegativeMinAndMaxRecordsValidators() {

        runner.setEnvironmentVariableValue("min_records", "-3");
        runner.setEnvironmentVariableValue("max_records", "-1");

        // This configuration breaks the "<Minimum Number of Records> property cannot be negative or zero" rule
        runner.setProperty(MergeRecord.MIN_RECORDS, "${min_records}");
        runner.setProperty(MergeRecord.MAX_RECORDS, "3");
        runner.assertNotValid();

        // This configuration breaks the "<Minimum Number of Records> property cannot be negative or zero" and the
        // "<Maximum Number of Records> property cannot be negative or zero" rules
        runner.setProperty(MergeRecord.MIN_RECORDS, "${min_records}");
        runner.setProperty(MergeRecord.MAX_RECORDS, "${max_records}");
        runner.assertNotValid();

        // This configuration breaks the "<Maximum Number of Records> property cannot be smaller than <Minimum Number of Records> property"
        // and the "<Maximum Number of Records> property cannot be negative or zero" rules
        runner.setProperty(MergeRecord.MIN_RECORDS, "3");
        runner.setProperty(MergeRecord.MAX_RECORDS, "${max_records}");
        runner.assertNotValid();

        // This configuration breaks the "<Maximum Number of Records> property cannot be smaller than <Minimum Number of Records> property"
        // and the "<Maximum Number of Records> property cannot be negative or zero" rules
        runner.removeProperty(MergeRecord.MIN_RECORDS); // Will use the default value of 1
        runner.setProperty(MergeRecord.MAX_RECORDS, "${max_records}");
        runner.assertNotValid();

        // This configuration breaks the "<Maximum Number of Records> property cannot be smaller than <Minimum Number of Records> property",
        // the "<Minimum Number of Records> property cannot be negative or zero" and the "<Maximum Number of Records>
        // property cannot be negative or zero" rules
        runner.setEnvironmentVariableValue("min_records", "-1");
        runner.setEnvironmentVariableValue("max_records", "-3");
        runner.setProperty(MergeRecord.MIN_RECORDS, "${min_records}");
        runner.setProperty(MergeRecord.MAX_RECORDS, "${max_records}");
        runner.assertNotValid();

        // This configuration is valid
        runner.setEnvironmentVariableValue("min_records", "1");
        runner.setEnvironmentVariableValue("max_records", "5");
        runner.setProperty(MergeRecord.MIN_RECORDS, "${min_records}");
        runner.setProperty(MergeRecord.MAX_RECORDS, "${max_records}");
        runner.assertValid();

        runner.removeProperty("min_records");
        runner.removeProperty("max_records");
    }

}
