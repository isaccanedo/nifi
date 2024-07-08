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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.ClusterMetrics;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.CheckAndMutate;
import org.apache.hadoop.hbase.client.CheckAndMutateResult;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.exceptions.DeserializationException;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.ParseFilter;
import org.apache.hadoop.hbase.security.visibility.Authorizations;
import org.apache.hadoop.hbase.security.visibility.CellVisibility;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.nifi.annotation.behavior.DynamicProperty;
import org.apache.nifi.annotation.behavior.RequiresInstanceClassLoading;
import org.apache.nifi.annotation.behavior.Restricted;
import org.apache.nifi.annotation.behavior.Restriction;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnDisabled;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.RequiredPermission;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.components.resource.ResourceCardinality;
import org.apache.nifi.components.resource.ResourceType;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.controller.ControllerServiceInitializationContext;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.hadoop.KerberosProperties;
import org.apache.nifi.hadoop.SecurityUtil;
import org.apache.nifi.hbase.put.PutColumn;
import org.apache.nifi.hbase.put.PutFlowFile;
import org.apache.nifi.hbase.scan.Column;
import org.apache.nifi.hbase.scan.HBaseRegion;
import org.apache.nifi.hbase.scan.ResultCell;
import org.apache.nifi.hbase.scan.ResultHandler;
import org.apache.nifi.kerberos.KerberosCredentialsService;
import org.apache.nifi.kerberos.KerberosUserService;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.security.krb.KerberosKeytabUser;
import org.apache.nifi.security.krb.KerberosPasswordUser;
import org.apache.nifi.security.krb.KerberosUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiresInstanceClassLoading
@Tags({ "hbase", "client"})
@CapabilityDescription("Implementation of HBaseClientService using the HBase 2.1.1 client. This service can be configured " +
        "by providing a comma-separated list of configuration files, or by specifying values for the other properties. If configuration files " +
        "are provided, they will be loaded first, and the values of the additional properties will override the values from " +
        "the configuration files. In addition, any user defined properties on the processor will also be passed to the HBase " +
        "configuration.")
@DynamicProperty(name = "The name of an HBase configuration property.", value = "The value of the given HBase configuration property.",
        description = "These properties will be set on the HBase configuration after loading any provided configuration files.")
@Restricted(
        restrictions = {
                @Restriction(
                        requiredPermission = RequiredPermission.REFERENCE_REMOTE_RESOURCES,
                        explanation = "Client JAR Location can reference resources over HTTP"
                )
        }
)
public class HBase_2_ClientService extends AbstractControllerService implements HBaseClientService {
    private static final String ALLOW_EXPLICIT_KEYTAB = "NIFI_ALLOW_EXPLICIT_KEYTAB";

    private static final Logger logger = LoggerFactory.getLogger(HBase_2_ClientService.class);

    static final PropertyDescriptor KERBEROS_CREDENTIALS_SERVICE = new PropertyDescriptor.Builder()
        .name("kerberos-credentials-service")
        .displayName("Kerberos Credentials Service")
        .description("Specifies the Kerberos Credentials Controller Service that should be used for authenticating with Kerberos")
        .identifiesControllerService(KerberosCredentialsService.class)
        .required(false)
        .build();

    static final PropertyDescriptor KERBEROS_USER_SERVICE = new PropertyDescriptor.Builder()
            .name("kerberos-user-service")
            .displayName("Kerberos User Service")
            .description("Specifies the Kerberos User Controller Service that should be used for authenticating with Kerberos")
            .identifiesControllerService(KerberosUserService.class)
            .required(false)
            .build();

    static final PropertyDescriptor HADOOP_CONF_FILES = new PropertyDescriptor.Builder()
        .name("Hadoop Configuration Files")
        .description("Comma-separated list of Hadoop Configuration files," +
            " such as hbase-site.xml and core-site.xml for kerberos, " +
            "including full paths to the files.")
        .identifiesExternalResource(ResourceCardinality.MULTIPLE, ResourceType.FILE, ResourceType.DIRECTORY)
        .expressionLanguageSupported(ExpressionLanguageScope.ENVIRONMENT)
        .build();

    static final PropertyDescriptor ZOOKEEPER_QUORUM = new PropertyDescriptor.Builder()
        .name("ZooKeeper Quorum")
        .description("Comma-separated list of ZooKeeper hosts for HBase. Required if Hadoop Configuration Files are not provided.")
        .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
        .expressionLanguageSupported(ExpressionLanguageScope.ENVIRONMENT)
        .build();

