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
package org.apache.nifi.controller;

import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.controller.queue.FlowFileQueue;
import org.apache.nifi.controller.queue.QueueSize;
import org.apache.nifi.controller.repository.CaffeineFieldCache;
import org.apache.nifi.controller.repository.FlowFileRecord;
import org.apache.nifi.controller.repository.FlowFileRepository;
import org.apache.nifi.controller.repository.FlowFileSwapManager;
import org.apache.nifi.controller.repository.SwapContents;
import org.apache.nifi.controller.repository.SwapManagerInitializationContext;
import org.apache.nifi.controller.repository.SwapSummary;
import org.apache.nifi.controller.repository.claim.ResourceClaimManager;
import org.apache.nifi.controller.swap.SchemaSwapDeserializer;
import org.apache.nifi.controller.swap.SchemaSwapSerializer;
import org.apache.nifi.controller.swap.SimpleSwapDeserializer;
import org.apache.nifi.controller.swap.StandardSwapContents;
import org.apache.nifi.controller.swap.StandardSwapSummary;
import org.apache.nifi.controller.swap.SwapDeserializer;
import org.apache.nifi.controller.swap.SwapSerializer;
import org.apache.nifi.events.EventReporter;
import org.apache.nifi.reporting.Severity;
import org.apache.nifi.repository.schema.FieldCache;
import org.apache.nifi.stream.io.StreamUtils;
import org.apache.nifi.util.NiFiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>
 * An implementation of the {@link FlowFileSwapManager} that swaps FlowFiles
 * to/from local disk
 * </p>
 */
public class FileSystemSwapManager implements FlowFileSwapManager {

    private static final Pattern SWAP_FILE_PATTERN = Pattern.compile("\\d+-.+?(\\..*?)?\\.swap");
    private static final Pattern TEMP_SWAP_FILE_PATTERN = Pattern.compile("\\d+-.+?(\\..*?)?\\.swap\\.part");
    private static final Pattern UUID_PATTERN = Pattern.compile("([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})");

    public static final String EVENT_CATEGORY = "Swap FlowFiles";
    private static final Logger logger = LoggerFactory.getLogger(FileSystemSwapManager.class);

    private final File storageDirectory;
    private final FieldCache fieldCache = new CaffeineFieldCache(10_000_000);

    // effectively final
    private FlowFileRepository flowFileRepository;
    private EventReporter eventReporter;
    private ResourceClaimManager claimManager;

    private static final byte[] MAGIC_HEADER = {'S', 'W', 'A', 'P'};

    /**
     * Default no args constructor for service loading only.
     */
    public FileSystemSwapManager() {
        storageDirectory = null;
    }

    public FileSystemSwapManager(final NiFiProperties nifiProperties) {
        this(nifiProperties.getFlowFileRepositoryPath());
    }

    public FileSystemSwapManager(final Path flowFileRepoPath) {
        this.storageDirectory = flowFileRepoPath.resolve("swap").toFile();
        if (!storageDirectory.exists() && !storageDirectory.mkdirs()) {
            throw new RuntimeException("Cannot create Swap Storage directory " + storageDirectory.getAbsolutePath());
        }
    }


    @Override
    public synchronized void initialize(final SwapManagerInitializationContext initializationContext) {
        this.claimManager = initializationContext.getResourceClaimManager();
        this.eventReporter = initializationContext.getEventReporter();
        this.flowFileRepository = initializationContext.getFlowFileRepository();
    }

    protected InputStream getInputStream(final File file) throws IOException {
        return new FileInputStream(file);
    }

    protected OutputStream getOutputStream(final File file) throws IOException {
        return new FileOutputStream(file);
    }

