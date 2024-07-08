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
package org.apache.nifi.persistence;

import org.apache.nifi.util.NiFiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestFlowConfigurationArchiveManager {

    private final File flowXmlFile = new File("./target/flow-archive/flow.json.gz");
    private final File archiveDir = new File("./target/flow-archive");

    @BeforeEach
    public void before() throws Exception {

        // Clean up old files.
        if (Files.isDirectory(archiveDir.toPath())) {
            Files.walkFileTree(archiveDir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        // Create original flow.xml.gz
        Files.createDirectories(flowXmlFile.getParentFile().toPath());
        try (OutputStream os = Files.newOutputStream(flowXmlFile.toPath(),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            // 10 bytes.
            os.write("0123456789".getBytes());
        }

    }

    private Object getPrivateFieldValue(final FlowConfigurationArchiveManager archiveManager, final String fieldName)
            throws NoSuchFieldException, IllegalAccessException {
        final Field field = FlowConfigurationArchiveManager.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(archiveManager);
    }

    @Test
    public void testNiFiPropertiesDefault() throws Exception {

        final NiFiProperties defaultProperties = mock(NiFiProperties.class);
        when(defaultProperties.getFlowConfigurationFile()).thenReturn(flowXmlFile);
        when(defaultProperties.getFlowConfigurationArchiveMaxCount()).thenReturn(null);
        when(defaultProperties.getFlowConfigurationArchiveMaxTime()).thenReturn(null);
        when(defaultProperties.getFlowConfigurationArchiveMaxStorage()).thenReturn(null);

        final FlowConfigurationArchiveManager archiveManager = new FlowConfigurationArchiveManager(defaultProperties);

        assertNull(getPrivateFieldValue(archiveManager, "maxCount"));
        assertEquals(60L * 60L * 24L * 30L * 1000L, getPrivateFieldValue(archiveManager, "maxTimeMillis"));
        assertEquals(500L * 1024L * 1024L, getPrivateFieldValue(archiveManager, "maxStorageBytes"));
    }

    @Test
    public void testNiFiPropertiesMaxTime() throws Exception {

        final NiFiProperties withMaxTime = mock(NiFiProperties.class);
        when(withMaxTime.getFlowConfigurationFile()).thenReturn(flowXmlFile);
        when(withMaxTime.getFlowConfigurationArchiveMaxCount()).thenReturn(null);
        when(withMaxTime.getFlowConfigurationArchiveMaxTime()).thenReturn("10 days");
        when(withMaxTime.getFlowConfigurationArchiveMaxStorage()).thenReturn(null);

        final FlowConfigurationArchiveManager archiveManager = new FlowConfigurationArchiveManager(withMaxTime);

        assertNull(getPrivateFieldValue(archiveManager, "maxCount"));
        assertEquals(60L * 60L * 24L * 10L * 1000L, getPrivateFieldValue(archiveManager, "maxTimeMillis"));
        assertNull(getPrivateFieldValue(archiveManager, "maxStorageBytes"));
    }

    @Test
    public void testNiFiPropertiesMaxStorage() throws Exception {

        final NiFiProperties withMaxTime = mock(NiFiProperties.class);
        when(withMaxTime.getFlowConfigurationFile()).thenReturn(flowXmlFile);
        when(withMaxTime.getFlowConfigurationArchiveMaxCount()).thenReturn(null);
        when(withMaxTime.getFlowConfigurationArchiveMaxTime()).thenReturn(null);
        when(withMaxTime.getFlowConfigurationArchiveMaxStorage()).thenReturn("10 MB");

        final FlowConfigurationArchiveManager archiveManager = new FlowConfigurationArchiveManager(withMaxTime);

        assertNull(getPrivateFieldValue(archiveManager, "maxCount"));
        assertNull(getPrivateFieldValue(archiveManager, "maxTimeMillis"));
        assertEquals(10L * 1024L * 1024L, getPrivateFieldValue(archiveManager, "maxStorageBytes"));
    }

    @Test
    public void testNiFiPropertiesCount() throws Exception {

        final NiFiProperties onlyCount = mock(NiFiProperties.class);
        when(onlyCount.getFlowConfigurationFile()).thenReturn(flowXmlFile);
        when(onlyCount.getFlowConfigurationArchiveMaxCount()).thenReturn(10);
        when(onlyCount.getFlowConfigurationArchiveMaxTime()).thenReturn(null);
        when(onlyCount.getFlowConfigurationArchiveMaxStorage()).thenReturn(null);

        final FlowConfigurationArchiveManager archiveManager = new FlowConfigurationArchiveManager(onlyCount);

        assertEquals(10, getPrivateFieldValue(archiveManager, "maxCount"));
        assertNull(getPrivateFieldValue(archiveManager, "maxTimeMillis"));
        assertNull(getPrivateFieldValue(archiveManager, "maxStorageBytes"));
    }

    @Test
    public void testArchiveWithoutOriginalFile() {
        final NiFiProperties properties = mock(NiFiProperties.class);
        when(properties.getFlowConfigurationArchiveDir()).thenReturn(archiveDir.getPath());
        when(properties.getFlowConfigurationFile()).thenReturn(flowXmlFile);


        final File flowXmlFile = new File("does-not-exist");
        final FlowConfigurationArchiveManager archiveManager = new FlowConfigurationArchiveManager(properties);

        assertThrows(NoSuchFileException.class, () -> archiveManager.archive(flowXmlFile));
    }

    private void createSimulatedOldArchives(final File[] oldArchives, final long intervalMillis) throws Exception {

        // Create old archive files. Altering file name and last modified date to simulate existing files.
        final long now = System.currentTimeMillis();
        final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("HHmmss");

        final FlowConfigurationArchiveManager archiveManager = createArchiveManager(null, null, null);

        for (int i = oldArchives.length; i > 0; i--) {
            final Date date = new Date(now - (intervalMillis * i));
            final String hhmmss = dateFormat.format(date.toInstant().atZone(ZoneId.systemDefault()));

            final File archiveFile = archiveManager.archive(flowXmlFile);
            final String renamedArchiveName = archiveFile.getName().replaceFirst("T[\\d]{6}", "T" + hhmmss);
            final File renamedArchive = archiveFile.getParentFile().toPath().resolve(renamedArchiveName).toFile();
            archiveFile.renameTo(renamedArchive);

            Files.setLastModifiedTime(renamedArchive.toPath(), FileTime.fromMillis(date.getTime()));

            oldArchives[oldArchives.length - i] = renamedArchive;
        }
    }

    private FlowConfigurationArchiveManager createArchiveManager(final Integer maxCount, final String maxTime, final String maxStorage) {
        final NiFiProperties properties = mock(NiFiProperties.class);
        when(properties.getFlowConfigurationArchiveDir()).thenReturn(archiveDir.getPath());
        when(properties.getFlowConfigurationArchiveMaxCount()).thenReturn(maxCount);
        when(properties.getFlowConfigurationArchiveMaxTime()).thenReturn(maxTime);
        when(properties.getFlowConfigurationArchiveMaxStorage()).thenReturn(maxStorage);
        when(properties.getFlowConfigurationFile()).thenReturn(flowXmlFile);
        return new FlowConfigurationArchiveManager(properties);
    }

    @Test
    public void testArchiveExpiration() throws Exception {

        final long intervalMillis = 60_000;
        File[] oldArchives = new File[5];
        createSimulatedOldArchives(oldArchives, intervalMillis);

        // Now, we will test expiration. There should be following old archives created above:
        // -5 min, -4 min, -3min, -2min, -1min
        final long maxTimeForExpirationTest = intervalMillis * 3 + (intervalMillis / 2);

        final FlowConfigurationArchiveManager archiveManager = createArchiveManager(null, maxTimeForExpirationTest + "ms", null);

        final File archive = archiveManager.archive(flowXmlFile);

        assertFalse(oldArchives[0].exists()); // -5 min
        assertFalse(oldArchives[1].exists()); // -4 min
        assertTrue(oldArchives[2].isFile()); // -3 min
        assertTrue(oldArchives[3].isFile()); // -2 min
        assertTrue(oldArchives[4].isFile()); // -1 min
        assertTrue(archive.exists()); // new archive

        assertTrue(flowXmlFile.isFile(), "Original file should remain intact");
    }

    @Test
    public void testArchiveStorageSizeLimit() throws Exception {

        final long intervalMillis = 60_000;
        File[] oldArchives = new File[5];
        createSimulatedOldArchives(oldArchives, intervalMillis);

        // Now, we will test storage size limit. There should be following old archives created above:
        // -5 min, -4 min, -3min, -2min, -1min, each of those have 10 bytes.
        final FlowConfigurationArchiveManager archiveManager = createArchiveManager(null, null, "20b");

        final File archive = archiveManager.archive(flowXmlFile);

        assertFalse(oldArchives[0].exists()); // -5 min
        assertFalse(oldArchives[1].exists()); // -4 min
        assertFalse(oldArchives[2].exists()); // -3 min
        assertFalse(oldArchives[3].exists()); // -2 min
        assertTrue(oldArchives[4].exists()); // -1 min
        assertTrue(archive.exists()); // new archive

        assertTrue(flowXmlFile.isFile(), "Original file should remain intact");
    }

    @Test
    public void testArchiveStorageCountLimit() throws Exception {

        final long intervalMillis = 60_000;
        File[] oldArchives = new File[5];
        createSimulatedOldArchives(oldArchives, intervalMillis);

        // Now, we will test count limit. There should be following old archives created above:
        // -5 min, -4 min, -3min, -2min, -1min, each of those have 10 bytes.
        final FlowConfigurationArchiveManager archiveManager = createArchiveManager(2, null, null);

        final File archive = archiveManager.archive(flowXmlFile);

        assertFalse(oldArchives[0].exists()); // -5 min
        assertFalse(oldArchives[1].exists()); // -4 min
        assertFalse(oldArchives[2].exists()); // -3 min
        assertFalse(oldArchives[3].exists()); // -2 min
        assertTrue(oldArchives[4].exists()); // -1 min
        assertTrue(archive.exists()); // new archive

        assertTrue(flowXmlFile.isFile(), "Original file should remain intact");
    }

    @Test
    public void testLargeConfigFile() throws Exception {
        final long intervalMillis = 60_000;
        File[] oldArchives = new File[5];
        createSimulatedOldArchives(oldArchives, intervalMillis);

        // Now, we will test storage size limit. There should be following old archives created above:
        // -5 min, -4 min, -3min, -2min, -1min, each of those have 10 bytes.
        final FlowConfigurationArchiveManager archiveManager = createArchiveManager(null, null, "3b");

        final File archive = archiveManager.archive(flowXmlFile);

        assertFalse(oldArchives[0].exists()); // -5 min
        assertFalse(oldArchives[1].exists()); // -4 min
        assertFalse(oldArchives[2].exists()); // -3 min
        assertFalse(oldArchives[3].exists()); // -2 min
        assertFalse(oldArchives[4].exists()); // -1 min
        assertTrue(archive.exists(), "Even if flow config file is larger than maxStorage file, it can be archived"); // new archive

        assertTrue(flowXmlFile.isFile(), "Original file should remain intact");
    }

}