    static final PropertyDescriptor ZOOKEEPER_CLIENT_PORT = new PropertyDescriptor.Builder()
        .name("ZooKeeper Client Port")
        .description("The port on which ZooKeeper is accepting client connections. Required if Hadoop Configuration Files are not provided.")
        .addValidator(StandardValidators.PORT_VALIDATOR)
        .expressionLanguageSupported(ExpressionLanguageScope.ENVIRONMENT)
        .build();

    static final PropertyDescriptor ZOOKEEPER_ZNODE_PARENT = new PropertyDescriptor.Builder()
        .name("ZooKeeper ZNode Parent")
        .description("The ZooKeeper ZNode Parent value for HBase (example: /hbase). Required if Hadoop Configuration Files are not provided.")
        .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
        .expressionLanguageSupported(ExpressionLanguageScope.ENVIRONMENT)
        .build();

    static final PropertyDescriptor HBASE_CLIENT_RETRIES = new PropertyDescriptor.Builder()
        .name("HBase Client Retries")
        .description("The number of times the HBase client will retry connecting. Required if Hadoop Configuration Files are not provided.")
        .addValidator(StandardValidators.POSITIVE_INTEGER_VALIDATOR)
        .defaultValue("1")
        .expressionLanguageSupported(ExpressionLanguageScope.ENVIRONMENT)
        .build();

    // This property is never referenced directly but is necessary so that the classpath will be dynamically modified.
    static final PropertyDescriptor PHOENIX_CLIENT_JAR_LOCATION = new PropertyDescriptor.Builder()
        .name("Phoenix Client JAR Location")
        .description("The full path to the Phoenix client JAR. Required if Phoenix is installed on top of HBase.")
        .identifiesExternalResource(ResourceCardinality.SINGLE, ResourceType.FILE, ResourceType.DIRECTORY, ResourceType.URL)
        .expressionLanguageSupported(ExpressionLanguageScope.ENVIRONMENT)
        .dynamicallyModifiesClasspath(true)
        .build();

    static final String HBASE_CONF_ZK_QUORUM = "hbase.zookeeper.quorum";
    static final String HBASE_CONF_ZK_PORT = "hbase.zookeeper.property.clientPort";
    static final String HBASE_CONF_ZNODE_PARENT = "zookeeper.znode.parent";
    static final String HBASE_CONF_CLIENT_RETRIES = "hbase.client.retries.number";

    private volatile Connection connection;
    private volatile UserGroupInformation ugi;
    private final AtomicReference<KerberosUser> kerberosUserReference = new AtomicReference<>();
    private volatile String masterAddress;

    private List<PropertyDescriptor> properties;
    private KerberosProperties kerberosProperties;
    private volatile File kerberosConfigFile = null;

    // Holder of cached Configuration information so validation does not reload the same config over and over
    private final AtomicReference<ValidationResources> validationResourceHolder = new AtomicReference<>();


    @Override
    protected void init(ControllerServiceInitializationContext config) throws InitializationException {
        kerberosConfigFile = config.getKerberosConfigurationFile();
        kerberosProperties = getKerberosProperties(kerberosConfigFile);

        List<PropertyDescriptor> props = new ArrayList<>();
        props.add(HADOOP_CONF_FILES);
        props.add(KERBEROS_USER_SERVICE);
        props.add(KERBEROS_CREDENTIALS_SERVICE);
        props.add(kerberosProperties.getKerberosPrincipal());
        props.add(kerberosProperties.getKerberosKeytab());
        props.add(kerberosProperties.getKerberosPassword());
        props.add(ZOOKEEPER_QUORUM);
        props.add(ZOOKEEPER_CLIENT_PORT);
        props.add(ZOOKEEPER_ZNODE_PARENT);
        props.add(HBASE_CLIENT_RETRIES);
        props.add(PHOENIX_CLIENT_JAR_LOCATION);
        props.addAll(getAdditionalProperties());
        this.properties = Collections.unmodifiableList(props);
    }

    protected List<PropertyDescriptor> getAdditionalProperties() {
        return new ArrayList<>();
    }

    protected KerberosProperties getKerberosProperties(File kerberosConfigFile) {
        return new KerberosProperties(kerberosConfigFile);
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return properties;
    }

    @Override
    protected PropertyDescriptor getSupportedDynamicPropertyDescriptor(String propertyDescriptorName) {
        return new PropertyDescriptor.Builder()
                .description("Specifies the value for '" + propertyDescriptorName + "' in the HBase configuration.")
                .name(propertyDescriptorName)
                .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
                .dynamic(true)
                .build();
    }

