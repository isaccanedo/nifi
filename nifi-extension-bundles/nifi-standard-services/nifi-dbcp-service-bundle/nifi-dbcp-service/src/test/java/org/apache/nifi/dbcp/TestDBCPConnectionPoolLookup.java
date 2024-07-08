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
package org.apache.nifi.dbcp;

import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.processor.FlowFileFilter;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.NoOpProcessor;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestDBCPConnectionPoolLookup {

    private MockConnection connectionA;
    private MockConnection connectionB;

    private MockDBCPService dbcpServiceA;
    private MockDBCPService dbcpServiceB;

    private DBCPService dbcpLookupService;
    private TestRunner runner;

    @BeforeEach
    public void setup() throws InitializationException {
        connectionA = mock(MockConnection.class);
        when(connectionA.getName()).thenReturn("A");

        connectionB = mock(MockConnection.class);
        when(connectionB.getName()).thenReturn("B");

        dbcpServiceA = new MockDBCPService(connectionA);
        dbcpServiceB = new MockDBCPService(connectionB);

        dbcpLookupService = new DBCPConnectionPoolLookup();

        runner = TestRunners.newTestRunner(NoOpProcessor.class);

        final String dbcpServiceAIdentifier = "dbcp-a";
        runner.addControllerService(dbcpServiceAIdentifier, dbcpServiceA);

        final String dbcpServiceBIdentifier = "dbcp-b";
        runner.addControllerService(dbcpServiceBIdentifier, dbcpServiceB);

        runner.addControllerService("dbcp-lookup", dbcpLookupService);
        runner.setProperty(dbcpLookupService, "a", dbcpServiceAIdentifier);
        runner.setProperty(dbcpLookupService, "b", dbcpServiceBIdentifier);

        runner.enableControllerService(dbcpServiceA);
        runner.enableControllerService(dbcpServiceB);
        runner.enableControllerService(dbcpLookupService);

    }

    @Test
    public void testLookupServiceA() {
        final Map<String, String> attributes = new HashMap<>();
        attributes.put(DBCPConnectionPoolLookup.DATABASE_NAME_ATTRIBUTE, "a");

        final Connection connection = dbcpLookupService.getConnection(attributes);
        assertNotNull(connection);
        assertTrue(connection instanceof MockConnection);

        final MockConnection mockConnection = (MockConnection) connection;
        assertEquals(connectionA.getName(), mockConnection.getName());
    }

    @Test
    public void testLookupServiceB() {
        final Map<String, String> attributes = new HashMap<>();
        attributes.put(DBCPConnectionPoolLookup.DATABASE_NAME_ATTRIBUTE, "b");

        final Connection connection = dbcpLookupService.getConnection(attributes);
        assertNotNull(connection);
        assertTrue(connection instanceof MockConnection);

        final MockConnection mockConnection = (MockConnection) connection;
        assertEquals(connectionB.getName(), mockConnection.getName());
    }

    @Test
    public void testLookupWithoutAttributes() {
        assertThrows(UnsupportedOperationException.class, () -> dbcpLookupService.getConnection());
    }

    @Test
    public void testLookupMissingDatabaseNameAttribute() {
        final Map<String, String> attributes = new HashMap<>();
        assertThrows(ProcessException.class, () -> dbcpLookupService.getConnection(attributes));
    }

    @Test
    public void testLookupWithDatabaseNameThatDoesNotExist() {
        final Map<String, String> attributes = new HashMap<>();
        attributes.put(DBCPConnectionPoolLookup.DATABASE_NAME_ATTRIBUTE, "DOES-NOT-EXIST");
        assertThrows(ProcessException.class, () -> dbcpLookupService.getConnection(attributes));
    }

    @Test
    public void testCustomValidateAtLeaseOneServiceDefined() throws InitializationException {
        // enable lookup service with no services registered, verify not valid
        runner = TestRunners.newTestRunner(NoOpProcessor.class);
        runner.addControllerService("dbcp-lookup", dbcpLookupService);
        runner.assertNotValid(dbcpLookupService);

        final String dbcpServiceAIdentifier = "dbcp-a";
        runner.addControllerService(dbcpServiceAIdentifier, dbcpServiceA);

        // register a service and now verify valid
        runner.setProperty(dbcpLookupService, "a", dbcpServiceAIdentifier);
        runner.enableControllerService(dbcpLookupService);
        runner.assertValid(dbcpLookupService);
    }

    @Test
    public void testCustomValidateSelfReferenceNotAllowed() throws InitializationException {
        runner = TestRunners.newTestRunner(NoOpProcessor.class);
        runner.addControllerService("dbcp-lookup", dbcpLookupService);
        runner.setProperty(dbcpLookupService, "dbcp-lookup", "dbcp-lookup");
        runner.assertNotValid(dbcpLookupService);
    }

    @Test
    public void testFlowFileFiltering() {
        final FlowFileFilter filter = dbcpLookupService.getFlowFileFilter();
        assertNotNull(filter);

        final MockFlowFile ff0 = new MockFlowFile(0);
        final MockFlowFile ff1 = new MockFlowFile(1);
        final MockFlowFile ff2 = new MockFlowFile(2);
        final MockFlowFile ff3 = new MockFlowFile(3);
        final MockFlowFile ff4 = new MockFlowFile(4);

        ff0.putAttributes(Collections.singletonMap(DBCPConnectionPoolLookup.DATABASE_NAME_ATTRIBUTE, "db-A"));
        ff1.putAttributes(Collections.singletonMap(DBCPConnectionPoolLookup.DATABASE_NAME_ATTRIBUTE, "db-B"));
        ff2.putAttributes(Collections.singletonMap(DBCPConnectionPoolLookup.DATABASE_NAME_ATTRIBUTE, "db-A"));
        ff3.putAttributes(Collections.singletonMap(DBCPConnectionPoolLookup.DATABASE_NAME_ATTRIBUTE, "db-B"));
        ff4.putAttributes(Collections.singletonMap(DBCPConnectionPoolLookup.DATABASE_NAME_ATTRIBUTE, "db-A"));

        assertEquals(FlowFileFilter.FlowFileFilterResult.ACCEPT_AND_CONTINUE, filter.filter(ff0));
        assertEquals(FlowFileFilter.FlowFileFilterResult.REJECT_AND_CONTINUE, filter.filter(ff1));
        assertEquals(FlowFileFilter.FlowFileFilterResult.ACCEPT_AND_CONTINUE, filter.filter(ff2));
        assertEquals(FlowFileFilter.FlowFileFilterResult.REJECT_AND_CONTINUE, filter.filter(ff3));
        assertEquals(FlowFileFilter.FlowFileFilterResult.ACCEPT_AND_CONTINUE, filter.filter(ff4));
    }

    @Test
    public void testFlowFileFilteringWithBatchSize() {
        final FlowFileFilter filter = dbcpLookupService.getFlowFileFilter(2);
        assertNotNull(filter);

        final MockFlowFile ff0 = new MockFlowFile(0);
        final MockFlowFile ff1 = new MockFlowFile(1);
        final MockFlowFile ff2 = new MockFlowFile(2);
        final MockFlowFile ff3 = new MockFlowFile(3);
        final MockFlowFile ff4 = new MockFlowFile(4);

        ff0.putAttributes(Collections.singletonMap(DBCPConnectionPoolLookup.DATABASE_NAME_ATTRIBUTE, "db-A"));
        ff1.putAttributes(Collections.singletonMap(DBCPConnectionPoolLookup.DATABASE_NAME_ATTRIBUTE, "db-B"));
        ff2.putAttributes(Collections.singletonMap(DBCPConnectionPoolLookup.DATABASE_NAME_ATTRIBUTE, "db-A"));
        ff3.putAttributes(Collections.singletonMap(DBCPConnectionPoolLookup.DATABASE_NAME_ATTRIBUTE, "db-B"));
        ff4.putAttributes(Collections.singletonMap(DBCPConnectionPoolLookup.DATABASE_NAME_ATTRIBUTE, "db-A"));

        assertEquals(FlowFileFilter.FlowFileFilterResult.ACCEPT_AND_CONTINUE, filter.filter(ff0));
        assertEquals(FlowFileFilter.FlowFileFilterResult.REJECT_AND_CONTINUE, filter.filter(ff1));
        assertEquals(FlowFileFilter.FlowFileFilterResult.ACCEPT_AND_CONTINUE, filter.filter(ff2));
        assertEquals(FlowFileFilter.FlowFileFilterResult.REJECT_AND_TERMINATE, filter.filter(ff3));
        assertEquals(FlowFileFilter.FlowFileFilterResult.REJECT_AND_TERMINATE, filter.filter(ff4));
    }

    /**
     * A mock DBCPService that will always return the passed in MockConnection.
     */
    private static class MockDBCPService extends AbstractControllerService implements DBCPService {

        private final MockConnection connection;

        public MockDBCPService(MockConnection connection) {
            this.connection = connection;
        }

        @Override
        public Connection getConnection() throws ProcessException {
            return connection;
        }

        @Override
        public Connection getConnection(Map<String, String> attributes) throws ProcessException {
            return connection;
        }
    }

    /**
     * A mock Connection that will allow us to mock getName so we can identify we have the expected Connection.
     */
    private interface MockConnection extends Connection {

        String getName();

    }
}
