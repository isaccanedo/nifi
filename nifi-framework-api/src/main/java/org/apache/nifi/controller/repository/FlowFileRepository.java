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
package org.apache.nifi.controller.repository;

import org.apache.nifi.controller.queue.FlowFileQueue;
import org.apache.nifi.controller.repository.claim.ResourceClaim;
import org.apache.nifi.controller.repository.claim.ResourceClaimManager;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementations must be thread safe
 *
 */
public interface FlowFileRepository extends Closeable {

    /**
     * Initializes the Content Repository, providing to it the
     * ContentClaimManager that is to be used for interacting with Content
     * Claims
     *
     * @param claimManager for handling claims
     * @throws java.io.IOException if unable to initialize repository
     */
    void initialize(ResourceClaimManager claimManager) throws IOException;

    /**
     * @return the maximum number of bytes that can be stored in the underlying
     * storage mechanism
     *
     * @throws IOException if computing capacity fails
     */
    long getStorageCapacity() throws IOException;

    /**
     * @return the number of bytes currently available for use by the underlying
     * storage mechanism
     *
     * @throws IOException if computing usable space fails
     */
    long getUsableStorageSpace() throws IOException;

    /**
     * Returns the name of the FileStore that the repository is stored on, or <code>null</code>
     * if not applicable or unable to determine the file store name
     *
     * @return the name of the FileStore
     */
    String getFileStoreName();

    /**
     * Updates the repository with the given RepositoryRecords.
     *
     * @param records the records to update the repository with
     * @throws java.io.IOException if update fails
     */
    void updateRepository(Collection<RepositoryRecord> records) throws IOException;

    /**
     * Loads all flow files found within the repository, establishes the content
     * claims and their reference count
     *
     * @param queueProvider the provider of FlowFile Queues into which the
     * FlowFiles should be enqueued
     *
     * @return index of highest flow file identifier
     * @throws IOException if load fails
     */
    long loadFlowFiles(QueueProvider queueProvider) throws IOException;

    /**
     * Searches through the repository to find the ID's of all FlowFile Queues that currently have data queued
     * @return the set of all FlowFileQueue identifiers for which a FlowFile is queued
     * @throws IOException if unable to read from the FlowFile Repository
     */
    Set<String> findQueuesWithFlowFiles(FlowFileSwapManager flowFileSwapManager) throws IOException;

    /**
     * @return <code>true</code> if the Repository is volatile (i.e., its data
     * is lost upon application restart), <code>false</code> otherwise
     */
    boolean isVolatile();

    /**
     * @return the next ID in sequence for creating <code>FlowFile</code>s.
     */
    long getNextFlowFileSequence();

    /**
     * @return the max ID of all <code>FlowFile</code>s that currently exist in
     * the repository.
     * @throws IOException if computing max identifier fails
     */
    long getMaxFlowFileIdentifier() throws IOException;

    /**
     * Notifies the FlowFile Repository that the given identifier has been identified as the maximum value that
     * has been encountered for an 'external' (swapped out) FlowFile.
     * @param maxId the max id of any FlowFile encountered
     */
    void updateMaxFlowFileIdentifier(long maxId);

    /**
     * Updates the Repository to indicate that the given FlowFileRecords were
     * Swapped Out of memory
     *
     * @param swappedOut the FlowFiles that were swapped out of memory
     * @param flowFileQueue the queue that the FlowFiles belong to
     * @param swapLocation the location to which the FlowFiles were swapped
     *
     * @throws IOException if swap fails
     */
    void swapFlowFilesOut(List<FlowFileRecord> swappedOut, FlowFileQueue flowFileQueue, String swapLocation) throws IOException;

    /**
     * Updates the Repository to indicate that the given FlowFileRecords were
     * Swapped In to memory
     *
     * @param swapLocation the location (e.g., a filename) from which FlowFiles
     * were recovered
     * @param flowFileRecords the records that were swapped in
     * @param flowFileQueue the queue that the FlowFiles belong to
     *
     * @throws IOException if swap fails
     */
    void swapFlowFilesIn(String swapLocation, List<FlowFileRecord> flowFileRecords, FlowFileQueue flowFileQueue) throws IOException;

    /**
     * <p>
     * Determines whether or not the given swap location suffix is a valid, known location according to this FlowFileRepository. Note that while
     * the {@link #swapFlowFilesIn(String, List, FlowFileQueue)} and {@link #swapFlowFilesOut(List, FlowFileQueue, String)} methods expect
     * a full "swap location" this method expects only the "suffix" of a swap location. For example, if the location points to a file, this method
     * would expect only the filename, not the full path.
     * </p>
     *
     * <p>
     * This method differs from the others because the other methods want to store the swap location or recover from a given location. However,
     * this method is used to verify that the location is known. If for any reason, NiFi is stopped, its FlowFile Repository relocated to a new
     * location (for example, a different disk partition), and restarted, the swap location would not match if we used the full location. Therefore,
     * by using only the "suffix" (i.e. the filename for a file-based implementation), we can avoid worrying about relocation.
     * </p>
     *
     * @param swapLocationSuffix the suffix of the location to check
     * @return <code>true</code> if the swap location is known and valid, <code>false</code> otherwise
     */
    boolean isValidSwapLocationSuffix(String swapLocationSuffix);

    /**
     * <p>
     * Scans the FlowFile Repository to locate any FlowFiles that reference the given Resource Claims. If the FlowFile Repository does not implement this capability, it will return <code>null</code>.
     * </p>
     *
     * @param resourceClaims the resource claims whose references should be found
     * @param swapManager the swap manager to use for scanning swap files
     * @return a Mapping of Resource Claim to a representation of the FlowFiles/Swap Files that reference those Resource Claims
     * @throws IOException if an IO failure occurs when attempting to find references
     */
    default Map<ResourceClaim, Set<ResourceClaimReference>> findResourceClaimReferences(Set<ResourceClaim> resourceClaims, FlowFileSwapManager swapManager)
        throws IOException {
        return null;
    }

    /**
     * Returns the set of Resource Claims that are referenced by FlowFiles that have been "orphaned" because they belong to FlowFile Queues/Connections
     * that did not exist in the flow when NiFi started
     * @return the set of orphaned Resource Claims
     */
    default Set<ResourceClaim> findOrphanedResourceClaims() {
        return Collections.emptySet();
    }
}
