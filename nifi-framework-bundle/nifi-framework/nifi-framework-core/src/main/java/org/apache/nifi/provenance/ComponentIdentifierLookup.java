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
package org.apache.nifi.provenance;

import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.connectable.Connection;
import org.apache.nifi.controller.FlowController;
import org.apache.nifi.groups.ProcessGroup;
import org.apache.nifi.nar.ExtensionDefinition;
import org.apache.nifi.processor.Processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ComponentIdentifierLookup implements IdentifierLookup {
    private final FlowController flowController;

    public ComponentIdentifierLookup(final FlowController flowController) {
        this.flowController = flowController;
    }

    @Override
    public List<String> getComponentIdentifiers() {
        final ProcessGroup rootGroup = flowController.getFlowManager().getRootGroup();

        final List<String> componentIds = new ArrayList<>();
        rootGroup.findAllProcessors().forEach(proc -> componentIds.add(proc.getIdentifier()));
        rootGroup.getInputPorts().forEach(port -> componentIds.add(port.getIdentifier()));
        rootGroup.getOutputPorts().forEach(port -> componentIds.add(port.getIdentifier()));

        return componentIds;
    }

    @Override
    public List<String> getComponentTypes() {
        final Set<ExtensionDefinition> procDefinitions = flowController.getExtensionManager().getExtensions(Processor.class);

        final List<String> componentTypes = new ArrayList<>(procDefinitions.size() + 2);
        componentTypes.add(ProvenanceEventRecord.REMOTE_INPUT_PORT_TYPE);
        componentTypes.add(ProvenanceEventRecord.REMOTE_OUTPUT_PORT_TYPE);

        procDefinitions.stream()
            .map(ExtensionDefinition::getImplementationClassName)
            .map(className -> className.contains(".") ? StringUtils.substringAfterLast(className, ".") : className)
            .forEach(componentTypes::add);

        return componentTypes;
    }

    @Override
    public List<String> getQueueIdentifiers() {
        Set<Connection> connectionSet = flowController.getFlowManager().findAllConnections();
        List<String> identifiers = new ArrayList<>(connectionSet.size());

        for (Connection c : connectionSet) {
            identifiers.add(c.getIdentifier());
        }
        return identifiers;
    }
}
