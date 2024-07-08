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
package org.apache.nifi.parameter;

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.ConfigVerificationResult;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.dbcp.DBCPService;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.processors.standard.db.DatabaseAdapter;
import org.apache.nifi.util.StringUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

@Tags({"database", "dbcp", "sql"})
@CapabilityDescription("Fetches parameters from database tables")

public class DatabaseParameterProvider extends AbstractParameterProvider implements VerifiableParameterProvider {

    protected final static Map<String, DatabaseAdapter> dbAdapters = new HashMap<>();

    public static final PropertyDescriptor DB_TYPE;

    static {
        // Load the DatabaseAdapters
        ArrayList<AllowableValue> dbAdapterValues = new ArrayList<>();
        ServiceLoader<DatabaseAdapter> dbAdapterLoader = ServiceLoader.load(DatabaseAdapter.class);
        dbAdapterLoader.forEach(it -> {
            dbAdapters.put(it.getName(), it);
            dbAdapterValues.add(new AllowableValue(it.getName(), it.getName(), it.getDescription()));
        });

        DB_TYPE = new PropertyDescriptor.Builder()
                .name("db-type")
                .displayName("Database Type")
                .description("The type/flavor of database, used for generating database-specific code. In many cases the Generic type "
                        + "should suffice, but some databases (such as Oracle) require custom SQL clauses. ")
                .allowableValues(dbAdapterValues.toArray(new AllowableValue[dbAdapterValues.size()]))
                .defaultValue("Generic")
                .required(true)
                .build();
    }

    static AllowableValue GROUPING_BY_COLUMN = new AllowableValue("grouping-by-column", "Column",
            "A single table is partitioned by the 'Parameter Group Name Column'.  All rows with the same value in this column will " +
                    "map to a group of the same name.");
    static AllowableValue GROUPING_BY_TABLE_NAME = new AllowableValue("grouping-by-table-name", "Table Name",
            "An entire table maps to a Parameter Group.  The group name will be the table name.");

    public static final PropertyDescriptor DBCP_SERVICE = new PropertyDescriptor.Builder()
            .name("dbcp-service")
            .displayName("Database Connection Pooling Service")
            .description("The Controller Service that is used to obtain a connection to the database.")
            .required(true)
            .identifiesControllerService(DBCPService.class)
            .build();

    public static final PropertyDescriptor PARAMETER_GROUPING_STRATEGY = new PropertyDescriptor.Builder()
            .name("parameter-grouping-strategy")
            .displayName("Parameter Grouping Strategy")
            .description("The strategy used to group parameters.")
            .required(true)
            .allowableValues(GROUPING_BY_COLUMN, GROUPING_BY_TABLE_NAME)
            .defaultValue(GROUPING_BY_COLUMN.getValue())
            .build();

    public static final PropertyDescriptor TABLE_NAMES = new PropertyDescriptor.Builder()
            .name("table-names")
            .displayName("Table Names")
            .description("A comma-separated list of names of the database tables containing the parameters.")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .required(true)
            .dependsOn(PARAMETER_GROUPING_STRATEGY, GROUPING_BY_TABLE_NAME)
            .build();

    public static final PropertyDescriptor TABLE_NAME = new PropertyDescriptor.Builder()
            .name("table-name")
            .displayName("Table Name")
            .description("The name of the database table containing the parameters.")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .required(true)
            .dependsOn(PARAMETER_GROUPING_STRATEGY, GROUPING_BY_COLUMN)
            .build();

    public static final PropertyDescriptor PARAMETER_NAME_COLUMN = new PropertyDescriptor.Builder()
            .name("parameter-name-column")
            .displayName("Parameter Name Column")
            .description("The name of a column containing the parameter name.")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor PARAMETER_VALUE_COLUMN = new PropertyDescriptor.Builder()
            .name("parameter-value-column")
            .displayName("Parameter Value Column")
            .description("The name of a column containing the parameter value.")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .required(true)
            .build();

    public static final PropertyDescriptor PARAMETER_GROUP_NAME_COLUMN = new PropertyDescriptor.Builder()
            .name("parameter-group-name-column")
            .displayName("Parameter Group Name Column")
            .description("The name of a column containing the name of the parameter group into which the parameter should be mapped.")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .required(true)
            .dependsOn(PARAMETER_GROUPING_STRATEGY, GROUPING_BY_COLUMN)
            .build();

    public static final PropertyDescriptor SQL_WHERE_CLAUSE = new PropertyDescriptor.Builder()
            .name("sql-where-clause")
            .displayName("SQL WHERE clause")
            .description("A optional SQL query 'WHERE' clause by which to filter all results.  The 'WHERE' keyword should not be included.")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    private List<PropertyDescriptor> properties;

