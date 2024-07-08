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
package org.apache.nifi.diagnostics;

import org.apache.nifi.controller.repository.ContentRepository;
import org.apache.nifi.controller.repository.FlowFileRepository;
import org.apache.nifi.controller.repository.claim.ResourceClaim;
import org.apache.nifi.controller.repository.claim.ResourceClaimManager;
import org.apache.nifi.provenance.ProvenanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A factory for creating system diagnostics.
 *
 */
public class SystemDiagnosticsFactory {

    private final Logger logger = LoggerFactory.getLogger(SystemDiagnosticsFactory.class);

    public SystemDiagnostics create(final FlowFileRepository flowFileRepo, final ContentRepository contentRepo, final ProvenanceRepository provenanceRepository,
                                    final ResourceClaimManager resourceClaimManager) {
        final SystemDiagnostics systemDiagnostics = new SystemDiagnostics();

        final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        final MemoryUsage heap = memory.getHeapMemoryUsage();
        final MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        final OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        final List<GarbageCollectorMXBean> garbageCollectors = ManagementFactory.getGarbageCollectorMXBeans();
        final RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();

        systemDiagnostics.setDaemonThreads(threads.getDaemonThreadCount());
        systemDiagnostics.setTotalThreads(threads.getThreadCount());

        systemDiagnostics.setTotalHeap(heap.getCommitted());
        systemDiagnostics.setUsedHeap(heap.getUsed());
        systemDiagnostics.setMaxHeap(heap.getMax());

        systemDiagnostics.setTotalNonHeap(nonHeap.getCommitted());
        systemDiagnostics.setUsedNonHeap(nonHeap.getUsed());
        systemDiagnostics.setMaxNonHeap(nonHeap.getMax());

        systemDiagnostics.setUptime(runtime.getUptime());

        systemDiagnostics.setAvailableProcessors(os.getAvailableProcessors());

        final double systemLoad = os.getSystemLoadAverage();
        if (systemLoad >= 0) {
            systemDiagnostics.setProcessorLoadAverage(systemLoad);
        } else {
            systemDiagnostics.setProcessorLoadAverage(-1.0);
        }

        // get the database disk usage
        final StorageUsage flowFileRepoStorageUsage = new StorageUsage();
        flowFileRepoStorageUsage.setIdentifier("FlowFile Repository");
        try {
            flowFileRepoStorageUsage.setFreeSpace(flowFileRepo.getUsableStorageSpace());
            flowFileRepoStorageUsage.setTotalSpace(flowFileRepo.getStorageCapacity());
        } catch (final IOException ioe) {
            flowFileRepoStorageUsage.setFreeSpace(0L);
            flowFileRepoStorageUsage.setTotalSpace(-1L);

            logger.warn("Unable to determine FlowFile Repository usage", ioe);
        }
        systemDiagnostics.setFlowFileRepositoryStorageUsage(flowFileRepoStorageUsage);

        // get the file repository disk usage
        final Set<String> containerNames = contentRepo.getContainerNames();
        final Map<String, StorageUsage> fileRepositoryUsage = new LinkedHashMap<>(containerNames.size());
        for (final String containerName : containerNames) {
            long containerCapacity = -1L;
            long containerFree = 0L;

            try {
                containerFree = contentRepo.getContainerUsableSpace(containerName);
                containerCapacity = contentRepo.getContainerCapacity(containerName);
            } catch (final IOException ioe) {
                logger.warn("Unable to determine Content Repository usage for container {}", containerName, ioe);
            }

            final StorageUsage storageUsage = new StorageUsage();
            storageUsage.setIdentifier(containerName);
            storageUsage.setFreeSpace(containerFree);
            storageUsage.setTotalSpace(containerCapacity);
            fileRepositoryUsage.put(containerName, storageUsage);
        }
        systemDiagnostics.setContentRepositoryStorageUsage(fileRepositoryUsage);

        // get provenance repository disk usage
        final Set<String> provContainerNames = provenanceRepository.getContainerNames();
        final Map<String, StorageUsage> provRepositoryUsage = new LinkedHashMap<>(provContainerNames.size());
        for (final String containerName : provContainerNames) {
            long containerCapacity = -1L;
            long containerFree = 0L;

            try {
                containerFree = provenanceRepository.getContainerUsableSpace(containerName);
                containerCapacity = provenanceRepository.getContainerCapacity(containerName);
            } catch (final IOException ioe) {
                logger.warn("Unable to determine Provenance Repository usage for container {}", containerName, ioe);
            }

            final StorageUsage storageUsage = new StorageUsage();
            storageUsage.setIdentifier(containerName);
            storageUsage.setFreeSpace(containerFree);
            storageUsage.setTotalSpace(containerCapacity);
            provRepositoryUsage.put(containerName, storageUsage);
        }
        systemDiagnostics.setProvenanceRepositoryStorageUsage(provRepositoryUsage);

        // get the garbage collection statistics
        final Map<String, GarbageCollection> garbageCollection = new LinkedHashMap<>(garbageCollectors.size());
        for (final GarbageCollectorMXBean garbageCollector : garbageCollectors) {
            final GarbageCollection garbageCollectionEntry = new GarbageCollection();
            garbageCollectionEntry.setCollectionCount(garbageCollector.getCollectionCount());
            garbageCollectionEntry.setCollectionTime(garbageCollector.getCollectionTime());
            garbageCollection.put(garbageCollector.getName(), garbageCollectionEntry);
        }
        systemDiagnostics.setGarbageCollection(garbageCollection);

        // This information is available only for *nix systems.
        final OperatingSystemMXBean osStats = ManagementFactory.getOperatingSystemMXBean();
        try {
            final Class<?> unixOsMxBeanClass = Class.forName("com.sun.management.UnixOperatingSystemMXBean");
            if (unixOsMxBeanClass.isAssignableFrom(osStats.getClass())) {
                final Method totalPhysicalMemory = unixOsMxBeanClass.getMethod("getTotalPhysicalMemorySize");
                totalPhysicalMemory.setAccessible(true);
                final Long ramBytes = (Long) totalPhysicalMemory.invoke(osStats);
                if (ramBytes != null) {
                    systemDiagnostics.setTotalPhysicalMemory(ramBytes);
                }

                final Method maxFileDescriptors = unixOsMxBeanClass.getMethod("getMaxFileDescriptorCount");
                maxFileDescriptors.setAccessible(true);
                final Long maxOpenFileDescriptors = (Long) maxFileDescriptors.invoke(osStats);
                if (maxOpenFileDescriptors != null) {
                    systemDiagnostics.setMaxOpenFileHandles(maxOpenFileDescriptors);
                }

                final Method openFileDescriptors = unixOsMxBeanClass.getMethod("getOpenFileDescriptorCount");
                openFileDescriptors.setAccessible(true);
                final Long openDescriptorCount = (Long) openFileDescriptors.invoke(osStats);
                if (openDescriptorCount != null) {
                    systemDiagnostics.setOpenFileHandles(openDescriptorCount);
                }
            }
        } catch (final Throwable t) {
            // Ignore. This will throw either ClassNotFound or NoClassDefFoundError if unavailable in this JVM.
        }

        final Map<ResourceClaim, Integer> claimantCounts = new HashMap<>();
        final Set<ResourceClaim> destructableClaims = new HashSet<>();
        for (final String containerName : contentRepo.getContainerNames()) {
            try {
                final Set<ResourceClaim> resourceClaims = contentRepo.getActiveResourceClaims(containerName);
                for (final ResourceClaim resourceClaim : resourceClaims) {
                    final int claimantCount = resourceClaimManager.getClaimantCount(resourceClaim);
                    claimantCounts.put(resourceClaim, claimantCount);

                    if (resourceClaimManager.isDestructable(resourceClaim)) {
                        destructableClaims.add(resourceClaim);
                    }
                }
            } catch (final Exception e) {
                logger.warn("Failed to determine Resource Claim usage for Content Repository Container {}", containerName, e);
            }
        }

        systemDiagnostics.setClaimantCounts(claimantCounts);
        systemDiagnostics.setDestructableClaims(destructableClaims);

        // set the creation timestamp
        systemDiagnostics.setCreationTimestamp(new Date().getTime());

        return systemDiagnostics;
    }

}
