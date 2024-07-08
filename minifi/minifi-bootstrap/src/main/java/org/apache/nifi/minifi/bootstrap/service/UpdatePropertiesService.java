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

package org.apache.nifi.minifi.bootstrap.service;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.apache.nifi.minifi.bootstrap.RunMiNiFi.CONF_DIR_KEY;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import org.apache.nifi.minifi.bootstrap.RunMiNiFi;
import org.apache.nifi.minifi.bootstrap.configuration.ConfigurationChangeException;
import org.apache.nifi.minifi.commons.api.MiNiFiCommandState;
import org.apache.nifi.minifi.properties.BootstrapProperties;
import org.slf4j.Logger;

public class UpdatePropertiesService {
    private final RunMiNiFi runner;
    private final Logger logger;
    private final BootstrapFileProvider bootstrapFileProvider;
    private final MiNiFiPropertiesGenerator miNiFiPropertiesGenerator;

    public UpdatePropertiesService(RunMiNiFi runner, Logger logger, BootstrapFileProvider bootstrapFileProvider) {
        this.runner = runner;
        this.logger = logger;
        this.bootstrapFileProvider = bootstrapFileProvider;
        this.miNiFiPropertiesGenerator = new MiNiFiPropertiesGenerator();
    }

    public Optional<MiNiFiCommandState> handleUpdate() {
        Optional<MiNiFiCommandState> commandState;
        try {
            File bootstrapConfigFile = BootstrapFileProvider.getBootstrapConfFile();

            File bootstrapSwapConfigFile = bootstrapFileProvider.getBootstrapConfSwapFile();
            logger.info("Persisting old bootstrap configuration to {}", bootstrapSwapConfigFile.getAbsolutePath());

            try (FileInputStream configFileInputStream = new FileInputStream(bootstrapConfigFile)) {
                Files.copy(configFileInputStream, bootstrapSwapConfigFile.toPath(), REPLACE_EXISTING);
            }

            Files.copy(bootstrapFileProvider.getBootstrapConfNewFile().toPath(), bootstrapConfigFile.toPath(), REPLACE_EXISTING);

            // already from new
            commandState = generateConfigfilesBasedOnNewProperties(bootstrapConfigFile, bootstrapSwapConfigFile, bootstrapFileProvider.getProtectedBootstrapProperties());
        } catch (Exception e) {
            commandState = Optional.of(MiNiFiCommandState.NOT_APPLIED_WITHOUT_RESTART);
            logger.error("Failed to load new bootstrap properties", e);
        }
        return commandState;
    }

    private Optional<MiNiFiCommandState> generateConfigfilesBasedOnNewProperties(File bootstrapConfigFile, File bootstrapSwapConfigFile, BootstrapProperties bootstrapProperties)
        throws IOException, ConfigurationChangeException {
        Optional<MiNiFiCommandState> commandState = Optional.empty();
        try {
            miNiFiPropertiesGenerator.generateMinifiProperties(bootstrapProperties.getProperty(CONF_DIR_KEY), bootstrapProperties);
            restartInstance();
        } catch (Exception e) {
            commandState = Optional.of(MiNiFiCommandState.NOT_APPLIED_WITHOUT_RESTART);
            // reverting config file
            try (FileInputStream swapConfigFileStream = new FileInputStream(bootstrapSwapConfigFile)) {
                Files.copy(swapConfigFileStream, bootstrapConfigFile.toPath(), REPLACE_EXISTING);
            }
            // read reverted properties
            bootstrapProperties = bootstrapFileProvider.getBootstrapProperties();
            miNiFiPropertiesGenerator.generateMinifiProperties(bootstrapProperties.getProperty(CONF_DIR_KEY), bootstrapProperties);

            logger.debug("Transformation of new config file failed after swap file was created, deleting it.");
            if (!bootstrapSwapConfigFile.delete()) {
                logger.warn("The swap file ({}) failed to delete after a failed handling of a change. It should be cleaned up manually.", bootstrapSwapConfigFile);
            }
        }
        return commandState;
    }

    private void restartInstance() throws IOException {
        try {
            runner.reload();
        } catch (IOException e) {
            throw new IOException("Unable to successfully restart MiNiFi instance after configuration change.", e);
        }
    }
}
