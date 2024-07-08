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
package org.apache.nifi.tests.system;

import org.apache.nifi.bootstrap.RunNiFi;
import org.apache.nifi.registry.security.util.KeystoreType;
import org.apache.nifi.toolkit.cli.impl.client.nifi.NiFiClient;
import org.apache.nifi.toolkit.cli.impl.client.nifi.NiFiClientConfig;
import org.apache.nifi.toolkit.cli.impl.client.nifi.impl.JerseyNiFiClient;
import org.apache.nifi.util.file.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpawnedStandaloneNiFiInstanceFactory implements NiFiInstanceFactory {
    private static final Logger logger = LoggerFactory.getLogger(SpawnedStandaloneNiFiInstanceFactory.class);
    private final InstanceConfiguration instanceConfig;

    public SpawnedStandaloneNiFiInstanceFactory(final InstanceConfiguration instanceConfig) {
        this.instanceConfig = instanceConfig;
    }

    @Override
    public NiFiInstance createInstance() {
        return new RunNiFiInstance(instanceConfig);
    }

    @Override
    public boolean isClusteredInstance() {
        return false;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        final SpawnedStandaloneNiFiInstanceFactory that = (SpawnedStandaloneNiFiInstanceFactory) other;
        return Objects.equals(instanceConfig, that.instanceConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instanceConfig);
    }

    private static class RunNiFiInstance implements NiFiInstance {
        private final File instanceDirectory;
        private final File configDir;
        private final InstanceConfiguration instanceConfiguration;
        private File bootstrapConfigFile;
        private RunNiFi runNiFi;

        public RunNiFiInstance(final InstanceConfiguration instanceConfiguration) {
            this.instanceDirectory = instanceConfiguration.getInstanceDirectory();
            this.bootstrapConfigFile = instanceConfiguration.getBootstrapConfigFile();
            this.instanceConfiguration = instanceConfiguration;

            final Properties bootstrapProperties = new Properties();
            try (final InputStream fis = new FileInputStream(bootstrapConfigFile)) {
                bootstrapProperties.load(fis);
            } catch (final IOException e) {
                throw new RuntimeException("Could not load boostrap config file " + bootstrapConfigFile, e);
            }

            final String confDirName = bootstrapProperties.getProperty("conf.dir");
            final File tempConfDir = new File(confDirName);
            if (tempConfDir.isAbsolute()) {
                configDir = tempConfDir;
            } else {
                configDir = new File(instanceDirectory, confDirName);
            }
        }

        @Override
        public String toString() {
            return "RunNiFiInstance[dir=" + instanceDirectory + "]";
        }

        @Override
        public void start(final boolean waitForCompletion) {
            if (runNiFi != null) {
                throw new IllegalStateException("NiFi has already been started");
            }

            logger.info("Starting NiFi [{}]", instanceDirectory.getName());

            try {
                this.runNiFi = new RunNiFi(bootstrapConfigFile);
            } catch (IOException e) {
                throw new RuntimeException("Failed to start NiFi", e);
            }

            try {
                runNiFi.start(false);

                if (waitForCompletion) {
                    waitForStartup();
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to start NiFi", e);
            }
        }

        @Override
        public void createEnvironment() throws IOException {
            logger.info("Creating environment for NiFi [{}]", instanceDirectory.getName());

            cleanup();

            final File destinationConf = new File(instanceDirectory, "conf");
            copyContents(bootstrapConfigFile.getParentFile(), destinationConf);
            bootstrapConfigFile = new File(destinationConf, bootstrapConfigFile.getName());

            final File destinationLib = new File(instanceDirectory, "lib");
            copyContents(new File("target/nifi-lib-assembly/lib"), destinationLib);

            final File destinationPythonDir = new File(instanceDirectory, "python");
            final File destinationPythonFrameworkDir = new File(destinationPythonDir, "framework");
            final File destinationPythonApiDir = new File(destinationPythonDir, "api");
            destinationPythonFrameworkDir.mkdirs();
            destinationPythonApiDir.mkdirs();

            copyContents(new File("target/nifi-lib-assembly/python/framework"), destinationPythonFrameworkDir);
            copyContents(new File("target/nifi-lib-assembly/python/api"), destinationPythonApiDir);

            if (instanceConfiguration.isUnpackPythonExtensions()) {
                final File destinationPythonExtensionsDir = new File(destinationPythonDir, "extensions");
                destinationPythonExtensionsDir.mkdirs();
                copyContents(new File("target/nifi-lib-assembly/python/extensions"), destinationPythonExtensionsDir);
            }

            final File destinationNarProviderNars = new File(instanceDirectory, "nifi-nar-provider-nars");
            copyContents(new File("target/nifi-nar-provider-nars"), destinationNarProviderNars);

            final File destinationExtensionsDir = new File(instanceDirectory, "extensions");
            destinationExtensionsDir.mkdir();

            final File destinationCertsDir = new File(instanceDirectory, "certs");
            if (!destinationCertsDir.exists()) {
                assertTrue(destinationCertsDir.mkdirs());
            }
            NiFiSystemKeyStoreProvider.configureKeyStores(destinationCertsDir);

            final File flowJsonGz = instanceConfiguration.getFlowJsonGz();
            if (flowJsonGz != null) {
                final File destinationFlowJsonGz = new File(destinationConf, "flow.json.gz");
                Files.copy(flowJsonGz.toPath(), destinationFlowJsonGz.toPath());
            }

            // Write out any Property overrides
            final Map<String, String> nifiPropertiesOverrides = instanceConfiguration.getNifiPropertiesOverrides();
            if (nifiPropertiesOverrides != null && !nifiPropertiesOverrides.isEmpty()) {
                final File destinationNifiProperties = new File(destinationConf, "nifi.properties");
                final File sourceNifiProperties = new File(bootstrapConfigFile.getParentFile(), "nifi.properties");

                final Properties nifiProperties = new Properties();
                try (final InputStream fis = new FileInputStream(sourceNifiProperties)) {
                    nifiProperties.load(fis);
                }

                nifiPropertiesOverrides.forEach(nifiProperties::setProperty);

                try (final OutputStream fos = new FileOutputStream(destinationNifiProperties)) {
                    nifiProperties.store(fos, null);
                }
            }
        }

        private void copyContents(final File dir, final File destinationDir) throws IOException {
            if (!destinationDir.exists()) {
                assertTrue(destinationDir.mkdirs());
            }

            final File[] sourceFiles = dir.listFiles();
            for (final File sourceFile : sourceFiles) {
                if (sourceFile.isDirectory()) {
                    final File destinationFile = new File(destinationDir, sourceFile.getName());
                    copyContents(sourceFile, destinationFile);
                    continue;
                }

                final File destinationFile = new File(destinationDir, sourceFile.getName());
                if (destinationFile.exists()) {
                    assertTrue(destinationFile.delete());
                }

                Files.copy(sourceFile.toPath(), destinationFile.toPath());
            }
        }

        @Override
        public boolean isAccessible() {
            if (runNiFi == null) {
                return false;
            }

            try (final NiFiClient client = createClient()) {
                client.getFlowClient().getRootGroupId();
                return true;
            } catch (final Exception e) {
                return false;
            }
        }

        private void waitForStartup() throws IOException {
            final long timeoutMillis = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5L);

            try (final NiFiClient client = createClient()) {
                while (true) {
                    try {
                        client.getFlowClient().getRootGroupId();
                        logger.info("NiFi Startup Completed [{}]", instanceDirectory.getName());
                        return;
                    } catch (final Exception e) {
                        if (System.currentTimeMillis() > timeoutMillis) {
                            throw new IOException("After waiting 5 minutes, NiFi instance still has not started");
                        }

                        try {
                            Thread.sleep(1000L);
                        } catch (InterruptedException ex) {
                            logger.debug("NiFi Startup sleep interrupted", ex);
                        }
                    }
                }
            }
        }

        @Override
        public void stop() {
            if (runNiFi == null) {
                logger.info("NiFi Shutdown Ignored (runNiFi==null) [{}]", instanceDirectory.getName());
                return;
            }

            logger.info("NiFi Shutdown Started [{}]", instanceDirectory.getName());

            try {
                runNiFi.stop();
                logger.info("NiFi Shutdown Completed [{}]", instanceDirectory.getName());
            } catch (IOException e) {
                throw new RuntimeException("Failed to stop NiFi", e);
            } finally {
                runNiFi = null;
            }
        }


        private void cleanup() throws IOException {
            if (instanceDirectory.exists()) {
                FileUtils.deleteFile(instanceDirectory, true);
            }
        }

        @Override
        public boolean isClustered() {
            return false;
        }

        @Override
        public int getNumberOfNodes() {
            return 1;
        }

        @Override
        public int getNumberOfNodes(final boolean includeOnlyAutoStartInstances) {
            return isAutoStart() ? 1 : 0;
        }

        @Override
        public NiFiInstance getNodeInstance(final int nodeIndex) {
            return null;
        }

        @Override
        public Properties getProperties() throws IOException {
            final File nifiPropsFile = new File(configDir, "nifi.properties");
            final Properties nifiProps = new Properties();

            try (final InputStream fis = new FileInputStream(nifiPropsFile)) {
                nifiProps.load(fis);
            }

            return nifiProps;
        }

        @Override
        public File getInstanceDirectory() {
            return instanceDirectory;
        }

        @Override
        public boolean isAutoStart() {
            return instanceConfiguration.isAutoStart();
        }

        @Override
        public void setProperty(final String propertyName, final String propertyValue) throws IOException {
            setProperties(Collections.singletonMap(propertyName, propertyValue));
        }

        @Override
        public void setProperties(final Map<String, String> properties) throws IOException {
            final Properties currentProperties = getProperties();
            currentProperties.putAll(properties);

            final File propertiesFile = new File(configDir, "nifi.properties");
            try (final OutputStream fos = new FileOutputStream(propertiesFile)) {
                currentProperties.store(fos, "");
            }
        }

        @Override
        public void quarantineTroubleshootingInfo(final File destinationDir, final Throwable cause) throws IOException {
            final String[] dirsToCopy = new String[] {"conf", "logs"};
            for (final String dirToCopy : dirsToCopy) {
                copyContents(new File(getInstanceDirectory(), dirToCopy), new File(destinationDir, dirToCopy));
            }

            if (runNiFi == null) {
                logger.warn("NiFi instance is not running so will not capture diagnostics for {}", getInstanceDirectory());
            } else {
                final File diagnosticsFile = new File(destinationDir, "diagnostics.txt");
                runNiFi.diagnostics(diagnosticsFile, false);
            }

            final File causeFile = new File(destinationDir, "test-failure-stack-trace.txt");
            try (final PrintWriter printWriter = new PrintWriter(causeFile)) {
                cause.printStackTrace(printWriter);
            }
        }

        public NiFiClient createClient() throws IOException {
            final Properties nifiProperties = getProperties();
            final String httpPort = nifiProperties.getProperty("nifi.web.http.port");
            final String httpsPort = nifiProperties.getProperty("nifi.web.https.port");
            final String webPort = (httpsPort == null || httpsPort.trim().isEmpty()) ? httpPort : httpsPort;

            final String keystoreType = nifiProperties.getProperty("nifi.security.keystoreType");
            final String truststoreType = nifiProperties.getProperty("nifi.security.truststoreType");

            final NiFiClientConfig clientConfig = new NiFiClientConfig.Builder()
                .baseUrl("http://localhost:" + webPort)
                .connectTimeout(30000)
                .readTimeout(30000)
                .keystoreFilename(getAbsolutePath(nifiProperties.getProperty("nifi.security.keystore")))
                .keystorePassword(nifiProperties.getProperty("nifi.security.keystorePasswd"))
                .keystoreType(keystoreType == null ? null : KeystoreType.valueOf(keystoreType))
                .truststoreFilename(getAbsolutePath(nifiProperties.getProperty("nifi.security.truststore")))
                .truststorePassword(nifiProperties.getProperty("nifi.security.truststorePasswd"))
                .truststoreType(truststoreType == null ? null : KeystoreType.valueOf(truststoreType))
                .build();

            return new JerseyNiFiClient.Builder()
                .config(clientConfig)
                .build();
        }

        private String getAbsolutePath(final String filename) {
            if (filename == null) {
                return null;
            }

            final File file = new File(filename);
            if (file.isAbsolute()) {
                return file.getAbsolutePath();
            }

            return new File(instanceDirectory, file.getPath()).getAbsolutePath();
        }
    }
}
