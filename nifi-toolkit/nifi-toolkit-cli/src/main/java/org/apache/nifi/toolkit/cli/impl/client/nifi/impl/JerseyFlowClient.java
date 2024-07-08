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
package org.apache.nifi.toolkit.cli.impl.client.nifi.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.flow.VersionedReportingTaskSnapshot;
import org.apache.nifi.toolkit.cli.impl.client.nifi.FlowClient;
import org.apache.nifi.toolkit.cli.impl.client.nifi.NiFiClientException;
import org.apache.nifi.toolkit.cli.impl.client.nifi.ProcessGroupBox;
import org.apache.nifi.toolkit.cli.impl.client.nifi.RequestConfig;
import org.apache.nifi.web.api.dto.PositionDTO;
import org.apache.nifi.web.api.dto.flow.FlowDTO;
import org.apache.nifi.web.api.dto.flow.ProcessGroupFlowDTO;
import org.apache.nifi.web.api.entity.ActivateControllerServicesEntity;
import org.apache.nifi.web.api.entity.ClusterSummaryEntity;
import org.apache.nifi.web.api.entity.ComponentEntity;
import org.apache.nifi.web.api.entity.ConnectionStatusEntity;
import org.apache.nifi.web.api.entity.ControllerServicesEntity;
import org.apache.nifi.web.api.entity.CurrentUserEntity;
import org.apache.nifi.web.api.entity.ParameterProvidersEntity;
import org.apache.nifi.web.api.entity.ProcessGroupFlowEntity;
import org.apache.nifi.web.api.entity.ProcessGroupStatusEntity;
import org.apache.nifi.web.api.entity.ReportingTasksEntity;
import org.apache.nifi.web.api.entity.ScheduleComponentsEntity;
import org.apache.nifi.web.api.entity.VersionedFlowSnapshotMetadataSetEntity;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Jersey implementation of FlowClient.
 */
public class JerseyFlowClient extends AbstractJerseyClient implements FlowClient {

    static final String ROOT = "root";

    private final WebTarget flowTarget;

    public JerseyFlowClient(final WebTarget baseTarget) {
        this(baseTarget, null);
    }

    public JerseyFlowClient(final WebTarget baseTarget, final RequestConfig requestConfig) {
        super(requestConfig);
        this.flowTarget = baseTarget.path("/flow");
    }

    @Override
    public CurrentUserEntity getCurrentUser() throws NiFiClientException, IOException {
        return executeAction("Error retrieving current", () -> {
            final WebTarget target = flowTarget.path("current-user");
            return getRequestBuilder(target).get(CurrentUserEntity.class);
        });
    }

    @Override
    public String getRootGroupId() throws NiFiClientException, IOException {
        final ProcessGroupFlowEntity entity = getProcessGroup(ROOT);
        return entity.getProcessGroupFlow().getId();
    }

    @Override
    public ProcessGroupFlowEntity getProcessGroup(final String id) throws NiFiClientException, IOException {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("Process group id cannot be null");
        }