    @Override
    protected Collection<ValidationResult> customValidate(ValidationContext validationContext) {
        boolean confFileProvided = validationContext.getProperty(HADOOP_CONF_FILES).isSet();
        boolean zkQuorumProvided = validationContext.getProperty(ZOOKEEPER_QUORUM).isSet();
        boolean zkPortProvided = validationContext.getProperty(ZOOKEEPER_CLIENT_PORT).isSet();
        boolean znodeParentProvided = validationContext.getProperty(ZOOKEEPER_ZNODE_PARENT).isSet();
        boolean retriesProvided = validationContext.getProperty(HBASE_CLIENT_RETRIES).isSet();

        final String explicitPrincipal = validationContext.getProperty(kerberosProperties.getKerberosPrincipal()).evaluateAttributeExpressions().getValue();
        final String explicitKeytab = validationContext.getProperty(kerberosProperties.getKerberosKeytab()).evaluateAttributeExpressions().getValue();
        final String explicitPassword = validationContext.getProperty(kerberosProperties.getKerberosPassword()).getValue();
        final KerberosCredentialsService credentialsService = validationContext.getProperty(KERBEROS_CREDENTIALS_SERVICE).asControllerService(KerberosCredentialsService.class);
        final KerberosUserService kerberosUserService = validationContext.getProperty(KERBEROS_USER_SERVICE).asControllerService(KerberosUserService.class);

        final String resolvedPrincipal;
        final String resolvedKeytab;
        if (credentialsService == null) {
            resolvedPrincipal = explicitPrincipal;
            resolvedKeytab = explicitKeytab;
        } else {
            resolvedPrincipal = credentialsService.getPrincipal();
            resolvedKeytab = credentialsService.getKeytab();
        }

        final List<ValidationResult> problems = new ArrayList<>();

        if (!confFileProvided && (!zkQuorumProvided || !zkPortProvided || !znodeParentProvided || !retriesProvided)) {
            problems.add(new ValidationResult.Builder()
                    .valid(false)
                    .subject(this.getClass().getSimpleName())
                    .explanation("ZooKeeper Quorum, ZooKeeper Client Port, ZooKeeper ZNode Parent, and HBase Client Retries are required " +
                            "when Hadoop Configuration Files are not provided.")
                    .build());
        }

        if (confFileProvided) {
            final String configFiles = validationContext.getProperty(HADOOP_CONF_FILES).evaluateAttributeExpressions().getValue();
            ValidationResources resources = validationResourceHolder.get();

            // if no resources in the holder, or if the holder has different resources loaded,
            // then load the Configuration and set the new resources in the holder
            if (resources == null || !configFiles.equals(resources.getConfigResources())) {
                getLogger().debug("Reloading validation resources");
                resources = new ValidationResources(configFiles, getConfigurationFromFiles(configFiles));
                validationResourceHolder.set(resources);
            }

            final Configuration hbaseConfig = resources.getConfiguration();
            if (kerberosUserService == null) {
                problems.addAll(KerberosProperties.validatePrincipalWithKeytabOrPassword(getClass().getSimpleName(), hbaseConfig,
                        resolvedPrincipal, resolvedKeytab, explicitPassword, getLogger()));
            } else {
                final boolean securityEnabled = SecurityUtil.isSecurityEnabled(hbaseConfig);
                if (!securityEnabled) {
                    getLogger().warn("Hadoop Configuration does not have security enabled, KerberosUserService will be ignored");
                }
            }
        }

        if (credentialsService != null && (explicitPrincipal != null || explicitKeytab != null || explicitPassword != null)) {
            problems.add(new ValidationResult.Builder()
                .subject("Kerberos Credentials")
                .valid(false)
                .explanation("Cannot specify a Kerberos Credentials Service while also specifying a Kerberos Principal, Kerberos Keytab, or Kerberos Password")
                .build());
        }

        if (kerberosUserService != null && (explicitPrincipal != null || explicitKeytab != null || explicitPassword != null)) {
            problems.add(new ValidationResult.Builder()
                    .subject("Kerberos User")
                    .valid(false)
                    .explanation("Cannot specify a Kerberos User Service while also specifying a Kerberos Principal, Kerberos Keytab, or Kerberos Password")
                    .build());
        }

        if (kerberosUserService != null && credentialsService != null) {
            problems.add(new ValidationResult.Builder()
                    .subject("Kerberos User")
                    .valid(false)
                    .explanation("Cannot specify a Kerberos User Service while also specifying a Kerberos Credentials Service")
                    .build());
        }

        if (!isAllowExplicitKeytab() && explicitKeytab != null) {
            problems.add(new ValidationResult.Builder()
                .subject("Kerberos Credentials")
                .valid(false)
                .explanation("The '" + ALLOW_EXPLICIT_KEYTAB + "' system environment variable is configured to forbid explicitly configuring Kerberos Keytab in processors. "
                    + "The Kerberos Credentials Service should be used instead of setting the Kerberos Keytab or Kerberos Principal property.")
                .build());
        }

        return problems;
    }

