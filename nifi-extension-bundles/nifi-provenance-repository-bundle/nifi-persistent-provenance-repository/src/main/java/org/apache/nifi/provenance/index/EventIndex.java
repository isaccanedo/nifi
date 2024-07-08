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

package org.apache.nifi.provenance.index;

import org.apache.nifi.authorization.user.NiFiUser;
import org.apache.nifi.provenance.ProvenanceEventRecord;
import org.apache.nifi.provenance.authorization.EventAuthorizer;
import org.apache.nifi.provenance.lineage.ComputeLineageSubmission;
import org.apache.nifi.provenance.search.Query;
import org.apache.nifi.provenance.search.QuerySubmission;
import org.apache.nifi.provenance.serialization.StorageSummary;
import org.apache.nifi.provenance.store.EventStore;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * An Event Index is responsible for indexing Provenance Events in such a way that the index can be quickly
 * searched to in order to retrieve events of interest.
 */
public interface EventIndex extends Closeable {

    /**
     * Initializes the Event Index, providing it access to the Event Store, in case it is necessary for performing
     * initialization tasks
     *
     * @param eventStore the EventStore that holds the events that have been given to the repository.
     */
    void initialize(EventStore eventStore);

    /**
     * Adds the given events to the index so that they can be queried later.
     *
     * @param events the events to index along with their associated Storage Summaries
     */
    void addEvents(Map<ProvenanceEventRecord, StorageSummary> events);

    /**
     * Indicates whether or not events that are not known to the index should be re-indexed (via {@link #reindexEvents(Map)}}) upon startup.
     *
     * @return <code>true</code> if unknown events should be re-indexed, <code>false</code> otherwise.
     */
    boolean isReindexNecessary();

    /**
     * Replaces the entries in the appropriate index with the given events
     *
     * @param events the events to add or replace along with their associated Storage Summaries
     */
    void reindexEvents(Map<ProvenanceEventRecord, StorageSummary> events);

    /**
     * @return the number of bytes that are utilized by the Event Index
     */
    long getSize();

    /**
     * Submits a Query asynchronously and returns a QuerySubmission that can be used to obtain the results
     *
     * @param query the query to perform
     * @param authorizer the authorizer to use in order to determine whether or not a particular event should be included in the result
     * @param userId the ID of the user on whose behalf the query is being submitted
     *
     * @return a QuerySubmission that can be used to retrieve the results later
     */
    QuerySubmission submitQuery(Query query, EventAuthorizer authorizer, String userId);

    /**
     * Retrieves the most recent Provenance Event that is cached for the given component that is also accessible by the given user
     * @param componentId the ID of the component
     *
     * @return an Optional containing the event, or an empty optional if no events are available or none of the available events are accessible by the given user
     * @throws IOException if unable to read from the repository
     */
    Optional<ProvenanceEventRecord> getLatestCachedEvent(String componentId) throws IOException;

    /**
     * Asynchronously computes the lineage for the FlowFile that is identified by the Provenance Event with the given ID.
     *
     * @param eventId the ID of the Provenance Event for which the lineage should be calculated
     * @param user the NiFi user on whose behalf the computing is being performed
     * @param authorizer the authorizer to use in order to determine whether or not a particular event should be included in the result
     *
     * @return a ComputeLineageSubmission that can be used to retrieve the results later
     */
    ComputeLineageSubmission submitLineageComputation(long eventId, NiFiUser user, EventAuthorizer authorizer);

    /**
     * Asynchronously computes the lineage for the FlowFile that has the given FlowFile UUID.
     *
     * @param flowFileUuid the UUID of the FlowFile for which the lineage should be computed
     * @param user the NiFi user on whose behalf the computing is being performed
     * @param authorizer the authorizer to use in order to determine whether or not a particular event should be included in the result
     *
     * @return a ComputeLineageSubmission that can be used to retrieve the results later
     */
    ComputeLineageSubmission submitLineageComputation(String flowFileUuid, NiFiUser user, EventAuthorizer authorizer);

    /**
     * Asynchronously computes the lineage that makes up the 'child flowfiles' generated by the event with the given ID. This method is
     * valid only for Events that produce 'child flowfiles' such as FORK, CLONE, REPLAY, etc.
     *
     * @param eventId the ID of the Provenance Event for which the lineage should be calculated
     * @param user the NiFi user on whose behalf the computing is being performed
     * @param authorizer the authorizer to use in order to determine whether or not a particular event should be included in the result
     *
     * @return a ComputeLineageSubmission that can be used to retrieve the results later
     */
    ComputeLineageSubmission submitExpandChildren(long eventId, NiFiUser user, EventAuthorizer authorizer);

    /**
     * Asynchronously computes the lineage that makes up the 'parent flowfiles' that were involved in the event with the given ID. This method
     * is valid only for Events that have 'parent flowfiles' such as FORK, JOIN, etc.
     *
     * @param eventId the ID of the Provenance Event for which the lineage should be calculated
     * @param user the NiFi user on whose behalf the computing is being performed
     * @param authorizer the authorizer to use in order to determine whether or not a particular event should be included in the result
     *
     * @return a ComputeLineageSubmission that can be used to retrieve the results later
     */
    ComputeLineageSubmission submitExpandParents(long eventId, NiFiUser user, EventAuthorizer authorizer);

    /**
     * Retrieves the ComputeLineageSubmission that was returned by the 'submitLineageComputation' methods
     *
     * @param lineageIdentifier the identifier of the linage
     * @param user the NiFi user on whose behalf the retrieval is being performed
     * @return the ComputeLineageSubmission that represents the asynchronous lineage computation that is being performed under the given
     *         identifier, or <code>null</code> if the identifier cannot be found.
     */
    ComputeLineageSubmission retrieveLineageSubmission(String lineageIdentifier, NiFiUser user);

    /**
     * Retrieves the QuerySubmission that was returned by the 'submitQuery' method
     *
     * @param queryIdentifier the identifier of the query
     * @param user the NiFi user on whose behalf the retrieval is being performed
     * @return the QuerySubmission that represents the asynchronous query that is being performed under the given
     *         identifier, or <code>null</code> if the identifier cannot be found.
     */
    QuerySubmission retrieveQuerySubmission(String queryIdentifier, NiFiUser user);

    /**
     * Upon restart of NiFi, it is possible that the Event Index will have lost some events due to frequency of committing the index.
     * In such as case, this method is responsible for returning the minimum Provenance Event ID that it knows is safely indexed. If
     * any Provenance Event exists in the Event Store with an ID greater than the value returned, that Event should be re-indexed.
     *
     * @param partitionName the name of the Partition for which the minimum Event ID is desired
     * @return the minimum Provenance Event ID that the Index knows is safely indexed for the given partition
     */
    long getMinimumEventIdToReindex(String partitionName);

    /**
     * Instructs the Event Index to commit any changes that have been made to the partition with the given name
     *
     * @param partitionName the name of the partition to commit changes
     * @throws IOException if unable to commit the changes
     */
    void commitChanges(String partitionName) throws IOException;
}
