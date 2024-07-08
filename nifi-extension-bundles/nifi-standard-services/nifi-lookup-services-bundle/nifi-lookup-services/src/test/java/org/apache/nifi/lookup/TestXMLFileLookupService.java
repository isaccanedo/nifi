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

import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestXMLFileLookupService {

    final static Optional<String> EMPTY_STRING = Optional.empty();

    @Test
    public void testXMLFileLookupService() throws InitializationException, LookupFailureException {
        final TestRunner runner = TestRunners.newTestRunner(TestProcessor.class);
        final XMLFileLookupService service = new XMLFileLookupService();

        runner.addControllerService("xml-file-lookup-service", service);
        runner.setProperty(service, XMLFileLookupService.CONFIGURATION_FILE, "src/test/resources/test.xml");
        runner.enableControllerService(service);
        runner.assertValid(service);

        final XMLFileLookupService lookupService =
            (XMLFileLookupService) runner.getProcessContext()
                .getControllerServiceLookup()
                .getControllerService("xml-file-lookup-service");

        final Optional<String> property1 = lookupService.lookup(Collections.singletonMap("key", "properties.property(0)"));
        assertEquals(Optional.of("this is property 1"), property1);

        final Optional<String> property2 = lookupService.lookup(Collections.singletonMap("key", "properties.property(1)"));
        assertEquals(Optional.of("this is property 2"), property2);

        final Optional<String> property3 = lookupService.lookup(Collections.singletonMap("key", "properties.property(2)[@value]"));
        assertEquals(Optional.of("this is property 3"), property3);

        final Optional<String> property4 = lookupService.lookup(Collections.singletonMap("key", "properties.property(3)"));
        assertEquals(EMPTY_STRING, property4);
    }

    @Test
    public void testXXEProtection() throws InitializationException {

        // Arrange
        final TestRunner runner = TestRunners.newTestRunner(TestProcessor.class);
        final XMLFileLookupService service = new XMLFileLookupService();
        runner.addControllerService("xml-file-lookup-service", service);
        runner.setProperty(service, XMLFileLookupService.CONFIGURATION_FILE, "src/test/resources/test-xxe.xml");

        try {
            // Act
            // Service will fail to enable because test-xxe.xml contains a DTD
            runner.enableControllerService(service);

        } catch (final Throwable e) {
            // Assert
            assertTrue(e.getMessage().contains("contained an external entity. To prevent XXE vulnerabilities, NiFi has external entity processing disabled."));
        }
    }
}