    /**
     * As of Apache NiFi 1.5.0, due to changes made to
     * {@link SecurityUtil#loginKerberos(Configuration, String, String)}, which is used by this
     * class to authenticate a principal with Kerberos, HBase controller services no longer
     * attempt relogins explicitly.  For more information, please read the documentation for
     * {@link SecurityUtil#loginKerberos(Configuration, String, String)}.
     *
     * @see SecurityUtil#loginKerberos(Configuration, String, String)
     */
    @OnEnabled
    public void onEnabled(final ConfigurationContext context) throws IOException, InterruptedException {
        this.connection = createConnection(context);

        // connection check
        if (this.connection != null) {
            final Admin admin = this.connection.getAdmin();
            if (admin != null) {
                admin.listTableNames();

                final ClusterMetrics metrics = admin.getClusterMetrics();
                if (metrics != null) {
                    final ServerName master = metrics.getMasterName();
                    masterAddress = master == null ? null : master.getAddress().toString();
                }
            }
        }
    }

    protected Connection createConnection(final ConfigurationContext context) throws IOException, InterruptedException {
        final String configFiles = context.getProperty(HADOOP_CONF_FILES).evaluateAttributeExpressions().getValue();
        final Configuration hbaseConfig = getConfigurationFromFiles(configFiles);

        // override with any properties that are provided
        if (context.getProperty(ZOOKEEPER_QUORUM).isSet()) {
            hbaseConfig.set(HBASE_CONF_ZK_QUORUM, context.getProperty(ZOOKEEPER_QUORUM).evaluateAttributeExpressions().getValue());
        }
        if (context.getProperty(ZOOKEEPER_CLIENT_PORT).isSet()) {
            hbaseConfig.set(HBASE_CONF_ZK_PORT, context.getProperty(ZOOKEEPER_CLIENT_PORT).evaluateAttributeExpressions().getValue());
        }
        if (context.getProperty(ZOOKEEPER_ZNODE_PARENT).isSet()) {
            hbaseConfig.set(HBASE_CONF_ZNODE_PARENT, context.getProperty(ZOOKEEPER_ZNODE_PARENT).evaluateAttributeExpressions().getValue());
        }
        if (context.getProperty(HBASE_CLIENT_RETRIES).isSet()) {
            hbaseConfig.set(HBASE_CONF_CLIENT_RETRIES, context.getProperty(HBASE_CLIENT_RETRIES).evaluateAttributeExpressions().getValue());
        }

        // add any dynamic properties to the HBase configuration
        for (final Map.Entry<PropertyDescriptor, String> entry : context.getProperties().entrySet()) {
            final PropertyDescriptor descriptor = entry.getKey();
            if (descriptor.isDynamic()) {
                hbaseConfig.set(descriptor.getName(), entry.getValue());
            }
        }

        if (SecurityUtil.isSecurityEnabled(hbaseConfig)) {
            getLogger().debug("HBase Security Enabled, creating KerberosUser");
            final KerberosUser kerberosUser = createKerberosUser(context);
            ugi = SecurityUtil.getUgiForKerberosUser(hbaseConfig, kerberosUser);
            kerberosUserReference.set(kerberosUser);
            getLogger().info("Successfully logged in as principal {}", kerberosUser.getPrincipal());
            return getUgi().doAs((PrivilegedExceptionAction<Connection>) () ->  ConnectionFactory.createConnection(hbaseConfig));
        } else {
            getLogger().debug("Simple Authentication");
            return ConnectionFactory.createConnection(hbaseConfig);
        }
    }

    protected KerberosUser createKerberosUser(final ConfigurationContext context) {
        // Check Kerberos User Service first, if present then get the KerberosUser from the service
        // The customValidate method ensures that KerberosUserService can't be set at the same time as the credentials service or explicit properties
        final KerberosUserService kerberosUserService = context.getProperty(KERBEROS_USER_SERVICE).asControllerService(KerberosUserService.class);
        if (kerberosUserService != null) {
            return kerberosUserService.createKerberosUser();
        }

        String principal = context.getProperty(kerberosProperties.getKerberosPrincipal()).evaluateAttributeExpressions().getValue();
        String keyTab = context.getProperty(kerberosProperties.getKerberosKeytab()).evaluateAttributeExpressions().getValue();
        String password = context.getProperty(kerberosProperties.getKerberosPassword()).getValue();

        // If the Kerberos Credentials Service is specified, we need to use its configuration, not the explicit properties for principal/keytab.
        // The customValidate method ensures that only one can be set, so we know that the principal & keytab above are null.
        final KerberosCredentialsService credentialsService = context.getProperty(KERBEROS_CREDENTIALS_SERVICE).asControllerService(KerberosCredentialsService.class);
        if (credentialsService != null) {
            principal = credentialsService.getPrincipal();
            keyTab = credentialsService.getKeytab();
        }

        if (keyTab != null) {
            return new KerberosKeytabUser(principal, keyTab);
        } else if (password != null) {
            return new KerberosPasswordUser(principal, password);
        } else {
            throw new IllegalStateException("Unable to authenticate with Kerberos, no keytab or password was provided");
        }
    }