    @Override
    protected void init(final ParameterProviderInitializationContext config) {
        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(DB_TYPE);
        properties.add(DBCP_SERVICE);
        properties.add(PARAMETER_GROUPING_STRATEGY);
        properties.add(TABLE_NAME);
        properties.add(TABLE_NAMES);
        properties.add(PARAMETER_NAME_COLUMN);
        properties.add(PARAMETER_VALUE_COLUMN);
        properties.add(PARAMETER_GROUP_NAME_COLUMN);
        properties.add(SQL_WHERE_CLAUSE);

        this.properties = Collections.unmodifiableList(properties);
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return properties;
    }

    @Override
    public List<ParameterGroup> fetchParameters(final ConfigurationContext context) {
        final boolean groupByColumn = GROUPING_BY_COLUMN.getValue().equals(context.getProperty(PARAMETER_GROUPING_STRATEGY).getValue());

        final DBCPService dbcpService = context.getProperty(DBCP_SERVICE).asControllerService(DBCPService.class);
        final String whereClause = context.getProperty(SQL_WHERE_CLAUSE).getValue();
        final String parameterNameColumn = context.getProperty(PARAMETER_NAME_COLUMN).getValue();
        final String parameterValueColumn = context.getProperty(PARAMETER_VALUE_COLUMN).getValue();
        final String parameterGroupNameColumn = context.getProperty(PARAMETER_GROUP_NAME_COLUMN).getValue();

        final List<String> tableNames = groupByColumn
                ? Collections.singletonList(context.getProperty(TABLE_NAME).getValue())
                : Arrays.stream(context.getProperty(TABLE_NAMES).getValue().split(",")).map(String::trim).collect(Collectors.toList());

        final Map<String, List<Parameter>> parameterMap = new HashMap<>();
        for (final String tableName : tableNames) {
            try (final Connection con = dbcpService.getConnection(Collections.emptyMap()); final Statement st = con.createStatement()) {
                final List<String> columns = new ArrayList<>();
                columns.add(parameterNameColumn);
                columns.add(parameterValueColumn);
                if (groupByColumn) {
                    columns.add(parameterGroupNameColumn);
                }
                final String query = getQuery(context, tableName, columns, whereClause);

                getLogger().info("Fetching parameters with query: {}", query);
                try (final ResultSet rs = st.executeQuery(query)) {
                    while (rs.next()) {
                        final String parameterName = rs.getString(parameterNameColumn);
                        final String parameterValue = rs.getString(parameterValueColumn);

                        validateValueNotNull(parameterName, parameterNameColumn);
                        validateValueNotNull(parameterValue, parameterValueColumn);
                        final String parameterGroupName;
                        if (groupByColumn) {
                            parameterGroupName = parameterGroupNameColumn == null ? null : rs.getString(parameterGroupNameColumn);
                            validateValueNotNull(parameterGroupName, parameterGroupNameColumn);
                        } else {
                            parameterGroupName = tableName;
                        }

                        final ParameterDescriptor parameterDescriptor = new ParameterDescriptor.Builder()
                                .name(parameterName)
                                .build();
                        final Parameter parameter = new Parameter(parameterDescriptor, parameterValue);

                        parameterMap.computeIfAbsent(parameterGroupName, key -> new ArrayList<>()).add(parameter);
                    }
                }
            } catch (final SQLException e) {
                getLogger().error("Encountered a database error when fetching parameters: {}", e.getMessage(), e);
                throw new RuntimeException("Encountered a database error when fetching parameters: " + e.getMessage(), e);
            }
        }

        return parameterMap.entrySet().stream()
                .map(entry -> new ParameterGroup(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private void validateValueNotNull(final String value, final String columnName) {
        if (value == null) {
            throw new IllegalStateException(String.format("Expected %s column to be non-null", columnName));
        }
    }

    String getQuery(final ConfigurationContext context, final String tableName, final List<String> columns, final String whereClause) {
        final DatabaseAdapter dbAdapter = dbAdapters.get(context.getProperty(DB_TYPE).getValue());
        return dbAdapter.getSelectStatement(tableName, StringUtils.join(columns, ", "), whereClause, null, null, null);
    }

    @Override
    public List<ConfigVerificationResult> verify(final ConfigurationContext context, final ComponentLog verificationLogger) {
        final List<ConfigVerificationResult> results = new ArrayList<>();
        try {
            final List<ParameterGroup> parameterGroups = fetchParameters(context);
            final long parameterCount = parameterGroups.stream()
                    .flatMap(group -> group.getParameters().stream())
                    .count();
            results.add(new ConfigVerificationResult.Builder()
                    .outcome(ConfigVerificationResult.Outcome.SUCCESSFUL)
                    .verificationStepName("Fetch Parameters")
                    .explanation(String.format("Successfully fetched %s Parameter Groups containing %s Parameters matching the filter.", parameterGroups.size(),
                            parameterCount))
                    .build());
        } catch (final Exception e) {
            verificationLogger.error("Failed to fetch Parameter Groups", e);
            results.add(new ConfigVerificationResult.Builder()
                    .outcome(ConfigVerificationResult.Outcome.FAILED)
                    .verificationStepName("Fetch Parameters")
                    .explanation(String.format("Failed to parameters: " + e.getMessage()))
                    .build());
        }

        return results;
    }
}
