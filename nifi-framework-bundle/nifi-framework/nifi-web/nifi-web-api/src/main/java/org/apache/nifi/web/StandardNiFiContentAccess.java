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
package org.apache.nifi.web;

import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.authorization.AccessDeniedException;
import org.apache.nifi.cluster.coordination.ClusterCoordinator;
import org.apache.nifi.cluster.coordination.http.replication.RequestReplicator;
import org.apache.nifi.cluster.exception.NoClusterCoordinatorException;
import org.apache.nifi.cluster.manager.NodeResponse;
import org.apache.nifi.cluster.manager.exception.IllegalClusterStateException;
import org.apache.nifi.cluster.protocol.NodeIdentifier;
import org.apache.nifi.controller.repository.claim.ContentDirection;
import org.apache.nifi.util.NiFiProperties;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.springframework.http.ContentDisposition;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class StandardNiFiContentAccess implements ContentAccess {

    public static final String CLIENT_ID_PARAM = "clientId";

    private static final Pattern FLOWFILE_CONTENT_URI_PATTERN = Pattern
        .compile("/flowfile-queues/([a-f0-9\\-]{36})/flowfiles/([a-f0-9\\-]{36})/content.*");

    private static final Pattern PROVENANCE_CONTENT_URI_PATTERN = Pattern
        .compile("/provenance-events/([0-9]+)/content/((?:input)|(?:output)).*");

    private NiFiProperties properties;
    private NiFiServiceFacade serviceFacade;
    private ClusterCoordinator clusterCoordinator;
    private RequestReplicator requestReplicator;

    @Override
    public DownloadableContent getContent(final ContentRequestContext request) {
        // if clustered, send request to cluster manager
        if (properties.isClustered() && clusterCoordinator != null && clusterCoordinator.isConnected()) {
            // get the URI
            URI dataUri;
            try {
                dataUri = new URI(request.getDataUri());
            } catch (final URISyntaxException use) {
                throw new ClusterRequestException(use);
            }

            // set the request parameters
            final MultivaluedMap<String, String> parameters = new MultivaluedHashMap();
            parameters.add(CLIENT_ID_PARAM, request.getClientId());

            // set the headers
            final Map<String, String> headers = new HashMap<>();

            // ensure we were able to detect the cluster node id
            if (request.getClusterNodeId() == null) {
                throw new IllegalArgumentException("Unable to determine the which node has the content.");
            }

            // get the target node and ensure it exists
            final NodeIdentifier nodeId = clusterCoordinator.getNodeIdentifier(request.getClusterNodeId());

            // replicate the request to the cluster coordinator, indicating the target node
            NodeResponse nodeResponse;
            try {
                headers.put(RequestReplicator.REPLICATION_TARGET_NODE_UUID_HEADER, nodeId.getId());
                final NodeIdentifier coordinatorNode = clusterCoordinator.getElectedActiveCoordinatorNode();
                if (coordinatorNode == null) {
                    throw new NoClusterCoordinatorException();
                }
                final Set<NodeIdentifier> coordinatorNodes = Collections.singleton(coordinatorNode);
                nodeResponse = requestReplicator.replicate(coordinatorNodes, HttpMethod.GET, dataUri, parameters, headers, false, true).awaitMergedResponse();
            } catch (InterruptedException e) {
                throw new IllegalClusterStateException("Interrupted while waiting for a response from node");
            }

            final Response clientResponse = nodeResponse.getClientResponse();
            final MultivaluedMap<String, String> responseHeaders = clientResponse.getStringHeaders();

            // ensure an appropriate response
            if (Response.Status.NOT_FOUND.getStatusCode() == clientResponse.getStatusInfo().getStatusCode()) {
                throw new ResourceNotFoundException(clientResponse.readEntity(String.class));
            } else if (Response.Status.FORBIDDEN.getStatusCode() == clientResponse.getStatusInfo().getStatusCode()
                        || Response.Status.UNAUTHORIZED.getStatusCode() == clientResponse.getStatusInfo().getStatusCode()) {
                throw new AccessDeniedException(clientResponse.readEntity(String.class));
            } else if (Response.Status.OK.getStatusCode() != clientResponse.getStatusInfo().getStatusCode()) {
                throw new IllegalStateException(clientResponse.readEntity(String.class));
            }

            // get the file name
            final String contentDisposition = getHeader(responseHeaders, "content-disposition");
            final ContentDisposition contentDispositionParsed = ContentDisposition.parse(contentDisposition);
            final String filename = contentDispositionParsed.getFilename();

            // get the content type
            final String contentType = getHeader(responseHeaders, "content-type");

            // create the downloadable content
            return new DownloadableContent(filename, contentType, nodeResponse.getInputStream());
        } else {
            // example URIs:
            // http://localhost:8080/nifi-api/provenance/events/{id}/content/{input|output}
            // http://localhost:8080/nifi-api/flowfile-queues/{uuid}/flowfiles/{uuid}/content

            // get just the context path for comparison
            final String dataUri = StringUtils.substringAfter(request.getDataUri(), "/nifi-api");
            if (StringUtils.isBlank(dataUri)) {
                throw new IllegalArgumentException("The specified data reference URI is not valid.");
            }

            // flowfile listing content
            final Matcher flowFileMatcher = FLOWFILE_CONTENT_URI_PATTERN.matcher(dataUri);
            if (flowFileMatcher.matches()) {
                final String connectionId = flowFileMatcher.group(1);
                final String flowfileId = flowFileMatcher.group(2);

                return getFlowFileContent(connectionId, flowfileId, dataUri);
            }

            // provenance event content
            final Matcher provenanceMatcher = PROVENANCE_CONTENT_URI_PATTERN.matcher(dataUri);
            if (provenanceMatcher.matches()) {
                try {
                    final Long eventId = Long.parseLong(provenanceMatcher.group(1));
                    final ContentDirection direction = ContentDirection.valueOf(provenanceMatcher.group(2).toUpperCase());

                    return getProvenanceEventContent(eventId, dataUri, direction);
                } catch (final IllegalArgumentException iae) {
                    throw new IllegalArgumentException("The specified data reference URI is not valid.");
                }
            }

            // invalid uri
            throw new IllegalArgumentException("The specified data reference URI is not valid.");
        }
    }

    /**
     * Returns the value of the first header in the given header map whose name matches the given header name.
     * In HTTP 2.0, all header names should be lower-case. Before that, they were not necessarily. The spec, however,
     * indicates that header names are case-insensitive. That said, the code has them stored in a Map, and the keys in Maps
     * are not case-insensitive. This method allows us to access the value of the first header with the given name,
     * ignoring case.
     *
     * @param headers the map containing all headers
     * @param headerName the case-insensitive name of the header to retrieve
     * @return the value of the first header with the given name, or <code>null</code> if the header is not found
     */
    private String getHeader(final MultivaluedMap<String, String> headers, final String headerName) {
        if (headers == null || headers.isEmpty() || headerName == null || headerName.isBlank()) {
            return null;
        }

        final String exactMatch = headers.getFirst(headerName);
        if (exactMatch != null) {
            return exactMatch;
        }

        for (final Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(headerName)) {
                final List<String> values = entry.getValue();
                return values == null || values.isEmpty() ? null : values.get(0);
            }
        }

        return null;
    }

    private DownloadableContent getFlowFileContent(final String connectionId, final String flowfileId, final String dataUri) {
        // user authorization is handled once we have the actual content so we can utilize the flow file attributes in the resource context
        return serviceFacade.getContent(connectionId, flowfileId, dataUri);
    }

    private DownloadableContent getProvenanceEventContent(final Long eventId, final String dataUri, final ContentDirection direction) {
        // user authorization is handled once we have the actual prov event so we can utilize the event attributes in the resource context
        return serviceFacade.getContent(eventId, dataUri, direction);
    }

    public void setProperties(NiFiProperties properties) {
        this.properties = properties;
    }

    public void setServiceFacade(NiFiServiceFacade serviceFacade) {
        this.serviceFacade = serviceFacade;
    }

    public void setRequestReplicator(RequestReplicator requestReplicator) {
        this.requestReplicator = requestReplicator;
    }

    public void setClusterCoordinator(ClusterCoordinator clusterCoordinator) {
        this.clusterCoordinator = clusterCoordinator;
    }
}