    protected Configuration getConfigurationFromFiles(final String configFiles) {
        final Configuration hbaseConfig = HBaseConfiguration.create();
        if (StringUtils.isNotBlank(configFiles)) {
            for (final String configFile : configFiles.split(",")) {
                hbaseConfig.addResource(new Path(configFile.trim()));
            }
        }
        return hbaseConfig;
    }

    @OnDisabled
    public void shutdown() {
        if (connection != null) {
            try {
                connection.close();
            } catch (final Exception e) {
                getLogger().warn("HBase connection close failed", e);
            }
        }

        final KerberosUser kerberosUser = kerberosUserReference.get();
        if (kerberosUser != null) {
            try {
                kerberosUser.logout();
            } catch (final Exception e) {
                getLogger().warn("KeberosUser Logout Failed", e);
            } finally {
                ugi = null;
                kerberosUserReference.set(null);
            }
        }
    }

    protected List<Put> buildPuts(byte[] rowKey, List<PutColumn> columns) {
        List<Put> retVal = new ArrayList<>();

        try {
            Put put = null;

            for (final PutColumn column : columns) {
                if (put == null || (put.getCellVisibility() == null && column.getVisibility() != null) || ( put.getCellVisibility() != null
                        && !put.getCellVisibility().getExpression().equals(column.getVisibility())
                    )) {
                    put = new Put(rowKey);

                    if (column.getVisibility() != null) {
                        put.setCellVisibility(new CellVisibility(column.getVisibility()));
                    }
                    retVal.add(put);
                }

                if (column.getTimestamp() != null) {
                    put.addColumn(
                            column.getColumnFamily(),
                            column.getColumnQualifier(),
                            column.getTimestamp(),
                            column.getBuffer());
                } else {
                    put.addColumn(
                            column.getColumnFamily(),
                            column.getColumnQualifier(),
                            column.getBuffer());
                }
            }
        } catch (DeserializationException de) {
            getLogger().error("Error writing cell visibility statement.", de);
            throw new RuntimeException(de);
        }

        return retVal;
    }

    @Override
    public void put(final String tableName, final Collection<PutFlowFile> puts) throws IOException {
        SecurityUtil.callWithUgi(getUgi(), () -> {
            try (final Table table = connection.getTable(TableName.valueOf(tableName))) {
                // Create one Put per row....
                final Map<String, List<PutColumn>> sorted = new HashMap<>();
                final List<Put> newPuts = new ArrayList<>();

                for (final PutFlowFile putFlowFile : puts) {
                    final String rowKeyString = new String(putFlowFile.getRow(), StandardCharsets.UTF_8);
                    final List<PutColumn> columns = sorted.computeIfAbsent(rowKeyString, k -> new ArrayList<>());

                    columns.addAll(putFlowFile.getColumns());
                }

                for (final Map.Entry<String, List<PutColumn>> entry : sorted.entrySet()) {
                    newPuts.addAll(buildPuts(entry.getKey().getBytes(StandardCharsets.UTF_8), entry.getValue()));
                }

                table.put(newPuts);
            }

            return null;
        });
    }

    @Override
    public void put(final String tableName, final byte[] rowId, final Collection<PutColumn> columns) throws IOException {
        SecurityUtil.callWithUgi(getUgi(), () -> {
            try (final Table table = connection.getTable(TableName.valueOf(tableName))) {
                table.put(buildPuts(rowId, new ArrayList<>(columns)));
            }
            return null;
        });
    }

    @Override
    public boolean checkAndPut(final String tableName, final byte[] rowId, final byte[] family, final byte[] qualifier, final byte[] value, final PutColumn column) throws IOException {
        return SecurityUtil.callWithUgi(getUgi(), () -> {
            try (final Table table = connection.getTable(TableName.valueOf(tableName))) {
                final Put put = new Put(rowId);
                put.addColumn(
                    column.getColumnFamily(),
                    column.getColumnQualifier(),
                    column.getBuffer());

                final CheckAndMutate checkAndMutate = CheckAndMutate.newBuilder(rowId)
                    .ifEquals(family, qualifier, value)
                    .build(put);
                final CheckAndMutateResult result = table.checkAndMutate(checkAndMutate);
                return result.isSuccess();
            }
        });
    }

