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
package org.apache.nifi.lookup;

import java.util.Collections;
import java.util.Optional;
import org.apache.nifi.csv.CSVUtils;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.serialization.record.Record;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCSVRecordLookupService {


    @Test
    public void testSimpleCsvRecordLookupService() throws InitializationException, LookupFailureException {
        final TestRunner runner = TestRunners.newTestRunner(TestProcessor.class);
        final CSVRecordLookupService service = new CSVRecordLookupService();

        runner.addControllerService("csv-record-lookup-service", service);
        runner.setProperty(service, CSVRecordLookupService.CSV_FILE, "src/test/resources/test.csv");
        runner.setProperty(service, CSVRecordLookupService.CSV_FORMAT, "RFC4180");
        runner.setProperty(service, CSVRecordLookupService.LOOKUP_KEY_COLUMN, "key");
        runner.enableControllerService(service);
        runner.assertValid(service);

        final CSVRecordLookupService lookupService =
                (CSVRecordLookupService) runner.getProcessContext()
                        .getControllerServiceLookup()
                        .getControllerService("csv-record-lookup-service");

        final Optional<Record> property1 = lookupService.lookup(Collections.singletonMap("key", "property.1"));
        assertEquals("this is property 1", property1.get().getAsString("value"));
        assertEquals("2017-04-01", property1.get().getAsString("created_at"));

        final Optional<Record> property2 = lookupService.lookup(Collections.singletonMap("key", "property.2"));
        assertEquals("this is property 2", property2.get().getAsString("value"));
        assertEquals("2017-04-02", property2.get().getAsString("created_at"));

        final Optional<Record> property3 = lookupService.lookup(Collections.singletonMap("key", "property.3"));
        assertTrue(property3.isEmpty());
    }

    @Test
    public void testSimpleCsvRecordLookupServiceWithCharset() throws InitializationException, LookupFailureException {
        final TestRunner runner = TestRunners.newTestRunner(TestProcessor.class);
        final CSVRecordLookupService service = new CSVRecordLookupService();

        runner.addControllerService("csv-record-lookup-service", service);
        runner.setProperty(service, CSVRecordLookupService.CSV_FILE, "src/test/resources/test_Windows-31J.csv");
        runner.setProperty(service, CSVRecordLookupService.CSV_FORMAT, "RFC4180");
        runner.setProperty(service, CSVRecordLookupService.CHARSET, "Windows-31J");
        runner.setProperty(service, CSVRecordLookupService.LOOKUP_KEY_COLUMN, "key");
        runner.enableControllerService(service);
        runner.assertValid(service);

        final Optional<Record> property1 = service.lookup(Collections.singletonMap("key", "property.1"));
        assertTrue(property1.isPresent());
        assertEquals("this is property \uff11", property1.get().getAsString("value"));
        assertEquals("2017-04-01", property1.get().getAsString("created_at"));
    }

    @Test
    public void testCsvRecordLookupServiceWithCustomSeparatorQuotedEscaped() throws InitializationException, LookupFailureException {
        final TestRunner runner = TestRunners.newTestRunner(TestProcessor.class);
        final CSVRecordLookupService service = new CSVRecordLookupService();

        runner.addControllerService("csv-file-lookup-service", service);
        runner.setProperty(service, SimpleCsvFileLookupService.CSV_FORMAT, "custom");
        runner.setProperty(service, SimpleCsvFileLookupService.CSV_FILE, "src/test/resources/test_sep_escape_comment.csv");
        runner.setProperty(service, SimpleCsvFileLookupService.LOOKUP_KEY_COLUMN, "key");
        runner.setProperty(service, SimpleCsvFileLookupService.LOOKUP_VALUE_COLUMN, "value");
        runner.setProperty(service, CSVUtils.VALUE_SEPARATOR, "|");
        runner.setProperty(service, CSVUtils.QUOTE_CHAR, "\"");
        runner.setProperty(service, CSVUtils.ESCAPE_CHAR, "%");
        runner.setProperty(service, CSVUtils.COMMENT_MARKER, "#");
        runner.setProperty(service, CSVUtils.QUOTE_MODE, CSVUtils.QUOTE_ALL);
        runner.enableControllerService(service);
        runner.assertValid(service);

        final Optional<Record> my_key = service.lookup(Collections.singletonMap("key", "my_key"));
        assertTrue(my_key.isPresent());
        assertEquals("my_value with an escaped |.", my_key.get().getAsString("value"));
    }

    @Test
    public void testCacheIsClearedWhenDisableService() throws InitializationException {
        final TestRunner runner = TestRunners.newTestRunner(TestProcessor.class);
        final CSVRecordLookupService service = new CSVRecordLookupService();
        runner.addControllerService("csv-record-lookup-service", service);
        runner.setProperty(service, CSVRecordLookupService.CSV_FILE, "src/test/resources/test.csv");
        runner.setProperty(service, CSVRecordLookupService.CSV_FORMAT, "RFC4180");
        runner.setProperty(service, CSVRecordLookupService.LOOKUP_KEY_COLUMN, "key");
        runner.enableControllerService(service);
        runner.assertValid(service);

        assertTrue(service.isCaching());

        runner.disableControllerService(service);

        assertFalse(service.isCaching());
    }
}
