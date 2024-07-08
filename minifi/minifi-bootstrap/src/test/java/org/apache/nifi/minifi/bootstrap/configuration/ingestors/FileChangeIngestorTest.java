/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.nifi.minifi.bootstrap.configuration.ingestors;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static org.apache.nifi.minifi.bootstrap.configuration.ingestors.FileChangeIngestor.DEFAULT_POLLING_PERIOD_INTERVAL;
import static org.apache.nifi.minifi.bootstrap.configuration.ingestors.PullHttpChangeIngestor.PULL_HTTP_BASE_KEY;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.nifi.minifi.bootstrap.ConfigurationFileHolder;
import org.apache.nifi.minifi.bootstrap.configuration.ConfigurationChangeNotifier;
import org.apache.nifi.minifi.bootstrap.configuration.differentiators.Differentiator;
import org.apache.nifi.minifi.commons.api.MiNiFiProperties;
import org.apache.nifi.minifi.properties.BootstrapProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class FileChangeIngestorTest {

    private static final String CONFIG_FILENAME = "config.yml";
    private static final String TEST_CONFIG_PATH = "src/test/resources/config.yml";

    private FileChangeIngestor notifierSpy;
    private WatchService mockWatchService;
    private BootstrapProperties testProperties;
    private Differentiator<ByteBuffer> mockDifferentiator;
    private ConfigurationChangeNotifier testNotifier;


    @BeforeEach
    public void setUp() {
        mockWatchService = mock(WatchService.class);
        notifierSpy = Mockito.spy(new FileChangeIngestor());
        mockDifferentiator = mock(Differentiator.class);
        testNotifier = mock(ConfigurationChangeNotifier.class);
        testProperties = mock(BootstrapProperties.class);

        setMocks();

        when(testProperties.getProperty(FileChangeIngestor.CONFIG_FILE_PATH_KEY)).thenReturn(TEST_CONFIG_PATH);
        when(testProperties.getProperty(PullHttpChangeIngestor.OVERRIDE_SECURITY)).thenReturn("true");
        when(testProperties.getProperty(PULL_HTTP_BASE_KEY + ".override.core")).thenReturn("true");
        when(testProperties.getProperty(eq(FileChangeIngestor.POLLING_PERIOD_INTERVAL_KEY), any())).thenReturn(String.valueOf(DEFAULT_POLLING_PERIOD_INTERVAL));
        when(testProperties.getProperty(MiNiFiProperties.NIFI_MINIFI_FLOW_CONFIG.getKey())).thenReturn(MiNiFiProperties.NIFI_MINIFI_FLOW_CONFIG.getDefaultValue());
    }

    @AfterEach
    public void tearDown() {
        notifierSpy.close();
    }

    @Test
    public void testInitializeInvalidFile() {
        when(testProperties.getProperty(FileChangeIngestor.CONFIG_FILE_PATH_KEY)).thenReturn("/land/of/make/believe");
        assertThrows(IllegalStateException.class, () -> notifierSpy.initialize(testProperties, mock(ConfigurationFileHolder.class), mock(ConfigurationChangeNotifier.class)));
    }

    @Test
    public void testInitializeValidFile() {
        notifierSpy.initialize(testProperties, mock(ConfigurationFileHolder.class), mock(ConfigurationChangeNotifier.class));
    }

    @Test
    public void testInitializeInvalidPollingPeriod() {
        when(testProperties.getProperty(eq(FileChangeIngestor.POLLING_PERIOD_INTERVAL_KEY), any())).thenReturn("abc");
        assertThrows(IllegalStateException.class, () -> notifierSpy.initialize(testProperties, mock(ConfigurationFileHolder.class), mock(ConfigurationChangeNotifier.class)));
    }

    @Test
    public void testInitializeUseDefaultPolling() {
        notifierSpy.initialize(testProperties, mock(ConfigurationFileHolder.class), mock(ConfigurationChangeNotifier.class));
    }

    /* Verify handleChange events */
    @Test
    public void testTargetChangedNoModification() throws Exception {
        when(mockDifferentiator.isNew(Mockito.any(ByteBuffer.class))).thenReturn(false);
        final ConfigurationChangeNotifier testNotifier = mock(ConfigurationChangeNotifier.class);

        // In this case the WatchKey is null because there were no events found
        establishMockEnvironmentForChangeTests(null);

        verify(testNotifier, Mockito.never()).notifyListeners(Mockito.any(ByteBuffer.class));
    }

    @Test
    public void testTargetChangedWithModificationEventNonConfigFile() throws Exception {
        when(mockDifferentiator.isNew(Mockito.any(ByteBuffer.class))).thenReturn(false);
        final ConfigurationChangeNotifier testNotifier = mock(ConfigurationChangeNotifier.class);

        // In this case, we receive a trigger event for the directory monitored, but it was another file not being monitored
        final WatchKey mockWatchKey = createMockWatchKeyForPath("footage_not_found.yml");

        establishMockEnvironmentForChangeTests(mockWatchKey);

        notifierSpy.targetFileChanged();

        verify(testNotifier, Mockito.never()).notifyListeners(Mockito.any(ByteBuffer.class));
    }

    @Test
    public void testTargetChangedWithModificationEvent() throws Exception {
        when(mockDifferentiator.isNew(Mockito.any(ByteBuffer.class))).thenReturn(true);

        final WatchKey mockWatchKey = createMockWatchKeyForPath(CONFIG_FILENAME);
        // Provided as a spy to allow injection of mock objects for some tests when dealing with the finalized FileSystems class
        establishMockEnvironmentForChangeTests(mockWatchKey);

        ConfigurationFileHolder configurationFileHolder = mock(ConfigurationFileHolder.class);
        when(configurationFileHolder.getConfigFileReference()).thenReturn(new AtomicReference<>(ByteBuffer.wrap(new byte[0])));

        notifierSpy.initialize(testProperties, configurationFileHolder, testNotifier);
        setMocks();

        // Invoke the method of interest
        notifierSpy.run();

        verify(mockWatchService, Mockito.atLeastOnce()).poll();
        verify(testNotifier, Mockito.atLeastOnce()).notifyListeners(Mockito.any(ByteBuffer.class));
    }

    /* Helper methods to establish mock environment */
    private WatchKey createMockWatchKeyForPath(String configFilePath) {
        WatchKey mockWatchKey = mock(WatchKey.class);
        WatchEvent mockWatchEvent = mock(WatchEvent.class);

        // In this case, we receive a trigger event for the directory monitored, and it was the file monitored
        when(mockWatchEvent.context()).thenReturn(Paths.get(configFilePath));
        when(mockWatchEvent.kind()).thenReturn(ENTRY_MODIFY);
        when(mockWatchKey.pollEvents()).thenReturn(Collections.singletonList(mockWatchEvent));
        when(mockWatchKey.reset()).thenReturn(true);

        return mockWatchKey;
    }

    private void establishMockEnvironmentForChangeTests(final WatchKey watchKey) {
        // Establish the file mock and its parent directory
        final Path mockConfigFilePath = mock(Path.class);
        final Path mockConfigFileParentPath = mock(Path.class);

        // When getting the parent of the file, get the directory
        when(mockConfigFilePath.getParent()).thenReturn(mockConfigFileParentPath);

        when(mockWatchService.poll()).thenReturn(watchKey);
    }

    private void setMocks() {
        notifierSpy.setConfigFilePath(Paths.get(TEST_CONFIG_PATH));
        notifierSpy.setWatchService(mockWatchService);
        notifierSpy.setDifferentiator(mockDifferentiator);
        notifierSpy.setConfigurationChangeNotifier(testNotifier);
    }
}