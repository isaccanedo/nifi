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
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.hadoop.KerberosProperties;
import org.apache.nifi.hbase.put.PutColumn;
import org.apache.nifi.hbase.put.PutFlowFile;
import org.apache.nifi.hbase.scan.Column;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Override methods to create a mock service that can return staged data
 */
public class MockHBaseClientService extends HBase_2_ClientService {

    private Table table;
    private String family;
    private Map<String, Result> results = new HashMap<>();
    private KerberosProperties kerberosProperties;
    private boolean allowExplicitKeytab;
    private UserGroupInformation mockUgi;

    {
        mockUgi = mock(UserGroupInformation.class);
        try {
            doAnswer(invocation -> {
                PrivilegedExceptionAction<?> action = invocation.getArgument(0);
                return action.run();
            }).when(mockUgi).doAs(any(PrivilegedExceptionAction.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public MockHBaseClientService(final Table table, final String family, final KerberosProperties kerberosProperties) {
        this(table, family, kerberosProperties, false);
    }

    public MockHBaseClientService(final Table table, final String family, final KerberosProperties kerberosProperties, boolean allowExplicitKeytab) {
        this.table = table;
        this.family = family;
        this.kerberosProperties = kerberosProperties;
        this.allowExplicitKeytab = allowExplicitKeytab;
    }

    @Override
    protected KerberosProperties getKerberosProperties(File kerberosConfigFile) {
        return kerberosProperties;
    }

    protected void setKerberosProperties(KerberosProperties properties) {
        this.kerberosProperties = properties;

    }

    public void addResult(final String rowKey, final Map<String, String> cells, final long timestamp) {
        final byte[] rowArray = rowKey.getBytes(StandardCharsets.UTF_8);
        final Cell[] cellArray = new Cell[cells.size()];
        int i = 0;
        for (final Map.Entry<String, String> cellEntry : cells.entrySet()) {
            final Cell cell = mock(Cell.class);
            when(cell.getRowArray()).thenReturn(rowArray);
            when(cell.getRowOffset()).thenReturn(0);
            when(cell.getRowLength()).thenReturn((short) rowArray.length);

            final String cellValue = cellEntry.getValue();
            final byte[] valueArray = cellValue.getBytes(StandardCharsets.UTF_8);
            when(cell.getValueArray()).thenReturn(valueArray);
            when(cell.getValueOffset()).thenReturn(0);
            when(cell.getValueLength()).thenReturn(valueArray.length);

            final byte[] familyArray = family.getBytes(StandardCharsets.UTF_8);
            when(cell.getFamilyArray()).thenReturn(familyArray);
            when(cell.getFamilyOffset()).thenReturn(0);
            when(cell.getFamilyLength()).thenReturn((byte) familyArray.length);

            final String qualifier = cellEntry.getKey();
            final byte[] qualifierArray = qualifier.getBytes(StandardCharsets.UTF_8);
            when(cell.getQualifierArray()).thenReturn(qualifierArray);
            when(cell.getQualifierOffset()).thenReturn(0);
            when(cell.getQualifierLength()).thenReturn(qualifierArray.length);

            when(cell.getTimestamp()).thenReturn(timestamp);

            cellArray[i++] = cell;
        }

        final Result result = mock(Result.class);
        when(result.getRow()).thenReturn(rowArray);
        when(result.rawCells()).thenReturn(cellArray);
        results.put(rowKey, result);
    }

    @Override
    public void put(final String tableName, final byte[] rowId, final Collection<PutColumn> columns) throws IOException {
        Put put = new Put(rowId);
        Map<String, String> map = new HashMap<String, String>();
        for (final PutColumn column : columns) {
            put.addColumn(
                    column.getColumnFamily(),
                    column.getColumnQualifier(),
                    column.getBuffer());
            map.put(new String(column.getColumnQualifier()), new String(column.getBuffer()));
        }

        table.put(put);
        addResult(new String(rowId), map, 1);
    }

    @Override
    public void put(final String tableName, final Collection<PutFlowFile> puts) throws IOException {
        final Map<String, List<PutColumn>> sorted = new HashMap<>();
        final List<Put> newPuts = new ArrayList<>();

        for (final PutFlowFile putFlowFile : puts) {
            Map<String, String> map = new HashMap<String, String>();
            final String rowKeyString = new String(putFlowFile.getRow(), StandardCharsets.UTF_8);
            List<PutColumn> columns = sorted.get(rowKeyString);
            if (columns == null) {
                columns = new ArrayList<>();
                sorted.put(rowKeyString, columns);
            }

            columns.addAll(putFlowFile.getColumns());
            for (PutColumn column : putFlowFile.getColumns()) {
                map.put(new String(column.getColumnQualifier()), new String(column.getBuffer()));
            }

            addResult(new String(putFlowFile.getRow()), map, 1);
        }

        for (final Map.Entry<String, List<PutColumn>> entry : sorted.entrySet()) {
            newPuts.addAll(buildPuts(entry.getKey().getBytes(StandardCharsets.UTF_8), entry.getValue()));
        }

        table.put(newPuts);
    }

    @Override
    public boolean checkAndPut(final String tableName, final byte[] rowId, final byte[] family, final byte[] qualifier, final byte[] value, final PutColumn column) throws IOException {
        for (Result result : results.values()) {
            if (Arrays.equals(result.getRow(), rowId)) {
                Cell[] cellArray = result.rawCells();
                for (Cell cell : cellArray) {
                    if (Arrays.equals(cell.getFamilyArray(), family) && Arrays.equals(cell.getQualifierArray(), qualifier)) {
                         if (value == null || !Arrays.equals(cell.getValueArray(), value)) {
                             return false;
                         }
                    }
                }
            }
        }

        final List<PutColumn> putColumns = new ArrayList<PutColumn>();
        putColumns.add(column);
        put(tableName, rowId, putColumns);
        return true;
    }

    @Override
    protected ResultScanner getResults(Table table, byte[] startRow, byte[] endRow, Collection<Column> columns, List<String> labels) throws IOException {
        final ResultScanner scanner = mock(ResultScanner.class);
        Mockito.when(scanner.iterator()).thenReturn(results.values().iterator());
        return scanner;
    }

    @Override
    protected ResultScanner getResults(Table table, Collection<Column> columns, Filter filter, long minTime, List<String> labels) throws IOException {
        final ResultScanner scanner = mock(ResultScanner.class);
        Mockito.when(scanner.iterator()).thenReturn(results.values().iterator());
        return scanner;
    }

    protected ResultScanner getResults(final Table table, final String startRow, final String endRow, final String filterExpression, final Long timerangeMin, final Long timerangeMax,
            final Integer limitRows, final Boolean isReversed, final Collection<Column> columns)  throws IOException {
        final ResultScanner scanner = mock(ResultScanner.class);
        Mockito.when(scanner.iterator()).thenReturn(results.values().iterator());
        return scanner;
    }

    @Override
    protected Connection createConnection(ConfigurationContext context) throws IOException {
        Connection connection = mock(Connection.class);
        Mockito.when(connection.getTable(table.getName())).thenReturn(table);
        return connection;
    }

    @Override
    boolean isAllowExplicitKeytab() {
        return allowExplicitKeytab;
    }

    @Override
    UserGroupInformation getUgi() throws IOException {
        return mockUgi;
    }
}
