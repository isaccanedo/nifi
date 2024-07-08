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

package org.apache.nifi.web.util;

import org.apache.nifi.authorization.user.NiFiUser;
import org.apache.nifi.authorization.user.NiFiUserUtils;
import org.apache.nifi.cluster.coordination.ClusterCoordinator;
import org.apache.nifi.cluster.coordination.http.replication.RequestReplicator;
import org.apache.nifi.cluster.exception.NoClusterCoordinatorException;
import org.apache.nifi.cluster.manager.NodeResponse;
import org.apache.nifi.cluster.protocol.NodeIdentifier;
import org.apache.nifi.controller.ScheduledState;
import org.apache.nifi.controller.service.ControllerServiceState;
import org.apache.nifi.groups.StatelessGroupScheduledState;
import org.apache.nifi.web.NiFiServiceFacade;
import org.apache.nifi.web.Revision;
import org.apache.nifi.web.api.ApplicationResource.ReplicationTarget;
import org.apache.nifi.web.api.dto.AffectedComponentDTO;
import org.apache.nifi.web.api.dto.ControllerServiceDTO;
import org.apache.nifi.web.api.dto.DtoFactory;
import org.apache.nifi.web.api.dto.ProcessorRunStatusDetailsDTO;
import org.apache.nifi.web.api.dto.RevisionDTO;
import org.apache.nifi.web.api.dto.status.ProcessGroupStatusSnapshotDTO;
import org.apache.nifi.web.api.entity.ActivateControllerServicesEntity;
import org.apache.nifi.web.api.entity.AffectedComponentEntity;
import org.apache.nifi.web.api.entity.ComponentEntity;
import org.apache.nifi.web.api.entity.ControllerServiceEntity;
import org.apache.nifi.web.api.entity.ControllerServicesEntity;
import org.apache.nifi.web.api.entity.ProcessGroupEntity;
import org.apache.nifi.web.api.entity.ProcessGroupStatusEntity;
import org.apache.nifi.web.api.entity.ProcessorRunStatusDetailsEntity;
import org.apache.nifi.web.api.entity.ProcessorsRunStatusDetailsEntity;
import org.apache.nifi.web.api.entity.RunStatusDetailsRequestEntity;
import org.apache.nifi.web.api.entity.ScheduleComponentsEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ClusterReplicationComponentLifecycle implements ComponentLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(ClusterReplicationComponentLifecycle.class);

    private ClusterCoordinator clusterCoordinator;
    private RequestReplicator requestReplicator;
    private NiFiServiceFacade serviceFacade;
    private DtoFactory dtoFactory;


    @Override
    public Set<AffectedComponentEntity> scheduleComponents(final URI exampleUri, final String groupId, final Set<AffectedComponentEntity> components,
            final ScheduledState desiredState, final Pause pause, final InvalidComponentAction invalidComponentAction) throws LifecycleManagementException {

        if (components.isEmpty()) {
            logger.debug("No components to schedule for group {} so will not issue request", groupId);
            return Collections.emptySet();
        }

        final Set<String> componentIds = components.stream()
            .map(ComponentEntity::getId)
            .collect(Collectors.toSet());

        final Map<String, AffectedComponentEntity> componentMap = components.stream()
            .collect(Collectors.toMap(AffectedComponentEntity::getId, Function.identity()));

        final Map<String, Revision> componentRevisionMap = getRevisions(groupId, componentIds);
        final Map<String, RevisionDTO> componentRevisionDtoMap = componentRevisionMap.entrySet().stream().collect(
            Collectors.toMap(Map.Entry::getKey, entry -> dtoFactory.createRevisionDTO(entry.getValue())));

        final ScheduleComponentsEntity scheduleProcessorsEntity = new ScheduleComponentsEntity();
        scheduleProcessorsEntity.setComponents(componentRevisionDtoMap);
        scheduleProcessorsEntity.setId(groupId);
        scheduleProcessorsEntity.setState(desiredState.name());

        URI scheduleGroupUri;
        try {
            scheduleGroupUri = new URI(exampleUri.getScheme(), exampleUri.getUserInfo(), exampleUri.getHost(),
                exampleUri.getPort(), "/nifi-api/flow/process-groups/" + groupId, null, exampleUri.getFragment());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        final Map<String, String> headers = new HashMap<>();
        headers.put("content-type", MediaType.APPLICATION_JSON);

        final NiFiUser user = NiFiUserUtils.getNiFiUser();

        // If attempting to run the processors, validation must complete first
        if (desiredState == ScheduledState.RUNNING) {
            try {
                waitForProcessorValidation(user, exampleUri, groupId, componentMap, pause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LifecycleManagementException("Interrupted while waiting for processors to complete validation");
            }
        }

        // Determine whether we should replicate only to the cluster coordinator, or if we should replicate directly to the cluster nodes themselves.
        try {
            final NodeResponse clusterResponse;
            if (getReplicationTarget() == ReplicationTarget.CLUSTER_NODES) {
                clusterResponse = getRequestReplicator().replicate(user, HttpMethod.PUT, scheduleGroupUri, scheduleProcessorsEntity, headers).awaitMergedResponse();
            } else {
                clusterResponse = getRequestReplicator().forwardToCoordinator(
                    getClusterCoordinatorNode(), user, HttpMethod.PUT, scheduleGroupUri, scheduleProcessorsEntity, headers).awaitMergedResponse();
            }

            final int scheduleComponentStatus = clusterResponse.getStatus();
            if (scheduleComponentStatus != Status.OK.getStatusCode()) {
                final String explanation = getResponseEntity(clusterResponse, String.class);
                throw new LifecycleManagementException("Failed to transition components to a state of " + desiredState + " due to " + explanation);
            }

            // If stopping, wait for the components to complete. If starting, there is no need to wait for them to complete. Some components may fail to start, but waiting will not help.
            if (desiredState == ScheduledState.STOPPED) {
                final boolean processorsTransitioned = waitForProcessorStatus(user, exampleUri, componentMap, desiredState, pause, invalidComponentAction);

                if (!processorsTransitioned) {
                    throw new LifecycleManagementException("Failed while waiting for components to transition to state of " + desiredState);
                }

                final boolean statelessGroupsTransitioned = waitForStatelessGroupStatus(user, exampleUri, componentMap, desiredState, pause);
                if (!statelessGroupsTransitioned) {
                    throw new LifecycleManagementException("Failed while waiting for Stateless Process Groups to transition to state of " + desiredState);
                }
            }
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new LifecycleManagementException("Interrupted while attempting to transition components to state of " + desiredState);
        }

        final Set<AffectedComponentEntity> updatedEntities = components.stream()
            .map(component -> AffectedComponentUtils.updateEntity(component, serviceFacade, dtoFactory))
            .collect(Collectors.toSet());
        return updatedEntities;
    }


    private ReplicationTarget getReplicationTarget() {
        return clusterCoordinator.isActiveClusterCoordinator() ? ReplicationTarget.CLUSTER_NODES : ReplicationTarget.CLUSTER_COORDINATOR;
    }

    private RequestReplicator getRequestReplicator() {
        return requestReplicator;
    }

    protected NodeIdentifier getClusterCoordinatorNode() {
        final NodeIdentifier activeClusterCoordinator = clusterCoordinator.getElectedActiveCoordinatorNode();
        if (activeClusterCoordinator != null) {
            return activeClusterCoordinator;
        }

        throw new NoClusterCoordinatorException();
    }

    private Map<String, Revision> getRevisions(final String groupId, final Set<String> componentIds) {
        final Set<Revision> processorRevisions = serviceFacade.getRevisionsFromGroup(groupId, group -> componentIds);
        return processorRevisions.stream().collect(Collectors.toMap(Revision::getComponentId, Function.identity()));
    }

    private boolean waitForProcessorValidation(final NiFiUser user, final URI originalUri, final String groupId,
                                               final Map<String, AffectedComponentEntity> processors, final Pause pause) throws InterruptedException {

        if (processors.isEmpty()) {
            logger.debug("No processors to wait for so will not wait for Processor Validation");
            return true;
        }

        URI groupUri;
        try {
            groupUri = new URI(originalUri.getScheme(), originalUri.getUserInfo(), originalUri.getHost(),
                    originalUri.getPort(), "/nifi-api/processors/run-status-details/queries", null, originalUri.getFragment());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        final Map<String, String> headers = new HashMap<>();
        final RunStatusDetailsRequestEntity requestEntity = new RunStatusDetailsRequestEntity();
        final Set<String> processorIds = processors.values().stream()
            .map(AffectedComponentEntity::getId)
            .collect(Collectors.toSet());
        requestEntity.setProcessorIds(processorIds);

        boolean continuePolling = true;
        while (continuePolling) {

            // Determine whether we should replicate only to the cluster coordinator, or if we should replicate directly to the cluster nodes themselves.
            final NodeResponse clusterResponse;
            if (getReplicationTarget() == ReplicationTarget.CLUSTER_NODES) {
                clusterResponse = getRequestReplicator().replicate(user, HttpMethod.POST, groupUri, requestEntity, headers).awaitMergedResponse();
            } else {
                clusterResponse = getRequestReplicator().forwardToCoordinator(
                        getClusterCoordinatorNode(), user, HttpMethod.POST, groupUri, requestEntity, headers).awaitMergedResponse();
            }

            if (clusterResponse.getStatus() != Status.OK.getStatusCode()) {
                return false;
            }

            final ProcessorsRunStatusDetailsEntity runStatusDetailsEntity = getResponseEntity(clusterResponse, ProcessorsRunStatusDetailsEntity.class);

            if (isProcessorValidationComplete(runStatusDetailsEntity, processors)) {
                logger.debug("All {} processors of interest now have been validated: {}", processors.size(), processorIds);
                return true;
            }

            // Not all of the processors are done validating. Pause for a bit and poll again.
            continuePolling = pause.pause();
        }

        return false;
    }

    private boolean isProcessorValidationComplete(final ProcessorsRunStatusDetailsEntity runStatusDetailsEntity, final Map<String, AffectedComponentEntity> affectedComponents) {
        updateAffectedProcessors(runStatusDetailsEntity.getRunStatusDetails(), affectedComponents);

        for (final ProcessorRunStatusDetailsEntity statusDetailsEntity : runStatusDetailsEntity.getRunStatusDetails()) {
            final ProcessorRunStatusDetailsDTO runStatusDetails = statusDetailsEntity.getRunStatusDetails();

            logger.debug("Processor {} now has Run Status of {}", runStatusDetails.getId(), runStatusDetails.getRunStatus());
            if (!affectedComponents.containsKey(runStatusDetails.getId())) {
                continue;
            }

            if (ProcessorRunStatusDetailsDTO.VALIDATING.equals(runStatusDetails.getRunStatus())) {
                return false;
            }
        }
        return true;
    }

    private boolean waitForStatelessGroupStatus(final NiFiUser user, final URI originalUri, final Map<String, AffectedComponentEntity> affectedComponents,
                                                final ScheduledState desiredState, final Pause pause) throws InterruptedException {

        final List<AffectedComponentEntity> affectedStatelessGroups = affectedComponents.values().stream()
            .filter(component -> AffectedComponentDTO.COMPONENT_TYPE_STATELESS_GROUP.equals(component.getReferenceType()))
            .collect(Collectors.toList());

        if (affectedStatelessGroups.isEmpty()) {
            logger.debug("There are no Stateless Group in the set of affected components so considering all groups to have reached state of {}", desiredState);
            return true;
        }

        boolean continuePolling = true;
        while (continuePolling) {
            boolean statesReached = true;
            for (final AffectedComponentEntity affectedComponent : affectedStatelessGroups) {
                final boolean stateReached = isStatelessGroupStateReached(originalUri, affectedComponent.getId(), user, desiredState);
                if (stateReached) {
                    logger.debug("Stateless Group with ID {} has reached desired state of {}", affectedComponent.getId(), desiredState);
                } else {
                    statesReached = false;
                    break;
                }
            }

            if (statesReached) {
                logger.info("All {} Stateless Groups have reached the desired state of {}", affectedStatelessGroups.size(), desiredState);
                return true;
            }

            logger.debug("Not all Stateless Groups have reached the desired state of {}", desiredState);
            continuePolling = pause.pause();
        }

        logger.info("After waiting the maximum amount of time, not all Stateless Groups have reached the desired state of {}", desiredState);
        return false;
    }

    private boolean isStatelessGroupStateReached(final URI originalUri, final String groupId, final NiFiUser user, final ScheduledState desiredState) throws InterruptedException {
        final URI groupUri;
        try {
            groupUri = new URI(originalUri.getScheme(), originalUri.getUserInfo(), originalUri.getHost(), originalUri.getPort(),
                "/nifi-api/process-groups/" + groupId, null, originalUri.getFragment());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        final Map<String, String> headers = new HashMap<>();
        final NodeResponse clusterResponse;
        if (getReplicationTarget() == ReplicationTarget.CLUSTER_NODES) {
            clusterResponse = getRequestReplicator().replicate(user, HttpMethod.GET, groupUri, Collections.emptyMap(), headers).awaitMergedResponse();
        } else {
            clusterResponse = getRequestReplicator().forwardToCoordinator(
                getClusterCoordinatorNode(), user, HttpMethod.GET, groupUri, Collections.emptyMap(), headers).awaitMergedResponse();
        }

        if (clusterResponse.getStatus() != Status.OK.getStatusCode()) {
            logger.warn("While waiting for Stateless Groups to transition to a state of {}, received unexpected HTTP status code {}", desiredState, clusterResponse.getStatus());
            return false;
        }

        final ProcessGroupEntity groupEntity = getResponseEntity(clusterResponse, ProcessGroupEntity.class);

        if (desiredState == ScheduledState.RUNNING) {
            final Integer stoppedCount = groupEntity.getStoppedCount();
            if (stoppedCount != null && stoppedCount > 0) {
                return false;
            }

            return true;
        } else {
            // We want to be stopped so wait until we have no components running, a scheduled state of STOPPED, and no active threads.
            final Integer runningCount = groupEntity.getRunningCount();
            if (runningCount != null && runningCount > 0) {
                return false;
            }

            final String statelessState = groupEntity.getComponent().getStatelessGroupScheduledState();
            if (!StatelessGroupScheduledState.STOPPED.name().equals(statelessState)) {
                return false;
            }

            final int activeThreadCount = getStatelessGroupActiveThreadCount(originalUri, groupId, user);
            logger.debug("Stateless Group with ID {} currently has an active thread count of {}", groupId, activeThreadCount);
            return activeThreadCount == 0;
        }
    }

    private int getStatelessGroupActiveThreadCount(final URI originalUri, final String groupId, final NiFiUser user) throws InterruptedException {
        final URI groupUri;
        try {
            groupUri = new URI(originalUri.getScheme(), originalUri.getUserInfo(), originalUri.getHost(), originalUri.getPort(),
                "/nifi-api/flow/process-groups/" + groupId + "/status", null, originalUri.getFragment());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        final NodeResponse clusterResponse;
        if (getReplicationTarget() == ReplicationTarget.CLUSTER_NODES) {
            clusterResponse = getRequestReplicator().replicate(user, HttpMethod.GET, groupUri, Collections.emptyMap(), Collections.emptyMap()).awaitMergedResponse();
        } else {
            clusterResponse = getRequestReplicator().forwardToCoordinator(
                getClusterCoordinatorNode(), user, HttpMethod.GET, groupUri, Collections.emptyMap(), Collections.emptyMap()).awaitMergedResponse();
        }

        if (clusterResponse.getStatus() != Status.OK.getStatusCode()) {
            logger.warn("While waiting for Stateless Groups to transition to a state of STOPPED, received unexpected HTTP status code {} when checking active thread count",
                clusterResponse.getStatus());
            return 1; // Return 1 to indicate that the group is still active
        }

        final ProcessGroupStatusEntity groupStatus = getResponseEntity(clusterResponse, ProcessGroupStatusEntity.class);
        final ProcessGroupStatusSnapshotDTO statusSnapshot = groupStatus.getProcessGroupStatus().getAggregateSnapshot();
        return statusSnapshot.getActiveThreadCount();
    }

    /**
     * Periodically polls the process group with the given ID, waiting for all processors whose ID's are given to have the given Scheduled State.
     *
     * @param user the user making the request
     * @param originalUri the original uri
     * @param processors the Processors whose state should be equal to the given desired state
     * @param desiredState the desired state for all processors with the ID's given
     * @param pause the Pause that can be used to wait between polling
     * @param invalidComponentAction indicates how to handle the condition of a Processor being invalid
     * @return <code>true</code> if successful, <code>false</code> if unable to wait for processors to reach the desired state
     */
    private boolean waitForProcessorStatus(final NiFiUser user, final URI originalUri, final Map<String, AffectedComponentEntity> processors,
                final ScheduledState desiredState, final Pause pause, final InvalidComponentAction invalidComponentAction) throws InterruptedException, LifecycleManagementException {

        if (processors.isEmpty()) {
            logger.debug("No processors to wait for, so will not wait for processors to reach state of {}", desiredState);
            return true;
        }

        final URI groupUri;
        try {
            groupUri = new URI(originalUri.getScheme(), originalUri.getUserInfo(), originalUri.getHost(), originalUri.getPort(),
                "/nifi-api/processors/run-status-details/queries", null, originalUri.getFragment());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        final Map<String, String> headers = new HashMap<>();

        final Set<String> processorIds = processors.values().stream()
            .filter(component -> AffectedComponentDTO.COMPONENT_TYPE_PROCESSOR.equals(component.getReferenceType()))
            .map(AffectedComponentEntity::getId)
            .collect(Collectors.toSet());

        final RunStatusDetailsRequestEntity requestEntity = new RunStatusDetailsRequestEntity();
        requestEntity.setProcessorIds(processorIds);

        boolean continuePolling = true;
        while (continuePolling) {

            // Determine whether we should replicate only to the cluster coordinator, or if we should replicate directly to the cluster nodes themselves.
            final NodeResponse clusterResponse;
            if (getReplicationTarget() == ReplicationTarget.CLUSTER_NODES) {
                clusterResponse = getRequestReplicator().replicate(user, HttpMethod.POST, groupUri, requestEntity, headers).awaitMergedResponse();
            } else {
                clusterResponse = getRequestReplicator().forwardToCoordinator(
                    getClusterCoordinatorNode(), user, HttpMethod.POST, groupUri, requestEntity, headers).awaitMergedResponse();
            }

            if (clusterResponse.getStatus() != Status.OK.getStatusCode()) {
                logger.warn("While waiting for Processors to transition to a state of {}, received unexpected HTTP status code {}", desiredState, clusterResponse.getStatus());
                return false;
            }

            final ProcessorsRunStatusDetailsEntity runStatusDetailsEntity = getResponseEntity(clusterResponse, ProcessorsRunStatusDetailsEntity.class);
            if (isProcessorActionComplete(runStatusDetailsEntity, processors, desiredState, invalidComponentAction)) {
                logger.debug("All {} processors of interest now have the desired state of {}", processors.size(), desiredState);
                return true;
            }

            // Not all of the processors are in the desired state. Pause for a bit and poll again.
            continuePolling = pause.pause();
        }

        return false;
    }

    /**
     * Extracts the response entity from the specified node response.
     *
     * @param nodeResponse node response
     * @param clazz class
     * @param <T> type of class
     * @return the response entity
     */
    @SuppressWarnings("unchecked")
    private <T> T getResponseEntity(final NodeResponse nodeResponse, final Class<T> clazz) {
        T entity = (T) nodeResponse.getUpdatedEntity();
        if (entity != null) {
            return entity;
        }

        final Response clientResponse = nodeResponse.getClientResponse();
        if (clientResponse == null) {
            return null;
        }

        return clientResponse.readEntity(clazz);
    }


    private void updateAffectedProcessors(final Collection<ProcessorRunStatusDetailsEntity> runStatusDetailsEntities, final Map<String, AffectedComponentEntity> affectedComponents) {
        // update the affected processors
        runStatusDetailsEntities.stream()
            .filter(entity -> affectedComponents.containsKey(entity.getRunStatusDetails().getId()))
            .forEach(entity -> {
                final AffectedComponentEntity affectedComponentEntity = affectedComponents.get(entity.getRunStatusDetails().getId());
                affectedComponentEntity.setRevision(entity.getRevision());

                final ProcessorRunStatusDetailsDTO runStatusDetailsDto = entity.getRunStatusDetails();

                // only consider update this component if the user had permissions to it
                if (Boolean.TRUE.equals(affectedComponentEntity.getPermissions().getCanRead())) {
                    final AffectedComponentDTO affectedComponent = affectedComponentEntity.getComponent();
                    affectedComponent.setState(runStatusDetailsDto.getRunStatus());
                    affectedComponent.setActiveThreadCount(runStatusDetailsDto.getActiveThreadCount());

                    if (Boolean.TRUE.equals(entity.getPermissions().getCanRead())) {
                        affectedComponent.setValidationErrors(runStatusDetailsDto.getValidationErrors());
                    }
                }
            });
    }


    private boolean isProcessorActionComplete(final ProcessorsRunStatusDetailsEntity runStatusDetailsEntity, final Map<String, AffectedComponentEntity> affectedComponents,
                                              final ScheduledState desiredState, final InvalidComponentAction invalidComponentAction) throws LifecycleManagementException {

        updateAffectedProcessors(runStatusDetailsEntity.getRunStatusDetails(), affectedComponents);

        boolean allReachedDesiredState = true;
        for (final ProcessorRunStatusDetailsEntity entity : runStatusDetailsEntity.getRunStatusDetails()) {
            final ProcessorRunStatusDetailsDTO runStatusDetailsDto = entity.getRunStatusDetails();
            if (!affectedComponents.containsKey(runStatusDetailsDto.getId())) {
                continue;
            }

            final boolean desiredStateReached = isDesiredProcessorStateReached(runStatusDetailsDto, desiredState);
            logger.debug("Processor[id={}, name={}] now has a state of {} with {} Active Threads, Validation Errors: {}; desired state = {}; invalid component action: {}; desired state reached = {}",
                runStatusDetailsDto.getId(), runStatusDetailsDto.getName(), runStatusDetailsDto.getRunStatus(), runStatusDetailsDto.getActiveThreadCount(), runStatusDetailsDto.getValidationErrors(),
                desiredState, invalidComponentAction, desiredStateReached);

            if (desiredStateReached) {
                continue;
            }

            // If the desired state is stopped and there are active threads, return false. We don't consider the validation status in this case.
            if (desiredState == ScheduledState.STOPPED && runStatusDetailsDto.getActiveThreadCount() != 0) {
                return false;
            }

            if (ProcessorRunStatusDetailsDTO.INVALID.equalsIgnoreCase(runStatusDetailsDto.getRunStatus())) {
                switch (invalidComponentAction) {
                    case WAIT:
                        break;
                    case SKIP:
                        continue;
                    case FAIL:
                        final String action = desiredState == ScheduledState.RUNNING ? "start" : "stop";
                        throw new LifecycleManagementException("Could not " + action + " " + runStatusDetailsDto.getName() + " because it is invalid");
                }
            }

            allReachedDesiredState = false;
        }

        if (allReachedDesiredState) {
            logger.debug("All {} Processors of interest now have the desired state of {}", runStatusDetailsEntity.getRunStatusDetails().size(), desiredState);
            return true;
        }

        return false;
    }

    private boolean isDesiredProcessorStateReached(final ProcessorRunStatusDetailsDTO runStatusDetailsDto, final ScheduledState desiredState) {
        final String runStatus = runStatusDetailsDto.getRunStatus();
        final boolean stateMatches = desiredState.name().equalsIgnoreCase(runStatus);

        if (!stateMatches) {
            return false;
        }

        if (desiredState == ScheduledState.STOPPED && runStatusDetailsDto.getActiveThreadCount() != 0) {
            return false;
        }

        return true;
    }


    @Override
    public Set<AffectedComponentEntity> activateControllerServices(final URI originalUri, final String groupId, final Set<AffectedComponentEntity> affectedServices,
                                                                   final Set<AffectedComponentEntity> servicesRequiringDesiredState, final ControllerServiceState desiredState,
                                                                   final Pause pause, final InvalidComponentAction invalidComponentAction) throws LifecycleManagementException {

        if (affectedServices.isEmpty() && servicesRequiringDesiredState.isEmpty()) {
            logger.debug("No Controller Services to activate for group {} so will not issue request", groupId);
            return Collections.emptySet();
        }

        final Set<String> affectedServiceIds = affectedServices.stream()
            .map(ComponentEntity::getId)
            .collect(Collectors.toSet());

        final Set<String> idsOfServicesRequiringDesiredState = servicesRequiringDesiredState.stream()
            .map(ComponentEntity::getId)
            .collect(Collectors.toSet());

        final Map<String, Revision> serviceRevisionMap = getRevisions(groupId, affectedServiceIds);
        final Map<String, RevisionDTO> serviceRevisionDtoMap = serviceRevisionMap.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> dtoFactory.createRevisionDTO(entry.getValue())));

        final ActivateControllerServicesEntity activateServicesEntity = new ActivateControllerServicesEntity();
        activateServicesEntity.setComponents(serviceRevisionDtoMap);
        activateServicesEntity.setId(groupId);
        activateServicesEntity.setState(desiredState.name());

        URI controllerServicesUri;
        try {
            controllerServicesUri = new URI(originalUri.getScheme(), originalUri.getUserInfo(), originalUri.getHost(),
                originalUri.getPort(), "/nifi-api/flow/process-groups/" + groupId + "/controller-services", null, originalUri.getFragment());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        final Map<String, String> headers = new HashMap<>();
        headers.put("content-type", MediaType.APPLICATION_JSON);

        final NiFiUser user = NiFiUserUtils.getNiFiUser();

        // If enabling services, validation must complete first
        if (desiredState == ControllerServiceState.ENABLED) {
            try {
                waitForControllerServiceValidation(user, originalUri, groupId, affectedServiceIds, pause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LifecycleManagementException("Interrupted while waiting for Controller Services to complete validation");
            }
        }

        // Determine whether we should replicate only to the cluster coordinator, or if we should replicate directly to the cluster nodes themselves.
        try {
            final NodeResponse clusterResponse;
            if (getReplicationTarget() == ReplicationTarget.CLUSTER_NODES) {
                clusterResponse = getRequestReplicator().replicate(user, HttpMethod.PUT, controllerServicesUri, activateServicesEntity, headers).awaitMergedResponse();
            } else {
                clusterResponse = getRequestReplicator().forwardToCoordinator(
                    getClusterCoordinatorNode(), user, HttpMethod.PUT, controllerServicesUri, activateServicesEntity, headers).awaitMergedResponse();
            }

            final int disableServicesStatus = clusterResponse.getStatus();
            if (disableServicesStatus != Status.OK.getStatusCode()) {
                final String explanation = getResponseEntity(clusterResponse, String.class);
                throw new LifecycleManagementException("Failed to update Controller Services to a state of " + desiredState + " due to " + explanation);
            }

            final boolean serviceTransitioned = waitForControllerServiceStatus(user, originalUri, groupId, idsOfServicesRequiringDesiredState, desiredState, pause, invalidComponentAction);

            if (!serviceTransitioned) {
                throw new LifecycleManagementException("Failed while waiting for Controller Services to finish transitioning to a state of " + desiredState);
            }
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new LifecycleManagementException("Interrupted while transitioning Controller Services to a state of " + desiredState);
        }

        return affectedServices.stream()
            .map(componentEntity -> serviceFacade.getControllerService(componentEntity.getId(), false))
            .map(dtoFactory::createAffectedComponentEntity)
            .collect(Collectors.toSet());
    }

    private boolean waitForControllerServiceValidation(final NiFiUser user, final URI originalUri, final String groupId,
                                                       final Set<String> serviceIds, final Pause pause)
            throws InterruptedException {

        URI groupUri;
        try {
            groupUri = new URI(originalUri.getScheme(), originalUri.getUserInfo(), originalUri.getHost(),
                    originalUri.getPort(), "/nifi-api/flow/process-groups/" + groupId + "/controller-services",
                "includeAncestorGroups=false&includeDescendantGroups=true&includeReferencingComponents=false", originalUri.getFragment());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        final Map<String, String> headers = new HashMap<>();
        final MultivaluedMap<String, String> requestEntity = new MultivaluedHashMap<>();

        boolean continuePolling = true;
        while (continuePolling) {

            // Determine whether we should replicate only to the cluster coordinator, or if we should replicate directly to the cluster nodes themselves.
            final NodeResponse clusterResponse;
            if (getReplicationTarget() == ReplicationTarget.CLUSTER_NODES) {
                clusterResponse = getRequestReplicator().replicate(user, HttpMethod.GET, groupUri, requestEntity, headers).awaitMergedResponse();
            } else {
                clusterResponse = getRequestReplicator().forwardToCoordinator(
                        getClusterCoordinatorNode(), user, HttpMethod.GET, groupUri, requestEntity, headers).awaitMergedResponse();
            }

            if (clusterResponse.getStatus() != Status.OK.getStatusCode()) {
                return false;
            }

            final ControllerServicesEntity controllerServicesEntity = getResponseEntity(clusterResponse, ControllerServicesEntity.class);
            final Set<ControllerServiceEntity> serviceEntities = controllerServicesEntity.getControllerServices();

            final Map<String, AffectedComponentEntity> affectedServices = serviceEntities.stream()
                    .filter(s -> serviceIds.contains(s.getId()))
                    .collect(Collectors.toMap(ControllerServiceEntity::getId, dtoFactory::createAffectedComponentEntity));

            if (isControllerServiceValidationComplete(serviceEntities, affectedServices)) {
                logger.debug("All {} controller services of interest have completed validation", affectedServices.size());
                return true;
            }
            continuePolling = pause.pause();
        }

        return false;
    }

    private boolean isControllerServiceValidationComplete(final Set<ControllerServiceEntity> controllerServiceEntities, final Map<String, AffectedComponentEntity> affectedComponents) {
        updateAffectedControllerServices(controllerServiceEntities, affectedComponents);
        for (final ControllerServiceEntity entity : controllerServiceEntities) {
            if (!affectedComponents.containsKey(entity.getId())) {
                continue;
            }

            if (ControllerServiceDTO.VALIDATING.equals(entity.getComponent().getValidationStatus())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Periodically polls the process group with the given ID, waiting for all controller services whose ID's are given to have the given Controller Service State.
     *
     * @param user the user making the request
     * @param groupId the ID of the Process Group to poll
     * @param serviceIds the ID of all Controller Services whose state should be equal to the given desired state
     * @param desiredState the desired state for all services with the ID's given
     * @param pause the Pause that can be used to wait between polling
     * @return <code>true</code> if successful, <code>false</code> if unable to wait for services to reach the desired state
     */
    private boolean waitForControllerServiceStatus(final NiFiUser user, final URI originalUri, final String groupId, final Set<String> serviceIds,
                                                   final ControllerServiceState desiredState, final Pause pause, final InvalidComponentAction invalidComponentAction)
                            throws InterruptedException, LifecycleManagementException {

        if (serviceIds.isEmpty()) {
            return true;
        }

        URI groupUri;
        try {
            groupUri = new URI(originalUri.getScheme(), originalUri.getUserInfo(), originalUri.getHost(),
                originalUri.getPort(), "/nifi-api/flow/process-groups/" + groupId + "/controller-services",
                "includeAncestorGroups=false&includeDescendantGroups=true&includeReferencingComponents=false", originalUri.getFragment());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        final Map<String, String> headers = new HashMap<>();
        final MultivaluedMap<String, String> requestEntity = new MultivaluedHashMap<>();

        boolean continuePolling = true;
        while (continuePolling) {

            // Determine whether we should replicate only to the cluster coordinator, or if we should replicate directly to the cluster nodes themselves.
            final NodeResponse clusterResponse;
            if (getReplicationTarget() == ReplicationTarget.CLUSTER_NODES) {
                clusterResponse = getRequestReplicator().replicate(user, HttpMethod.GET, groupUri, requestEntity, headers).awaitMergedResponse();
            } else {
                clusterResponse = getRequestReplicator().forwardToCoordinator(
                    getClusterCoordinatorNode(), user, HttpMethod.GET, groupUri, requestEntity, headers).awaitMergedResponse();
            }

            if (clusterResponse.getStatus() != Status.OK.getStatusCode()) {
                return false;
            }

            final ControllerServicesEntity controllerServicesEntity = getResponseEntity(clusterResponse, ControllerServicesEntity.class);
            final Set<ControllerServiceEntity> serviceEntities = controllerServicesEntity.getControllerServices();

            final Map<String, AffectedComponentEntity> affectedServices = serviceEntities.stream()
                .collect(Collectors.toMap(ControllerServiceEntity::getId, dtoFactory::createAffectedComponentEntity));

            // update the affected controller services
            updateAffectedControllerServices(serviceEntities, affectedServices);

            final String desiredStateName = desiredState.name();
            boolean allReachedDesiredState = true;
            for (final ControllerServiceEntity serviceEntity : serviceEntities) {
                final ControllerServiceDTO serviceDto = serviceEntity.getComponent();
                if (serviceDto == null) {
                    continue;
                }

                if (!serviceIds.contains(serviceDto.getId())) {
                    continue;
                }

                final String validationStatus = serviceDto.getValidationStatus();
                final boolean desiredStateReached = desiredStateName.equals(serviceDto.getState());

                logger.debug("ControllerService[id={}, name={}] now has a state of {} with a Validation Status of {}; desired state = {}; invalid component action is {}; desired state reached = {}",
                    serviceDto.getId(), serviceDto.getName(), serviceDto.getState(), validationStatus, desiredState, invalidComponentAction, desiredStateReached);

                if (desiredStateReached) {
                    continue;
                }

                // The desired state for this component has not yet been reached. Check how we should handle this based on the validation status.
                if (ControllerServiceDTO.INVALID.equalsIgnoreCase(validationStatus)) {
                    switch (invalidComponentAction) {
                        case WAIT:
                            break;
                        case SKIP:
                            continue;
                        case FAIL:
                            final String action = desiredState == ControllerServiceState.ENABLED ? "enable" : "disable";
                            throw new LifecycleManagementException("Could not " + action + " " + serviceEntity.getComponent().getName() + " because it is invalid");
                    }
                }

                allReachedDesiredState = false;
            }

            if (allReachedDesiredState) {
                logger.debug("All {} controller services of interest now have the desired state of {}", affectedServices.size(), desiredState);
                return true;
            }

            // Not all of the controller services are in the desired state. Pause for a bit and poll again.
            continuePolling = pause.pause();
        }

        return false;
    }


    /**
     * Updates the affected controller services in the specified updateRequest with the serviceEntities.
     *
     * @param serviceEntities service entities
     * @param affectedServices affected services
     */
    private void updateAffectedControllerServices(final Set<ControllerServiceEntity> serviceEntities, final Map<String, AffectedComponentEntity> affectedServices) {
        // update the affected components
        serviceEntities.stream()
            .filter(entity -> affectedServices.containsKey(entity.getId()))
            .forEach(entity -> {
                final AffectedComponentEntity affectedComponentEntity = affectedServices.get(entity.getId());
                affectedComponentEntity.setRevision(entity.getRevision());

                // only consider update this component if the user had permissions to it
                if (Boolean.TRUE.equals(affectedComponentEntity.getPermissions().getCanRead())) {
                    final AffectedComponentDTO affectedComponent = affectedComponentEntity.getComponent();
                    affectedComponent.setState(entity.getComponent().getState());

                    if (Boolean.TRUE.equals(entity.getPermissions().getCanRead())) {
                        affectedComponent.setValidationErrors(entity.getComponent().getValidationErrors());
                    }
                }
            });
    }

    public void setClusterCoordinator(final ClusterCoordinator clusterCoordinator) {
        this.clusterCoordinator = clusterCoordinator;
    }

    public void setRequestReplicator(final RequestReplicator requestReplicator) {
        this.requestReplicator = requestReplicator;
    }

    public void setDtoFactory(final DtoFactory dtoFactory) {
        this.dtoFactory = dtoFactory;
    }

    public void setServiceFacade(final NiFiServiceFacade serviceFacade) {
        this.serviceFacade = serviceFacade;
    }
}
