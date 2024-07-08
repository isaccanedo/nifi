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

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.nifi.distributed.cache.client.AtomicCacheEntry;
import org.apache.nifi.distributed.cache.client.AtomicDistributedMapCacheClient;
import org.apache.nifi.distributed.cache.client.Deserializer;
import org.apache.nifi.distributed.cache.client.DistributedMapCacheClient;
import org.apache.nifi.distributed.cache.client.Serializer;
import org.apache.nifi.distributed.cache.client.exception.DeserializationException;
import org.apache.nifi.distributed.cache.client.exception.SerializationException;
import org.apache.nifi.hadoop.KerberosProperties;
import org.apache.nifi.hbase.scan.ResultCell;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestHBase_2_ClientMapCacheService {

    private KerberosProperties kerberosPropsWithFile;
    private KerberosProperties kerberosPropsWithoutFile;

    private Serializer<String> stringSerializer = new StringSerializer();
    private Deserializer<String> stringDeserializer = new StringDeserializer();

    @BeforeEach
    public void setup() {
        // needed for calls to UserGroupInformation.setConfiguration() to work when passing in
        // config with Kerberos authentication enabled
        System.setProperty("java.security.krb5.realm", "nifi.com");
        System.setProperty("java.security.krb5.kdc", "nifi.kdc");

        kerberosPropsWithFile = new KerberosProperties(new File("src/test/resources/krb5.conf"));

        kerberosPropsWithoutFile = new KerberosProperties(null);
    }

    private final String tableName = "nifi";
    private final String columnFamily = "family1";
    private final String columnQualifier = "qualifier1";


    @Test
    public void testPut() throws InitializationException, IOException {
        final String row = "row1";
        final String content = "content1";

        final TestRunner runner = TestRunners.newTestRunner(TestProcessor.class);

        // Mock an HBase Table so we can verify the put operations later
        final Table table = Mockito.mock(Table.class);
        when(table.getName()).thenReturn(TableName.valueOf(tableName));

        // create the controller service and link it to the test processor
        final MockHBaseClientService service = configureHBaseClientService(runner, table);
        runner.assertValid(service);

        final HBaseClientService hBaseClientService = runner.getProcessContext().getProperty(TestProcessor.HBASE_CLIENT_SERVICE)
            .asControllerService(HBaseClientService.class);

        final DistributedMapCacheClient cacheService = configureHBaseCacheService(runner, hBaseClientService);
        runner.assertValid(cacheService);

        // try to put a single cell
        final DistributedMapCacheClient hBaseCacheService = runner.getProcessContext().getProperty(TestProcessor.HBASE_CACHE_SERVICE)
                .asControllerService(DistributedMapCacheClient.class);

        hBaseCacheService.put( row, content, stringSerializer, stringSerializer);

        // verify only one call to put was made
        ArgumentCaptor<Put> capture = ArgumentCaptor.forClass(Put.class);
        verify(table, times(1)).put(capture.capture());

        verifyPut(row, columnFamily, columnQualifier, content, capture.getValue());
    }

    @Test
    public void testPutAll() throws InitializationException, IOException {
        final TestRunner runner = TestRunners.newTestRunner(TestProcessor.class);

        // Mock an HBase Table so we can verify the put operations later
        final Table table = Mockito.mock(Table.class);
        when(table.getName()).thenReturn(TableName.valueOf(tableName));

        // create the controller service and link it to the test processor
        final MockHBaseClientService service = configureHBaseClientService(runner, table);
        runner.assertValid(service);

        final HBaseClientService hBaseClientService = runner.getProcessContext().getProperty(TestProcessor.HBASE_CLIENT_SERVICE)
                .asControllerService(HBaseClientService.class);

        final DistributedMapCacheClient cacheService = configureHBaseCacheService(runner, hBaseClientService);
        runner.assertValid(cacheService);

        // try to put a single cell
        final DistributedMapCacheClient hBaseCacheService = runner.getProcessContext().getProperty(TestProcessor.HBASE_CACHE_SERVICE)
                .asControllerService(DistributedMapCacheClient.class);

        Map<String, String> putz = new HashMap<>();
        List<String> content = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        for (int x = 1; x <= 5; x++) {
            putz.put(String.format("row-%d", x), String.format("content-%d", x));
            content.add(String.format("content-%d", x));
            rows.add(String.format("row-%d", x));
        }

        hBaseCacheService.putAll( putz, stringSerializer, stringSerializer);

        // verify only one call to put was made
        ArgumentCaptor<List> capture = ArgumentCaptor.forClass(List.class);
        verify(table, times(1)).put(capture.capture());

        List<Put> captured = capture.getValue();


        for (int x = 0; x < 5; x++) {
            Put put = captured.get(x);

            String row = new String(put.getRow());
            assertTrue(rows.contains(row));

            NavigableMap<byte[], List<Cell>> familyCells = put.getFamilyCellMap();
            assertEquals(1, familyCells.size());

            Map.Entry<byte[], List<Cell>> entry = familyCells.firstEntry();
            assertEquals(columnFamily, new String(entry.getKey()));
            assertEquals(1, entry.getValue().size());

            Cell cell = entry.getValue().get(0);
            String contentString = new String(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength());
            assertEquals(columnQualifier, new String(cell.getQualifierArray(), cell.getQualifierOffset(), cell.getQualifierLength()));
            assertTrue(content.contains(contentString));

            content.remove(contentString);
            rows.remove(row);
        }
    }

    @Test
    public void testGet() throws InitializationException, IOException {
        final String row = "row1";
        final String content = "content1";

        final TestRunner runner = TestRunners.newTestRunner(TestProcessor.class);

        // Mock an HBase Table so we can verify the put operations later
        final Table table = Mockito.mock(Table.class);
        when(table.getName()).thenReturn(TableName.valueOf(tableName));

        // create the controller service and link it to the test processor
        final MockHBaseClientService service = configureHBaseClientService(runner, table);
        runner.assertValid(service);

        final HBaseClientService hBaseClientService = runner.getProcessContext().getProperty(TestProcessor.HBASE_CLIENT_SERVICE)
            .asControllerService(HBaseClientService.class);

        final DistributedMapCacheClient cacheService = configureHBaseCacheService(runner, hBaseClientService);
        runner.assertValid(cacheService);

        final DistributedMapCacheClient hBaseCacheService = runner.getProcessContext().getProperty(TestProcessor.HBASE_CACHE_SERVICE)
                .asControllerService(DistributedMapCacheClient.class);

        hBaseCacheService.put(row, content, stringSerializer, stringSerializer);

        final String result = hBaseCacheService.get(row, stringSerializer, stringDeserializer);

        assertEquals( content, result);

    }

    @Test
    public void testContainsKey() throws InitializationException, IOException {
        final String row = "row1";
        final String content = "content1";

        final TestRunner runner = TestRunners.newTestRunner(TestProcessor.class);

        // Mock an HBase Table so we can verify the put operations later
        final Table table = Mockito.mock(Table.class);
        when(table.getName()).thenReturn(TableName.valueOf(tableName));

        // create the controller service and link it to the test processor
        final MockHBaseClientService service = configureHBaseClientService(runner, table);
        runner.assertValid(service);

        final HBaseClientService hBaseClientService = runner.getProcessContext().getProperty(TestProcessor.HBASE_CLIENT_SERVICE)
            .asControllerService(HBaseClientService.class);

        final DistributedMapCacheClient cacheService = configureHBaseCacheService(runner, hBaseClientService);
        runner.assertValid(cacheService);

        final DistributedMapCacheClient hBaseCacheService = runner.getProcessContext().getProperty(TestProcessor.HBASE_CACHE_SERVICE)
                .asControllerService(DistributedMapCacheClient.class);

        assertFalse(hBaseCacheService.containsKey(row, stringSerializer));

        hBaseCacheService.put(row, content, stringSerializer, stringSerializer);

        assertTrue(hBaseCacheService.containsKey(row, stringSerializer));
    }

    @Test
    public void testPutIfAbsent() throws InitializationException, IOException {
        final String row = "row1";
        final String content = "content1";

        final TestRunner runner = TestRunners.newTestRunner(TestProcessor.class);

        // Mock an HBase Table so we can verify the put operations later
        final Table table = Mockito.mock(Table.class);
        when(table.getName()).thenReturn(TableName.valueOf(tableName));

        // create the controller service and link it to the test processor
        final MockHBaseClientService service = configureHBaseClientService(runner, table);
        runner.assertValid(service);

        final HBaseClientService hBaseClientService = runner.getProcessContext().getProperty(TestProcessor.HBASE_CLIENT_SERVICE)
            .asControllerService(HBaseClientService.class);

        final DistributedMapCacheClient cacheService = configureHBaseCacheService(runner, hBaseClientService);
        runner.assertValid(cacheService);

        final DistributedMapCacheClient hBaseCacheService = runner.getProcessContext().getProperty(TestProcessor.HBASE_CACHE_SERVICE)
                .asControllerService(DistributedMapCacheClient.class);

        assertTrue( hBaseCacheService.putIfAbsent( row, content, stringSerializer, stringSerializer));

        // verify only one call to put was made
        ArgumentCaptor<Put> capture = ArgumentCaptor.forClass(Put.class);
        verify(table, times(1)).put(capture.capture());

        verifyPut(row, columnFamily, columnQualifier, content, capture.getValue());

        assertFalse(hBaseCacheService.putIfAbsent(row, content, stringSerializer, stringSerializer));

        verify(table, times(1)).put(capture.capture());
    }

    @Test
    public void testGetAndPutIfAbsent() throws InitializationException, IOException {
        final String row = "row1";
        final String content = "content1";

        final TestRunner runner = TestRunners.newTestRunner(TestProcessor.class);

        // Mock an HBase Table so we can verify the put operations later
        final Table table = Mockito.mock(Table.class);
        when(table.getName()).thenReturn(TableName.valueOf(tableName));

        // create the controller service and link it to the test processor
        final MockHBaseClientService service = configureHBaseClientService(runner, table);
        runner.assertValid(service);

        final HBaseClientService hBaseClientService = runner.getProcessContext().getProperty(TestProcessor.HBASE_CLIENT_SERVICE)
            .asControllerService(HBaseClientService.class);

        final DistributedMapCacheClient cacheService = configureHBaseCacheService(runner, hBaseClientService);
        runner.assertValid(cacheService);

        final DistributedMapCacheClient hBaseCacheService = runner.getProcessContext().getProperty(TestProcessor.HBASE_CACHE_SERVICE)
                .asControllerService(DistributedMapCacheClient.class);

        assertNull( hBaseCacheService.getAndPutIfAbsent( row, content, stringSerializer, stringSerializer, stringDeserializer));

        // verify only one call to put was made
        ArgumentCaptor<Put> capture = ArgumentCaptor.forClass(Put.class);
        verify(table, times(1)).put(capture.capture());

        verifyPut(row, columnFamily, columnQualifier, content, capture.getValue());

        final String result = hBaseCacheService.getAndPutIfAbsent(row, content, stringSerializer, stringSerializer, stringDeserializer);

        verify(table, times(1)).put(capture.capture());

        assertEquals(result, content);
    }

    @Test
    public void testFetch() throws InitializationException, IOException {
        final String key = "key1";
        final String value = "value1";
        final byte[] revision = value.getBytes();

        final TestRunner runner = TestRunners.newTestRunner(TestProcessor.class);

        // Mock an HBase Table so we can verify the put operations later
        final Table table = Mockito.mock(Table.class);
        when(table.getName()).thenReturn(TableName.valueOf(tableName));

        // create the controller service and link it to the test processor
        final MockHBaseClientService service = configureHBaseClientService(runner, table);
        runner.assertValid(service);

        final HBaseClientService hBaseClientService = runner.getProcessContext().getProperty(TestProcessor.HBASE_CLIENT_SERVICE)
                .asControllerService(HBaseClientService.class);

        final AtomicDistributedMapCacheClient<byte[]> cacheService = configureHBaseCacheService(runner, hBaseClientService);
        runner.assertValid(cacheService);

        final AtomicDistributedMapCacheClient<byte[]> hBaseCacheService = runner.getProcessContext().getProperty(TestProcessor.HBASE_CACHE_SERVICE)
                .asControllerService(AtomicDistributedMapCacheClient.class);

        hBaseCacheService.put(key, value, stringSerializer, stringSerializer);

        final AtomicCacheEntry<String, String, byte[]> atomicCacheEntry = hBaseCacheService.fetch(key, stringSerializer, stringDeserializer);

        assertEquals(key, atomicCacheEntry.getKey());
        assertEquals(value, atomicCacheEntry.getValue());
        assertArrayEquals(revision, atomicCacheEntry.getRevision().get());
    }

    @Test
    public void testReplace() throws InitializationException, IOException {
        final String key = "key1";
        final String value = "value1";
        final byte[] revision = value.getBytes();

        final TestRunner runner = TestRunners.newTestRunner(TestProcessor.class);

        // Mock an HBase Table so we can verify the put operations later
        final Table table = Mockito.mock(Table.class);
        when(table.getName()).thenReturn(TableName.valueOf(tableName));

        // create the controller service and link it to the test processor
        final MockHBaseClientService service = configureHBaseClientService(runner, table);
        runner.assertValid(service);

        final HBaseClientService hBaseClientService = runner.getProcessContext().getProperty(TestProcessor.HBASE_CLIENT_SERVICE)
                .asControllerService(HBaseClientService.class);

        final AtomicDistributedMapCacheClient<byte[]> cacheService = configureHBaseCacheService(runner, hBaseClientService);
        runner.assertValid(cacheService);

        final AtomicDistributedMapCacheClient<byte[]> hBaseCacheService = runner.getProcessContext().getProperty(TestProcessor.HBASE_CACHE_SERVICE)
                .asControllerService(AtomicDistributedMapCacheClient.class);

        // First time value should not already be in cache so this should return true
        final boolean newResult = hBaseCacheService.replace(new AtomicCacheEntry(key, value, null), stringSerializer, stringSerializer);
        assertTrue(newResult);

        // Second time value is already in cache so this should return false
        final boolean existingResult = hBaseCacheService.replace(new AtomicCacheEntry(key, value, revision), stringSerializer, stringSerializer);
        assertTrue(existingResult);

        // Third time we're replacing with a new value so this should return true
        final boolean replaceResult = hBaseCacheService.replace(new AtomicCacheEntry(key, "value2", revision), stringSerializer, stringSerializer);
        assertTrue(replaceResult);
    }


    private MockHBaseClientService configureHBaseClientService(final TestRunner runner, final Table table) throws InitializationException {
        final MockHBaseClientService service = new MockHBaseClientService(table, "family1", kerberosPropsWithFile);
        runner.addControllerService("hbaseClient", service);
        runner.setProperty(service, HBase_2_ClientService.HADOOP_CONF_FILES, "src/test/resources/hbase-site.xml");
        runner.enableControllerService(service);
        runner.setProperty(TestProcessor.HBASE_CLIENT_SERVICE, "hbaseClient");
        return service;
    }

    private AtomicDistributedMapCacheClient<byte[]> configureHBaseCacheService(final TestRunner runner, final HBaseClientService service) throws InitializationException {
        final HBase_2_ClientMapCacheService cacheService = new HBase_2_ClientMapCacheService();
        runner.addControllerService("hbaseCache", cacheService);
        runner.setProperty(cacheService, HBase_2_ClientMapCacheService.HBASE_CLIENT_SERVICE, "hbaseClient");
        runner.setProperty(cacheService, HBase_2_ClientMapCacheService.HBASE_CACHE_TABLE_NAME, tableName);
        runner.setProperty(cacheService, HBase_2_ClientMapCacheService.HBASE_COLUMN_FAMILY, columnFamily);
        runner.setProperty(cacheService, HBase_2_ClientMapCacheService.HBASE_COLUMN_QUALIFIER, columnQualifier);
        runner.enableControllerService(cacheService);
        runner.setProperty(TestProcessor.HBASE_CACHE_SERVICE, "hbaseCache");
        return cacheService;
    }

    private void verifyResultCell(final ResultCell result, final String cf, final String cq, final String val) {
        final String colFamily = new String(result.getFamilyArray(), result.getFamilyOffset(), result.getFamilyLength());
        assertEquals(cf, colFamily);

        final String colQualifier = new String(result.getQualifierArray(), result.getQualifierOffset(), result.getQualifierLength());
        assertEquals(cq, colQualifier);

        final String value = new String(result.getValueArray(), result.getValueOffset(), result.getValueLength());
        assertEquals(val, value);
    }

    private void verifyPut(String row, String columnFamily, String columnQualifier, String content, Put put) {
        assertEquals(row, new String(put.getRow()));

        NavigableMap<byte[], List<Cell>> familyCells = put.getFamilyCellMap();
        assertEquals(1, familyCells.size());

        Map.Entry<byte[], List<Cell>> entry = familyCells.firstEntry();
        assertEquals(columnFamily, new String(entry.getKey()));
        assertEquals(1, entry.getValue().size());

        Cell cell = entry.getValue().get(0);
        assertEquals(columnQualifier, new String(cell.getQualifierArray(), cell.getQualifierOffset(), cell.getQualifierLength()));
        assertEquals(content, new String(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength()));
    }

    private static class StringSerializer implements Serializer<String> {
        @Override
        public void serialize(final String value, final OutputStream out) throws SerializationException, IOException {
            out.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static class StringDeserializer implements Deserializer<String> {
        @Override
        public String deserialize(byte[] input) throws DeserializationException, IOException {
            return new String(input);
        }
    }
}
