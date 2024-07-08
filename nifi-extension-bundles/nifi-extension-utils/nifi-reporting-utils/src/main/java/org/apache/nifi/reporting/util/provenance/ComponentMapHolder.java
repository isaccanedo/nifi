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
package org.apache.nifi.reporting.util.provenance;

import org.apache.nifi.controller.status.ConnectionStatus;
import org.apache.nifi.controller.status.PortStatus;
import org.apache.nifi.controller.status.ProcessGroupStatus;
import org.apache.nifi.controller.status.ProcessorStatus;
import org.apache.nifi.controller.status.RemoteProcessGroupStatus;

import java.util.HashMap;
import java.util.Map;

public class ComponentMapHolder {
    private static final String REMOTE_INPUT_PORT = "Remote Input Port";
    private static final String REMOTE_OUTPUT_PORT = "Remote Output Port";
    private final Map<String, String> componentNameMap = new HashMap<>();
    private final Map<String, ParentProcessGroupSearchNode> componentToParentGroupMap = new HashMap<>();
    private final Map<String, String> sourceToConnectionParentGroupMap = new HashMap<>();
    private final Map<String, String> destinationToConnectionParentGroupMap = new HashMap<>();

    private ComponentMapHolder putAll(ComponentMapHolder holder) {
        this.componentNameMap.putAll(holder.componentNameMap);
        this.componentToParentGroupMap.putAll(holder.componentToParentGroupMap);
        this.sourceToConnectionParentGroupMap.putAll(holder.sourceToConnectionParentGroupMap);
        this.destinationToConnectionParentGroupMap.putAll(holder.destinationToConnectionParentGroupMap);
        return this;
    }

    public String getComponentName(final String componentId) {
        return componentNameMap.get(componentId);
    }

    public String getProcessGroupId(final String componentId, final String componentType) {
        // Where a Remote Input/Output Port resides is only available at ConnectionStatus.
        if (REMOTE_INPUT_PORT.equals(componentType)) {
            return destinationToConnectionParentGroupMap.get(componentId);
        } else if (REMOTE_OUTPUT_PORT.equals(componentType)) {
            return sourceToConnectionParentGroupMap.get(componentId);
        }
        ParentProcessGroupSearchNode parentNode = componentToParentGroupMap.get(componentId);
        return parentNode == null ? null : parentNode.getId();
    }

    public ParentProcessGroupSearchNode getProcessGroupParent(final String componentId) {
        return componentToParentGroupMap.get(componentId);
    }

    public static ComponentMapHolder createComponentMap(final ProcessGroupStatus status) {
        return createComponentMap(status, new ParentProcessGroupSearchNode(status.getId(), null));
    }

    private static ComponentMapHolder createComponentMap(final ProcessGroupStatus status, final ParentProcessGroupSearchNode thisProcessGroupNode) {
        final ComponentMapHolder holder = new ComponentMapHolder();
        final Map<String, String> componentNameMap = holder.componentNameMap;
        final Map<String, ParentProcessGroupSearchNode> componentToParentGroupMap = holder.componentToParentGroupMap;
        final Map<String, String> sourceToConnectionParentGroupMap = holder.sourceToConnectionParentGroupMap;
        final Map<String, String> destinationToConnectionParentGroupMap = holder.destinationToConnectionParentGroupMap;

        if (status != null) {
            componentNameMap.put(status.getId(), status.getName());

            for (final ProcessorStatus procStatus : status.getProcessorStatus()) {
                componentNameMap.put(procStatus.getId(), procStatus.getName());
                componentToParentGroupMap.put(procStatus.getId(), thisProcessGroupNode);
            }

            for (final PortStatus portStatus : status.getInputPortStatus()) {
                componentNameMap.put(portStatus.getId(), portStatus.getName());
                componentToParentGroupMap.put(portStatus.getId(), thisProcessGroupNode);
            }

            for (final PortStatus portStatus : status.getOutputPortStatus()) {
                componentNameMap.put(portStatus.getId(), portStatus.getName());
                componentToParentGroupMap.put(portStatus.getId(), thisProcessGroupNode);
            }

            for (final RemoteProcessGroupStatus rpgStatus : status.getRemoteProcessGroupStatus()) {
                componentNameMap.put(rpgStatus.getId(), rpgStatus.getName());
                componentToParentGroupMap.put(rpgStatus.getId(), thisProcessGroupNode);
            }

            for (final ConnectionStatus connectionStatus : status.getConnectionStatus()) {
                componentNameMap.put(connectionStatus.getId(), connectionStatus.getName());
                componentToParentGroupMap.put(connectionStatus.getId(), thisProcessGroupNode);
                // Add source and destination for Remote Input/Output Ports because metadata for those are only available at ConnectionStatus.
                componentNameMap.computeIfAbsent(connectionStatus.getSourceId(), k -> connectionStatus.getSourceName());
                componentNameMap.computeIfAbsent(connectionStatus.getDestinationId(), k -> connectionStatus.getDestinationName());
                sourceToConnectionParentGroupMap.put(connectionStatus.getSourceId(), connectionStatus.getGroupId());
                destinationToConnectionParentGroupMap.put(connectionStatus.getDestinationId(), connectionStatus.getGroupId());
            }

            for (final ProcessGroupStatus childGroup : status.getProcessGroupStatus()) {
                componentNameMap.put(childGroup.getId(), childGroup.getName());
                ParentProcessGroupSearchNode childProcessGroupNode = new ParentProcessGroupSearchNode(childGroup.getId(), thisProcessGroupNode);
                componentToParentGroupMap.put(childGroup.getId(), thisProcessGroupNode);
                holder.putAll(createComponentMap(childGroup, childProcessGroupNode));
            }
        }

        return holder;
    }

}