    @Override
    public String swapOut(final List<FlowFileRecord> toSwap, final FlowFileQueue flowFileQueue, final String partitionName) throws IOException {
        if (toSwap == null || toSwap.isEmpty()) {
            return null;
        }

        final String swapFileName = getSwapFileName(flowFileQueue.getIdentifier(), partitionName);
        final Path storageDirectoryPath = storageDirectory.toPath();
        final Path swapFilePath = storageDirectoryPath.resolve(swapFileName).toAbsolutePath();

        final File swapFile = swapFilePath.toFile();
        final File swapTempFile = new File(swapFile.getParentFile(), swapFile.getName() + ".part");
        final String swapLocation = swapFile.getAbsolutePath();

        final SwapSerializer serializer = new SchemaSwapSerializer();
        try (final OutputStream os = getOutputStream(swapTempFile);
            final OutputStream out = new BufferedOutputStream(os)) {
            out.write(MAGIC_HEADER);
            final DataOutputStream dos = new DataOutputStream(out);
            dos.writeUTF(serializer.getSerializationName());

            serializer.serializeFlowFiles(toSwap, flowFileQueue, swapLocation, out);
            out.flush();
        } catch (final IOException ioe) {
            // we failed to write out the entire swap file. Delete the temporary file, if we can.
            swapTempFile.delete();
            throw ioe;
        }

        if (swapTempFile.renameTo(swapFile)) {
            flowFileRepository.swapFlowFilesOut(toSwap, flowFileQueue, swapLocation);
        } else {
            error("Failed to swap out FlowFiles from " + flowFileQueue + " due to: Unable to rename swap file from " + swapTempFile + " to " + swapFile);
        }

        return swapLocation;
    }

    @Override
    public SwapContents swapIn(final String swapLocation, final FlowFileQueue flowFileQueue) throws IOException {
        final File swapFile = new File(swapLocation);

        final boolean validLocation = flowFileRepository.isValidSwapLocationSuffix(swapFile.getName());
        if (!validLocation) {
            warn("Cannot swap in FlowFiles from location " + swapLocation + " because the FlowFile Repository does not know about this Swap Location. " +
                "This file should be manually removed. This typically occurs when a Swap File is written but the FlowFile Repository is not updated yet to reflect this. " +
                "This is generally not a cause for concern, but may be indicative of a failure to update the FlowFile Repository.");
            final SwapSummary swapSummary = new StandardSwapSummary(new QueueSize(0, 0), 0L, Collections.emptyList(), 0L, 0L);
            return new StandardSwapContents(swapSummary, Collections.emptyList());
        }

        final SwapContents swapContents = peek(swapLocation, flowFileQueue);
        flowFileRepository.swapFlowFilesIn(swapFile.getAbsolutePath(), swapContents.getFlowFiles(), flowFileQueue);

        if (!swapFile.delete()) {
            warn("Swapped in FlowFiles from file " + swapFile.getAbsolutePath() + " but failed to delete the file; this file should be cleaned up manually");
        }

        return swapContents;
    }

    @Override
    public SwapContents peek(final String swapLocation, final FlowFileQueue flowFileQueue) throws IOException {
        final File swapFile = new File(swapLocation);
        if (!swapFile.exists()) {
            throw new FileNotFoundException("Failed to swap in FlowFiles from external storage location " + swapLocation + " into FlowFile Queue because the file could not be found");
        }

        try (final InputStream is = getInputStream(swapFile);
                final InputStream bis = new BufferedInputStream(is);
                final DataInputStream in = new DataInputStream(bis)) {

            final SwapDeserializer deserializer = createSwapDeserializer(in);
            return deserializer.deserializeFlowFiles(in, swapLocation, flowFileQueue, claimManager);
        }
    }

