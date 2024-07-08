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
package org.apache.nifi.flow.encryptor.command;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FlowEncryptorCommandTest {
    private static final String TEMP_FILE_PREFIX = SetSensitivePropertiesKeyTest.class.getSimpleName();

    private static final String FLOW_CONTENTS_JSON = "{\"property\":\"value\"}";

    private static final String JSON_GZ = ".json.gz";

    private static final String PROPERTIES_EXTENSION = ".properties";

    private static final String BLANK_PROPERTIES = "/blank.nifi.properties";

    private static final String POPULATED_PROPERTIES = "/populated.nifi.properties";

    private static final String REQUESTED_ALGORITHM = "NIFI_PBKDF2_AES_GCM_256";

    @AfterEach
    public void clearProperties() {
        System.clearProperty(FlowEncryptorCommand.PROPERTIES_FILE_PATH);
    }

    @Test
    public void testRunSystemPropertyNotDefined() {
        final FlowEncryptorCommand command = new FlowEncryptorCommand();
        assertThrows(IllegalStateException.class, command::run);
    }

    @Test
    public void testRunPropertiesKeyBlankProperties() throws IOException, URISyntaxException {
        final Path propertiesPath = getBlankNiFiProperties();
        System.setProperty(FlowEncryptorCommand.PROPERTIES_FILE_PATH, propertiesPath.toString());

        final FlowEncryptorCommand command = new FlowEncryptorCommand();

        final String propertiesKey = UUID.randomUUID().toString();
        command.setRequestedPropertiesKey(propertiesKey);
        command.run();

        assertPropertiesKeyUpdated(propertiesPath, propertiesKey);
    }

    @Test
    public void testRunPropertiesAlgorithmWithPropertiesKeyPopulatedProperties() throws IOException, URISyntaxException {
        final Path propertiesPath = getPopulatedNiFiProperties();
        System.setProperty(FlowEncryptorCommand.PROPERTIES_FILE_PATH, propertiesPath.toString());

        final FlowEncryptorCommand command = new FlowEncryptorCommand();

        final String propertiesKey = UUID.randomUUID().toString();

        command.setRequestedPropertiesAlgorithm(REQUESTED_ALGORITHM);
        command.setRequestedPropertiesKey(propertiesKey);
        command.run();

        assertPropertiesKeyUpdated(propertiesPath, propertiesKey);
        assertPropertiesAlgorithmUpdated(propertiesPath, REQUESTED_ALGORITHM);
    }

    protected static void assertPropertiesAlgorithmUpdated(final Path propertiesPath, final String sensitivePropertiesAlgorithm) throws IOException {
        final Optional<String> keyProperty = Files.readAllLines(propertiesPath)
                .stream()
                .filter(line -> line.startsWith(FlowEncryptorCommand.PROPS_ALGORITHM))
                .findFirst();
        assertTrue(keyProperty.isPresent(), "Sensitive Algorithm Property not found");

        final String expectedProperty = String.format("%s=%s", FlowEncryptorCommand.PROPS_ALGORITHM, sensitivePropertiesAlgorithm);
        assertEquals(expectedProperty, keyProperty.get(), "Sensitive Algorithm Property not updated");
    }

    protected static void assertPropertiesKeyUpdated(final Path propertiesPath, final String sensitivePropertiesKey) throws IOException {
        final Optional<String> keyProperty = Files.readAllLines(propertiesPath)
                .stream()
                .filter(line -> line.startsWith(FlowEncryptorCommand.PROPS_KEY))
                .findFirst();
        assertTrue(keyProperty.isPresent(), "Sensitive Key Property not found");

        final String expectedProperty = String.format("%s=%s", FlowEncryptorCommand.PROPS_KEY, sensitivePropertiesKey);
        assertEquals(expectedProperty, keyProperty.get(), "Sensitive Key Property not updated");
    }

    protected static Path getBlankNiFiProperties() throws IOException, URISyntaxException {
        final Path flowConfigurationJson = getFlowConfiguration(FLOW_CONTENTS_JSON, JSON_GZ);
        return getNiFiProperties(flowConfigurationJson, BLANK_PROPERTIES);
    }

    protected static Path getPopulatedNiFiProperties() throws IOException, URISyntaxException {
        final Path flowConfigurationJson = getFlowConfiguration(FLOW_CONTENTS_JSON, JSON_GZ);
        return getNiFiProperties(flowConfigurationJson, POPULATED_PROPERTIES);
    }

    private static Path getNiFiProperties(
            final Path flowConfigurationJsonPath,
            String propertiesResource
    ) throws IOException, URISyntaxException {
        final Path sourcePropertiesPath = Paths.get(getResourceUrl(propertiesResource).toURI());
        final List<String> sourceProperties = Files.readAllLines(sourcePropertiesPath);
        final List<String> flowProperties = sourceProperties.stream().map(line -> {
            if (line.startsWith(FlowEncryptorCommand.CONFIGURATION_FILE)) {
                return flowConfigurationJsonPath == null ? line : line + flowConfigurationJsonPath;
            } else {
                return line;
            }
        }).collect(Collectors.toList());

        final Path propertiesPath = Files.createTempFile(TEMP_FILE_PREFIX, PROPERTIES_EXTENSION);
        propertiesPath.toFile().deleteOnExit();
        Files.write(propertiesPath, flowProperties);
        return propertiesPath;
    }

    private static URL getResourceUrl(String resource) throws FileNotFoundException {
        final URL resourceUrl = FlowEncryptorCommand.class.getResource(resource);
        if (resourceUrl == null) {
            throw new FileNotFoundException(String.format("Resource [%s] not found", resource));
        }
        return resourceUrl;
    }

    private static Path getFlowConfiguration(final String contents, final String extension) throws IOException {
        final Path flowConfigurationPath = Files.createTempFile(TEMP_FILE_PREFIX, extension);
        final File flowConfigurationFile = flowConfigurationPath.toFile();
        flowConfigurationFile.deleteOnExit();

        try (final GZIPOutputStream outputStream = new GZIPOutputStream(new FileOutputStream(flowConfigurationFile))) {
            outputStream.write(contents.getBytes(StandardCharsets.UTF_8));
        }
        return flowConfigurationPath;
    }
}