    @Override
    public void delete(final String tableName, final byte[] rowId) throws IOException {
        delete(tableName, rowId, null);
    }

    @Override
    public void delete(String tableName, byte[] rowId, String visibilityLabel) throws IOException {
        SecurityUtil.callWithUgi(getUgi(), () -> {
            try (final Table table = connection.getTable(TableName.valueOf(tableName))) {
                Delete delete = new Delete(rowId);
                if (!StringUtils.isEmpty(visibilityLabel)) {
                    delete.setCellVisibility(new CellVisibility(visibilityLabel));
                }
                table.delete(delete);
            }
            return null;
        });
    }

    @Override
    public void delete(final String tableName, final List<byte[]> rowIds) throws IOException {
        delete(tableName, rowIds, null);
    }

    @Override
    public void deleteCells(final String tableName, final List<DeleteRequest> deletes) throws IOException {
        final List<Delete> deleteRequests = new ArrayList<>();
        for (final DeleteRequest req : deletes) {
            final Delete delete = new Delete(req.getRowId())
                .addColumn(req.getColumnFamily(), req.getColumnQualifier());

            if (!StringUtils.isEmpty(req.getVisibilityLabel())) {
                delete.setCellVisibility(new CellVisibility(req.getVisibilityLabel()));
            }

            deleteRequests.add(delete);
        }

        batchDelete(tableName, deleteRequests);
    }

    @Override
    public void delete(String tableName, List<byte[]> rowIds, String visibilityLabel) throws IOException {
        final List<Delete> deletes = new ArrayList<>();
        for (final byte[] rowId : rowIds) {
            final Delete delete = new Delete(rowId);
            if (!StringUtils.isBlank(visibilityLabel)) {
                delete.setCellVisibility(new CellVisibility(visibilityLabel));
            }

            deletes.add(delete);
        }

        batchDelete(tableName, deletes);
    }

    private void batchDelete(String tableName, List<Delete> deletes) throws IOException {
        SecurityUtil.callWithUgi(getUgi(), () -> {
            try (final Table table = connection.getTable(TableName.valueOf(tableName))) {
                table.delete(deletes);
            }
            return null;
        });
    }

    @Override
    public void scan(final String tableName, final Collection<Column> columns, final String filterExpression, final long minTime, final ResultHandler handler)
            throws IOException {
        scan(tableName, columns, filterExpression, minTime, null, handler);
    }

    @Override
    public void scan(String tableName, Collection<Column> columns, String filterExpression, long minTime, List<String> visibilityLabels, ResultHandler handler) throws IOException {
        SecurityUtil.callWithUgi(getUgi(), () -> {
            Filter filter = null;
            if (!StringUtils.isBlank(filterExpression)) {
                ParseFilter parseFilter = new ParseFilter();
                filter = parseFilter.parseFilterString(filterExpression);
            }

            try (final Table table = connection.getTable(TableName.valueOf(tableName));
                 final ResultScanner scanner = getResults(table, columns, filter, minTime, visibilityLabels)) {

                for (final Result result : scanner) {
                    final byte[] rowKey = result.getRow();
                    final Cell[] cells = result.rawCells();

                    if (cells == null) {
                        continue;
                    }

                    // convert HBase cells to NiFi cells
                    final ResultCell[] resultCells = new ResultCell[cells.length];
                    for (int i = 0; i < cells.length; i++) {
                        final Cell cell = cells[i];
                        final ResultCell resultCell = getResultCell(cell);
                        resultCells[i] = resultCell;
                    }

                    // delegate to the handler
                    handler.handle(rowKey, resultCells);
                }
            }
            return null;
        });
    }

    @Override
    public void scan(final String tableName, final byte[] startRow, final byte[] endRow, final Collection<Column> columns, List<String> authorizations, final ResultHandler handler)
            throws IOException {

        SecurityUtil.callWithUgi(getUgi(), () -> {
            try (final Table table = connection.getTable(TableName.valueOf(tableName));
                 final ResultScanner scanner = getResults(table, startRow, endRow, columns, authorizations)) {

                for (final Result result : scanner) {
                    final byte[] rowKey = result.getRow();
                    final Cell[] cells = result.rawCells();

                    if (cells == null) {
                        continue;
                    }

                    // convert HBase cells to NiFi cells
                    final ResultCell[] resultCells = new ResultCell[cells.length];
                    for (int i = 0; i < cells.length; i++) {
                        final Cell cell = cells[i];
                        final ResultCell resultCell = getResultCell(cell);
                        resultCells[i] = resultCell;
                    }

                    // delegate to the handler
                    handler.handle(rowKey, resultCells);
                }
            }
            return null;
        });
    }