        return executeAction("Error retrieving process group flow", () -> {
            final WebTarget target = flowTarget
                    .path("process-groups/{id}")
                    .resolveTemplate("id", id);

            return getRequestBuilder(target).get(ProcessGroupFlowEntity.class);
        });
    }

    @Override
    public ProcessGroupBox getSuggestedProcessGroupCoordinates(final String parentId) throws NiFiClientException, IOException {
        if (StringUtils.isBlank(parentId)) {
            throw new IllegalArgumentException("Process group id cannot be null");
        }

        final ProcessGroupFlowEntity processGroup = getProcessGroup(parentId);
        final ProcessGroupFlowDTO processGroupFlowDTO = processGroup.getProcessGroupFlow();
        final FlowDTO flowDTO = processGroupFlowDTO.getFlow();

        final List<ComponentEntity> pgComponents = new ArrayList<>();
        pgComponents.addAll(flowDTO.getProcessGroups());
        pgComponents.addAll(flowDTO.getProcessors());
        pgComponents.addAll(flowDTO.getRemoteProcessGroups());
        pgComponents.addAll(flowDTO.getConnections());
        pgComponents.addAll(flowDTO.getFunnels());
        pgComponents.addAll(flowDTO.getInputPorts());
        pgComponents.addAll(flowDTO.getOutputPorts());
        pgComponents.addAll(flowDTO.getLabels());

        final Set<PositionDTO> positions = pgComponents.stream()
                .map(ComponentEntity::getPosition)
                .collect(Collectors.toSet());

        if (positions.isEmpty()) {
            return ProcessGroupBox.CANVAS_CENTER;
        }

        final List<ProcessGroupBox> coords = positions.stream()
                .filter(Objects::nonNull)
                .map(p -> new ProcessGroupBox(p.getX().intValue(), p.getY().intValue()))
                .collect(Collectors.toList());

        final ProcessGroupBox freeSpot = coords.get(0).findFreeSpace(coords);
        return freeSpot;
    }

    @Override
    public ScheduleComponentsEntity scheduleProcessGroupComponents(
            final String processGroupId, final ScheduleComponentsEntity scheduleComponentsEntity)
            throws NiFiClientException, IOException {

        if (StringUtils.isBlank(processGroupId)) {
            throw new IllegalArgumentException("Process group id cannot be null");
        }

        if (scheduleComponentsEntity == null) {
            throw new IllegalArgumentException("ScheduleComponentsEntity cannot be null");
        }

        scheduleComponentsEntity.setId(processGroupId);

        return executeAction("Error scheduling components", () -> {
            final WebTarget target = flowTarget
                    .path("process-groups/{id}")
                    .resolveTemplate("id", processGroupId);

            return getRequestBuilder(target).put(
                    Entity.entity(scheduleComponentsEntity, MediaType.APPLICATION_JSON_TYPE),
                    ScheduleComponentsEntity.class);
        });
    }

    @Override
    public VersionedFlowSnapshotMetadataSetEntity getVersions(final String registryId, final String bucketId, final String flowId)
            throws NiFiClientException, IOException {

        if (StringUtils.isBlank(registryId)) {
            throw new IllegalArgumentException("Registry id cannot be null");
        }

        if (StringUtils.isBlank(bucketId)) {
            throw new IllegalArgumentException("Bucket id cannot be null");
        }

        if (StringUtils.isBlank(flowId)) {
            throw new IllegalArgumentException("Flow id cannot be null");
        }

        return executeAction("Error retrieving versions", () -> {
            final WebTarget target = flowTarget
                    .path("registries/{registry-id}/buckets/{bucket-id}/flows/{flow-id}/versions")
                    .resolveTemplate("registry-id", registryId)
                    .resolveTemplate("bucket-id", bucketId)
                    .resolveTemplate("flow-id", flowId);

            return getRequestBuilder(target).get(VersionedFlowSnapshotMetadataSetEntity.class);
        });
    }

    @Override
    public ControllerServicesEntity getControllerServices(final String groupId) throws NiFiClientException, IOException {
        if (StringUtils.isBlank(groupId)) {
            throw new IllegalArgumentException("Group Id cannot be null or blank");
        }

        return executeAction("Error retrieving controller services", () -> {
            final WebTarget target = flowTarget
                    .path("process-groups/{id}/controller-services")
                    .resolveTemplate("id", groupId);

            return getRequestBuilder(target).get(ControllerServicesEntity.class);
        });
    }

    @Override
    public ActivateControllerServicesEntity activateControllerServices(final ActivateControllerServicesEntity activateControllerServicesEntity)
            throws NiFiClientException, IOException {

        if (activateControllerServicesEntity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }

        if (StringUtils.isBlank(activateControllerServicesEntity.getId())) {
            throw new IllegalArgumentException("Entity must contain a process group id");
        }

        return executeAction("Error enabling or disabling controlling services", () -> {
            final WebTarget target = flowTarget
                    .path("process-groups/{id}/controller-services")
                    .resolveTemplate("id", activateControllerServicesEntity.getId());

            return getRequestBuilder(target).put(
                    Entity.entity(activateControllerServicesEntity, MediaType.APPLICATION_JSON_TYPE),
                    ActivateControllerServicesEntity.class);
        });
    }

    @Override
    public ClusterSummaryEntity getClusterSummary() throws NiFiClientException, IOException {
        return executeAction("Error retrieving cluster summary", () -> {
            final WebTarget target = flowTarget.path("cluster/summary");
            return getRequestBuilder(target).get(ClusterSummaryEntity.class);
        });
    }

    @Override
    public ControllerServicesEntity getControllerServices() throws NiFiClientException, IOException {
        return executeAction("Error retrieving reporting task/flow analysis rule controller services", () -> {
            final WebTarget target = flowTarget.path("controller/controller-services");
            return getRequestBuilder(target).get(ControllerServicesEntity.class);
        });
    }

    @Override
    public ReportingTasksEntity getReportingTasks() throws NiFiClientException, IOException {
        return executeAction("Error retrieving reporting tasks", () -> {
            final WebTarget target = flowTarget.path("reporting-tasks");
            return getRequestBuilder(target).get(ReportingTasksEntity.class);
        });
    }

    @Override
    public VersionedReportingTaskSnapshot getReportingTaskSnapshot() throws NiFiClientException, IOException {
        return executeAction("Error retrieving reporting tasks", () -> {
            final WebTarget target = flowTarget.path("reporting-tasks/snapshot");
            return getRequestBuilder(target).get(VersionedReportingTaskSnapshot.class);
        });
    }

    @Override
    public VersionedReportingTaskSnapshot getReportingTaskSnapshot(final String reportingTaskId) throws NiFiClientException, IOException {
        return executeAction("Error retrieving reporting task", () -> {
            final WebTarget target = flowTarget.path("reporting-tasks/snapshot")
                    .queryParam("reportingTaskId", reportingTaskId);
            return getRequestBuilder(target).get(VersionedReportingTaskSnapshot.class);
        });
    }

    @Override
    public ParameterProvidersEntity getParamProviders() throws NiFiClientException, IOException {
        return executeAction("Error retrieving parameter providers", () -> {
            final WebTarget target = flowTarget.path("parameter-providers");
            return getRequestBuilder(target).get(ParameterProvidersEntity.class);
        });
    }

    @Override
    public ConnectionStatusEntity getConnectionStatus(final String connectionId, final boolean nodewise) throws NiFiClientException, IOException {
        return executeAction("Error retrieving Connection status", () -> {
            final WebTarget target = flowTarget.path("/connections/{connectionId}/status")
                .resolveTemplate("connectionId", connectionId)
                .queryParam("nodewise", nodewise);

            return getRequestBuilder(target).get(ConnectionStatusEntity.class);
        });
    }

    @Override
    public ProcessGroupStatusEntity getProcessGroupStatus(final String groupId, final boolean recursive) throws NiFiClientException, IOException {
        return executeAction("Error retrieving ProcessGroup status", () -> {
            final WebTarget target = flowTarget.path("/process-groups/{groupId}/status")
                .resolveTemplate("groupId", groupId)
                .queryParam("recursive", recursive);

            return getRequestBuilder(target).get(ProcessGroupStatusEntity.class);
        });
    }
}
