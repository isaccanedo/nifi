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
package org.apache.nifi.cluster.manager;

import org.apache.nifi.cluster.protocol.NodeIdentifier;
import org.apache.nifi.groups.StatelessGroupScheduledState;
import org.apache.nifi.web.api.dto.ProcessGroupDTO;
import org.apache.nifi.web.api.dto.VersionControlInformationDTO;
import org.apache.nifi.web.api.dto.status.ProcessGroupStatusDTO;
import org.apache.nifi.web.api.entity.ParameterContextReferenceEntity;
import org.apache.nifi.web.api.entity.ProcessGroupEntity;

import java.util.HashMap;
import java.util.Map;

public class ProcessGroupEntityMerger implements ComponentEntityMerger<ProcessGroupEntity>, ComponentEntityStatusMerger<ProcessGroupStatusDTO> {

    @Override
    public void merge(ProcessGroupEntity clientEntity, Map<NodeIdentifier, ProcessGroupEntity> entityMap) {
        ComponentEntityMerger.super.merge(clientEntity, entityMap);

        final Map<NodeIdentifier, ProcessGroupDTO> dtoMap = new HashMap<>();
        for (Map.Entry<NodeIdentifier, ProcessGroupEntity> entry : entityMap.entrySet()) {
            final ProcessGroupEntity entity = entry.getValue();
            if (entity != clientEntity) {
                mergeStatus(clientEntity.getStatus(), clientEntity.getPermissions().getCanRead(), entry.getValue().getStatus(), entry.getValue().getPermissions().getCanRead(), entry.getKey());
                mergeVersionControlInformation(clientEntity, entity);
            }

            dtoMap.put(entry.getKey(), entity.getComponent());
        }

        mergeDtos(clientEntity.getComponent(), dtoMap);
    }

    private static void mergeDtos(final ProcessGroupDTO clientDto, final Map<NodeIdentifier, ProcessGroupDTO> dtoMap) {
        // if unauthorized for the client dto, simple return
        if (clientDto == null) {
            return;
        }

        // get the parameter context if configured
        final ParameterContextReferenceEntity clientParameterContextEntity = clientDto.getParameterContext();

        // if this process group is bound to a parameter context, merge the permissions from the other nodes
        if (clientParameterContextEntity != null) {
            for (Map.Entry<NodeIdentifier, ProcessGroupDTO> entry : dtoMap.entrySet()) {
                final ProcessGroupDTO dto = entry.getValue();
                final ParameterContextReferenceEntity parameterContextReferenceEntity = dto.getParameterContext();

                clientDto.setStatelessGroupScheduledState(mergeScheduledState(clientDto.getStatelessGroupScheduledState(), dto.getStatelessGroupScheduledState()));

                PermissionsDtoMerger.mergePermissions(clientParameterContextEntity.getPermissions(), parameterContextReferenceEntity.getPermissions());
            }
        }
    }

    private static String mergeScheduledState(final String stateAName, final String stateBName) {
        final StatelessGroupScheduledState stateA = getScheduledState(stateAName);
        final StatelessGroupScheduledState stateB = getScheduledState(stateBName);

        if (stateA == null && stateB == null) {
            return null;
        }

        if (stateA == null) {
            return stateB.name();
        }
        if (stateB == null) {
            return stateA.name();
        }
        if (stateA == StatelessGroupScheduledState.RUNNING || stateB == StatelessGroupScheduledState.RUNNING) {
            return StatelessGroupScheduledState.RUNNING.name();
        }
        return StatelessGroupScheduledState.STOPPED.name();
    }

    private static StatelessGroupScheduledState getScheduledState(final String value) {
        if (value == null) {
            return null;
        }

        try {
            return StatelessGroupScheduledState.valueOf(value);
        } catch (final Exception e) {
            return null;
        }
    }

    @Override
    public void mergeStatus(ProcessGroupStatusDTO clientStatus, boolean clientStatusReadablePermission, ProcessGroupStatusDTO status, boolean statusReadablePermission,
                            NodeIdentifier statusNodeIdentifier) {
        StatusMerger.merge(clientStatus, clientStatusReadablePermission, status, statusReadablePermission, statusNodeIdentifier.getId(), statusNodeIdentifier.getApiAddress(),
                statusNodeIdentifier.getApiPort());
    }

    private void mergeVersionControlInformation(ProcessGroupEntity targetGroup, ProcessGroupEntity toMerge) {
        final ProcessGroupDTO targetGroupDto = targetGroup.getComponent();
        final ProcessGroupDTO toMergeGroupDto = toMerge.getComponent();
        if (targetGroupDto == null || toMergeGroupDto == null) {
            return;
        }

        final VersionControlInformationDTO targetVersionControl = targetGroupDto.getVersionControlInformation();
        final VersionControlInformationDTO toMergeVersionControl = toMergeGroupDto.getVersionControlInformation();

        if (targetVersionControl == null) {
            targetGroupDto.setVersionControlInformation(toMergeGroupDto.getVersionControlInformation());
        } else if (toMergeVersionControl != null) {
            VersionControlInformationEntityMerger.updateFlowState(targetVersionControl, toMergeVersionControl);
        }
    }
}
