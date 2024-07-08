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
package org.apache.nifi.controller.inheritance;

import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.cluster.protocol.DataFlow;
import org.apache.nifi.controller.FlowController;
import org.apache.nifi.controller.flow.VersionedDataflow;
import org.apache.nifi.flow.VersionedComponent;
import org.apache.nifi.flow.VersionedProcessGroup;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MissingComponentsCheck implements FlowInheritabilityCheck {
    @Override
    public FlowInheritability checkInheritability(final DataFlow existingFlow, final DataFlow proposedFlow, final FlowController flowController) {
        if (existingFlow == null) {
            return FlowInheritability.inheritable(); // no existing flow, so equivalent to proposed flow
        }

        final Set<String> existingMissingComponents = new HashSet<>(existingFlow.getMissingComponents());
        existingMissingComponents.removeAll(proposedFlow.getMissingComponents());

        if (!existingMissingComponents.isEmpty()) {
            // Do not consider any components that are not present in the proposed flow.
            final Set<String> versionedComponentIds = getAllComponentIds(proposedFlow.getVersionedDataflow());
            existingMissingComponents.retainAll(versionedComponentIds);
        }

        if (!existingMissingComponents.isEmpty()) {
            final String missingIds = StringUtils.join(existingMissingComponents, ",");
            return FlowInheritability.notInheritable("Current flow has missing components that are not considered missing in the proposed flow (" + missingIds + ")");
        }

        final Set<String> proposedMissingComponents = new HashSet<>(proposedFlow.getMissingComponents());
        proposedMissingComponents.removeAll(existingFlow.getMissingComponents());

        if (!proposedMissingComponents.isEmpty()) {
            // Do not consider any components that are not present in the current flow.
            final Set<String> versionedComponentIds = getAllComponentIds(existingFlow.getVersionedDataflow());
            proposedMissingComponents.retainAll(versionedComponentIds);
        }
        if (!proposedMissingComponents.isEmpty()) {
            final String missingIds = StringUtils.join(proposedMissingComponents, ",");
            return FlowInheritability.notInheritable("Proposed flow has missing components that are not considered missing in the current flow (" + missingIds + ")");
        }

        return FlowInheritability.inheritable();
    }

    private Set<String> getAllComponentIds(final VersionedDataflow dataflow) {
        if (dataflow == null) {
            return Collections.emptySet();
        }

        final Set<String> ids = new HashSet<>();
        findAllComponentIds(dataflow, ids);
        return ids;
    }

    private void findAllComponentIds(final VersionedDataflow dataflow, final Set<String> ids) {
        addAllIds(dataflow.getControllerServices(), ids);
        addAllIds(dataflow.getRegistries(), ids);
        addAllIds(dataflow.getFlowAnalysisRules(), ids);
        addAllIds(dataflow.getParameterContexts(), ids);
        addAllIds(dataflow.getParameterProviders(), ids);
        addAllIds(dataflow.getReportingTasks(), ids);

        findAllComponentIds(dataflow.getRootGroup(), ids);
    }

    private void findAllComponentIds(final VersionedProcessGroup group, final Set<String> ids) {
        if (group == null) {
            return;
        }

        addAllIds(group.getControllerServices(), ids);
        addAllIds(group.getProcessors(), ids);

        group.getProcessGroups().forEach(child -> findAllComponentIds(child, ids));
    }

    private void addAllIds(final Collection<? extends VersionedComponent> components, final Set<String> ids) {
        if (components != null) {
            components.forEach(component -> ids.add(component.getInstanceIdentifier()));
        }
    }

}