    @Override
    public void scan(final String tableName, final String startRow, final String endRow, String filterExpression,
            final Long timerangeMin, final Long timerangeMax, final Integer limitRows, final Boolean isReversed,
            final Boolean blockCache, final Collection<Column> columns, List<String> visibilityLabels, final ResultHandler handler) throws IOException {

        SecurityUtil.callWithUgi(getUgi(), () -> {
            try (final Table table = connection.getTable(TableName.valueOf(tableName));
                 final ResultScanner scanner = getResults(table, startRow, endRow, filterExpression, timerangeMin,
                     timerangeMax, isReversed, blockCache, columns, visibilityLabels)) {

                int cnt = 0;
                final int lim = limitRows != null ? limitRows : 0;
                for (final Result result : scanner) {

                    if (lim > 0 && ++cnt > lim) {
                        break;
                    }

                    final byte[] rowKey = result.getRow();
                    final Cell[] cells = result.rawCells();

                    if (cells == null) {
                        continue;
                    }

                    // convert HBase cells to NiFi cells
                    final ResultCell[] resultCells = new ResultCell[cells.length];
                    for (int i = 0; i < cells.length; i++) {
                        final Cell cell = cells[i];
                        final ResultCell resultCell = getResultCell(cell);
                        resultCells[i] = resultCell;
                    }

                    // delegate to the handler
                    handler.handle(rowKey, resultCells);
                }
            }
            return null;
        });
    }

    //
    protected ResultScanner getResults(final Table table, final String startRow, final String endRow, final String filterExpression, final Long timerangeMin, final Long timerangeMax,
                                       final Boolean isReversed, final Boolean blockCache, final Collection<Column> columns, List<String> authorizations) throws IOException {
        Scan scan = new Scan();
        if (!StringUtils.isBlank(startRow)) {
            scan = scan.withStartRow(startRow.getBytes(StandardCharsets.UTF_8));
        }
        if (!StringUtils.isBlank(endRow)) {
            scan = scan.withStopRow(endRow.getBytes(StandardCharsets.UTF_8));
        }

        if (authorizations != null && authorizations.size() > 0) {
            scan.setAuthorizations(new Authorizations(authorizations));
        }

        Filter filter = null;
        if (columns != null) {
            for (Column col : columns) {
                if (col.getQualifier() == null) {
                    scan.addFamily(col.getFamily());
                } else {
                    scan.addColumn(col.getFamily(), col.getQualifier());
                }
            }
        }
        if (!StringUtils.isBlank(filterExpression)) {
            ParseFilter parseFilter = new ParseFilter();
            filter = parseFilter.parseFilterString(filterExpression);
        }
        if (filter != null) {
            scan.setFilter(filter);
        }

        if (timerangeMin != null && timerangeMax != null) {
            scan.setTimeRange(timerangeMin, timerangeMax);
        }

        // ->>> reserved for HBase v 2 or later
        //if (limitRows != null && limitRows > 0){
        //    scan.setLimit(limitRows)
        //}

        if (isReversed != null) {
            scan.setReversed(isReversed);
        }

        scan.setCacheBlocks(blockCache);

        return table.getScanner(scan);
    }

    // protected and extracted into separate method for testing
    protected ResultScanner getResults(final Table table, final byte[] startRow, final byte[] endRow, final Collection<Column> columns, List<String> authorizations) throws IOException {
        Scan scan = new Scan();
        scan = scan.withStartRow(startRow);
        scan = scan.withStopRow(endRow);

        if (authorizations != null && authorizations.size() > 0) {
            scan.setAuthorizations(new Authorizations(authorizations));
        }

        if (columns != null && columns.size() > 0) {
            for (Column col : columns) {
                if (col.getQualifier() == null) {
                    scan.addFamily(col.getFamily());
                } else {
                    scan.addColumn(col.getFamily(), col.getQualifier());
                }
            }
        }

        return table.getScanner(scan);
    }

    // protected and extracted into separate method for testing
    protected ResultScanner getResults(final Table table, final Collection<Column> columns, final Filter filter, final long minTime, List<String> authorizations) throws IOException {
        // Create a new scan. We will set the min timerange as the latest timestamp that
        // we have seen so far. The minimum timestamp is inclusive, so we will get duplicates.
        // We will record any cells that have the latest timestamp, so that when we scan again,
        // we know to throw away those duplicates.
        final Scan scan = new Scan();
        scan.setTimeRange(minTime, Long.MAX_VALUE);

        if (authorizations != null && authorizations.size() > 0) {
            scan.setAuthorizations(new Authorizations(authorizations));
        }

        if (filter != null) {
            scan.setFilter(filter);
        }

        if (columns != null) {
            for (Column col : columns) {
                if (col.getQualifier() == null) {
                    scan.addFamily(col.getFamily());
                } else {
                    scan.addColumn(col.getFamily(), col.getQualifier());
                }
            }
        }

        return table.getScanner(scan);
    }