    @Override
    public void purge() {
        final File[] swapFiles = storageDirectory.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(final File dir, final String name) {
                return SWAP_FILE_PATTERN.matcher(name).matches() || TEMP_SWAP_FILE_PATTERN.matcher(name).matches();
            }
        });

        for (final File file : swapFiles) {
            if (!file.delete()) {
                warn("Failed to delete Swap File " + file + " when purging FlowFile Swap Manager");
            }
        }
    }

    @Override
    public String getQueueIdentifier(final String swapLocation) {
        final String filename = swapLocation.contains("/") ? StringUtils.substringAfterLast(swapLocation, "/") : swapLocation;
        final String[] splits = filename.split("-");
        if (splits.length > 6) {
            final String queueIdentifier = splits[1] + "-" + splits[2] + "-" + splits[3] + "-" + splits[4] + "-" + splits[5];
            return queueIdentifier;
        }

        return null;
    }

    private String getOwnerQueueIdentifier(final File swapFile) {
        return getQueueIdentifier(swapFile.getName());
    }

    private String getOwnerPartition(final File swapFile) {
        final String filename = swapFile.getName();
        final int indexOfDot = filename.indexOf(".");
        if (indexOfDot < 1) {
            return null;
        }

        final int lastIndexOfDot = filename.lastIndexOf(".");
        if (lastIndexOfDot == indexOfDot) {
            return null;
        }

        return filename.substring(indexOfDot + 1, lastIndexOfDot);
    }

    @Override
    public Set<String> getSwappedPartitionNames(final FlowFileQueue queue) {
        final File[] swapFiles = storageDirectory.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(final File dir, final String name) {
                return SWAP_FILE_PATTERN.matcher(name).matches();
            }
        });

        if (swapFiles == null) {
            return Collections.emptySet();
        }

        final String queueId = queue.getIdentifier();

        return Stream.of(swapFiles)
            .filter(swapFile -> queueId.equals(getOwnerQueueIdentifier(swapFile)))
            .map(this::getOwnerPartition)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    @Override
    public List<String> recoverSwapLocations(final FlowFileQueue flowFileQueue, final String partitionName) throws IOException {
        final File[] swapFiles = storageDirectory.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(final File dir, final String name) {
                return SWAP_FILE_PATTERN.matcher(name).matches() || TEMP_SWAP_FILE_PATTERN.matcher(name).matches();
            }
        });

        if (swapFiles == null) {
            return Collections.emptyList();
        }

        final List<String> swapLocations = new ArrayList<>();
        // remove in .part files, as they are partial swap files that did not get written fully.
        for (final File swapFile : swapFiles) {
            if (TEMP_SWAP_FILE_PATTERN.matcher(swapFile.getName()).matches()) {
                if (swapFile.delete()) {
                    logger.info("Removed incomplete/temporary Swap File {}", swapFile);
                } else {
                    warn("Failed to remove incomplete/temporary Swap File " + swapFile + "; this file should be cleaned up manually");
                }

                continue;
            }

            // split the filename by dashes. The old filenaming scheme was "<timestamp>-<randomuuid>.swap" but the new naming scheme is
            // "<timestamp>-<queue identifier>-<random uuid>.[partition name.]swap".
            final String ownerQueueId = getOwnerQueueIdentifier(swapFile);
            if (ownerQueueId != null) {
                if (!ownerQueueId.equals(flowFileQueue.getIdentifier())) {
                    continue;
                }

                if (partitionName != null) {
                    final String ownerPartition = getOwnerPartition(swapFile);
                    if (!partitionName.equals(ownerPartition)) {
                        continue;
                    }
                }

                final boolean validLocation = flowFileRepository.isValidSwapLocationSuffix(swapFile.getName());
                if (!validLocation) {
                    logger.warn("Encountered unknown Swap File {}; will ignore this Swap File. This file should be cleaned up manually", swapFile);
                    continue;
                }

                swapLocations.add(swapFile.getAbsolutePath());
                continue;
            }

            // Read the queue identifier from the swap file to check if the swap file is for this queue
            try (final InputStream fis = getInputStream(swapFile);
                    final InputStream bufferedIn = new BufferedInputStream(fis);
                    final DataInputStream in = new DataInputStream(bufferedIn)) {

                final SwapDeserializer deserializer;
                try {
                    deserializer = createSwapDeserializer(in);
                } catch (final Exception e) {
                    final String errMsg = "Cannot swap FlowFiles in from " + swapFile + " due to " + e;
                    eventReporter.reportEvent(Severity.ERROR, EVENT_CATEGORY, errMsg);
                    throw new IOException(errMsg);
                }

                // If deserializer is not an instance of Simple Swap Deserializer, then it means that the serializer is new enough that
                // we use the 3-element filename as illustrated above, so this is only necessary for the SimpleSwapDeserializer.
                if (deserializer instanceof SimpleSwapDeserializer) {
                    final String connectionId = in.readUTF();
                    if (connectionId.equals(flowFileQueue.getIdentifier())) {
                        swapLocations.add(swapFile.getAbsolutePath());
                    }
                }
            }
        }

        swapLocations.sort(new SwapFileComparator());
        return swapLocations;
    }

    @Override
    public SwapSummary getSwapSummary(final String swapLocation) throws IOException {
        final File swapFile = new File(swapLocation);

        // read record from disk via the swap file
        try (final InputStream fis = getInputStream(swapFile);
                final InputStream bufferedIn = new BufferedInputStream(fis);
                final DataInputStream in = new DataInputStream(bufferedIn)) {

            final SwapDeserializer deserializer = createSwapDeserializer(in);
            return deserializer.getSwapSummary(in, swapLocation, claimManager);
        }
    }


    private SwapDeserializer createSwapDeserializer(final DataInputStream dis) throws IOException {
        dis.mark(MAGIC_HEADER.length);

        final byte[] magicHeader = new byte[MAGIC_HEADER.length];
        try {
            StreamUtils.fillBuffer(dis, magicHeader);
        } catch (final EOFException eof) {
            throw new IOException("Failed to read swap file because the file contained less than 4 bytes of data");
        }

        if (Arrays.equals(magicHeader, MAGIC_HEADER)) {
            final String serializationName = dis.readUTF();
            if (serializationName.equals(SchemaSwapDeserializer.getSerializationName())) {
                return new SchemaSwapDeserializer(fieldCache);
            }

            throw new IOException("Cannot find a suitable Deserializer for swap file, written with Serialization Name '" + serializationName + "'");
        } else {
            // SimpleSwapDeserializer is old and did not write out a magic header.
            dis.reset();
            return new SimpleSwapDeserializer();
        }
    }


    private void error(final String error) {
        logger.error(error);
        if (eventReporter != null) {
            eventReporter.reportEvent(Severity.ERROR, EVENT_CATEGORY, error);
        }
    }

    private void warn(final String warning) {
        logger.warn(warning);
        if (eventReporter != null) {
            eventReporter.reportEvent(Severity.WARNING, EVENT_CATEGORY, warning);
        }
    }

    private static class SwapFileComparator implements Comparator<String> {

        @Override
        public int compare(final String o1, final String o2) {
            if (o1 == o2) {
                return 0;
            }

            final Long time1 = getTimestampFromFilename(o1);
            final Long time2 = getTimestampFromFilename(o2);

            if (time1 == null && time2 == null) {
                return 0;
            }
            if (time1 == null) {
                return 1;
            }
            if (time2 == null) {
                return -1;
            }

            final int timeComparisonValue = time1.compareTo(time2);
            if (timeComparisonValue != 0) {
                return timeComparisonValue;
            }

            return o1.compareTo(o2);
        }

        private Long getTimestampFromFilename(final String fullyQualifiedFilename) {
            if (fullyQualifiedFilename == null) {
                return null;
            }

            final File file = new File(fullyQualifiedFilename);
            final String filename = file.getName();

            final int idx = filename.indexOf("-");
            if (idx < 1) {
                return null;
            }

            final String millisVal = filename.substring(0, idx);
            try {
                return Long.parseLong(millisVal);
            } catch (final NumberFormatException e) {
                return null;
            }
        }
    }

    @Override
    public String changePartitionName(final String swapLocation, final String newPartitionName) throws IOException {
        final File existingFile = new File(swapLocation);
        if (!existingFile.exists()) {
            throw new FileNotFoundException("Could not change name of partition for swap location " + swapLocation + " because no swap file exists at that location");
        }

        final String existingFilename = existingFile.getName();

        final String newFilename;
        final int dotIndex = existingFilename.indexOf(".");
        if (dotIndex < 0) {
            newFilename = existingFilename + "." + newPartitionName + ".swap";
        } else {
            newFilename = existingFilename.substring(0, dotIndex) + "." + newPartitionName + ".swap";
        }

        final File newFile = new File(existingFile.getParentFile(), newFilename);
        // Use Files.move and convert to Path's instead of File.rename so that we get an IOException on failure that describes why we failed.
        Files.move(existingFile.toPath(), newFile.toPath());

        logger.debug("Changed Partition for Swap File by renaming from {} to {}", swapLocation, newPartitionName);
        return newFile.getAbsolutePath();
    }

    private String getSwapFileName(final String flowFileQueueIdentifier, final String partitionName) {
        final UUID identifier;
        final Matcher identifierMatcher = UUID_PATTERN.matcher(flowFileQueueIdentifier);
        if (identifierMatcher.find()) {
            identifier = UUID.fromString(identifierMatcher.group(1));
        } else {
            throw new IllegalArgumentException("FlowFile Queue Identifier [%s] not valid".formatted(flowFileQueueIdentifier));
        }

        final String swapFilePrefix = System.currentTimeMillis() + "-" + identifier + "-" + UUID.randomUUID();
        final String swapFileBaseName = partitionName == null ? swapFilePrefix : swapFilePrefix + "." + partitionName;
        return swapFileBaseName + ".swap";
    }
}