    private ResultCell getResultCell(Cell cell) {
        final ResultCell resultCell = new ResultCell();
        resultCell.setRowArray(cell.getRowArray());
        resultCell.setRowOffset(cell.getRowOffset());
        resultCell.setRowLength(cell.getRowLength());

        resultCell.setFamilyArray(cell.getFamilyArray());
        resultCell.setFamilyOffset(cell.getFamilyOffset());
        resultCell.setFamilyLength(cell.getFamilyLength());

        resultCell.setQualifierArray(cell.getQualifierArray());
        resultCell.setQualifierOffset(cell.getQualifierOffset());
        resultCell.setQualifierLength(cell.getQualifierLength());

        resultCell.setTimestamp(cell.getTimestamp());
        final Cell.Type cellType = cell.getType();
        if (cellType != null) {
            resultCell.setTypeByte(cellType.getCode());
        }

        resultCell.setValueArray(cell.getValueArray());
        resultCell.setValueOffset(cell.getValueOffset());
        resultCell.setValueLength(cell.getValueLength());

        return resultCell;
    }

    @Override
    public List<HBaseRegion> listHBaseRegions(final String tableName) throws HBaseClientException {
        if (connection == null || connection.isClosed() || connection.isAborted()) {
            final String errorMsg = String.format(
                    "Unable to fetch regions for table %s since there is no active connection to HBase.",
                    tableName
            );
            throw new IllegalStateException(errorMsg);
        }

        try {
            final List<RegionInfo> regionInfos = connection.getAdmin().getRegions(TableName.valueOf(tableName));
            // maps to the NiFi HBaseRegion object
            final List<HBaseRegion> regions = regionInfos.stream()
                    .map(regionInfo ->
                            new HBaseRegion(
                                regionInfo.getStartKey(),
                                regionInfo.getEndKey(),
                                regionInfo.getRegionNameAsString(),
                                regionInfo.getRegionId(),
                                regionInfo.isDegenerate()
                            )
                    )
                    .collect(Collectors.toList());
            return regions;
        } catch (final IOException e) {
            logger.error("Encountered error while communicating with HBase.", e);
            throw new HBaseClientException(e);
        }
    }

    static protected class ValidationResources {
        private final String configResources;
        private final Configuration configuration;

        public ValidationResources(final String configResources, final Configuration configuration) {
            this.configResources = configResources;
            this.configuration = configuration;
        }

        public String getConfigResources() {
            return configResources;
        }

        public Configuration getConfiguration() {
            return configuration;
        }
    }

    @Override
    public byte[] toBytes(boolean b) {
        return Bytes.toBytes(b);
    }

    @Override
    public byte[] toBytes(float f) {
        return Bytes.toBytes(f);
    }

    @Override
    public byte[] toBytes(int i) {
        return Bytes.toBytes(i);
    }

    @Override
    public byte[] toBytes(long l) {
        return Bytes.toBytes(l);
    }

    @Override
    public byte[] toBytes(double d) {
        return Bytes.toBytes(d);
    }

    @Override
    public byte[] toBytes(String s) {
        return Bytes.toBytes(s);
    }

    @Override
    public byte[] toBytesBinary(String s) {
        return Bytes.toBytesBinary(s);
    }

    @Override
    public String toTransitUri(String tableName, String rowKey) {
        if (connection == null) {
            logger.warn("Connection has not been established, could not create a transit URI. Returning null.");
            return null;
        }
        final String transitUriMasterAddress = StringUtils.isEmpty(masterAddress) ? "unknown" : masterAddress;
        return "hbase://" + transitUriMasterAddress + "/" + tableName + (StringUtils.isEmpty(rowKey) ? "" : "/" + rowKey);
    }

    /*
     * Overridable by subclasses in the same package, mainly intended for testing purposes to allow verification without having to set environment variables.
     */
    boolean isAllowExplicitKeytab() {
        return Boolean.parseBoolean(System.getenv(ALLOW_EXPLICIT_KEYTAB));
    }

    UserGroupInformation getUgi() throws IOException {
        getLogger().trace("getting UGI instance");
        // if there is a KerberosUser associated with UGI, call checkTGTAndRelogin to ensure UGI's underlying Subject has a valid ticket
        SecurityUtil.checkTGTAndRelogin(getLogger(), kerberosUserReference.get());
        return ugi;
    }
}
