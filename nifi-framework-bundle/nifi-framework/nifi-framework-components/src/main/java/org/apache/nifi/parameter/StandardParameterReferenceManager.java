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
package org.apache.nifi.parameter;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.VersionedComponent;
import org.apache.nifi.controller.ComponentNode;
import org.apache.nifi.controller.ControllerService;
import org.apache.nifi.controller.ProcessorNode;
import org.apache.nifi.controller.PropertyConfiguration;
import org.apache.nifi.controller.flow.FlowManager;
import org.apache.nifi.controller.service.ControllerServiceNode;
import org.apache.nifi.groups.ProcessGroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class StandardParameterReferenceManager implements ParameterReferenceManager {
    private final FlowManager flowManager;

    // TODO: Consider reworking this so that we don't have to recurse through all components all the time and instead
    //  have a 'caching' impl that AbstractComponentNode.setProperties() adds to/subtracts from.
    public StandardParameterReferenceManager(final FlowManager flowManager) {
        this.flowManager = flowManager;
    }

    @Override
    public Set<ProcessorNode> getProcessorsReferencing(final ParameterContext parameterContext, final String parameterName) {
        return getComponentsReferencing(parameterContext, parameterName, ProcessGroup::getProcessors);
    }

    @Override
    public Set<ControllerServiceNode> getControllerServicesReferencing(final ParameterContext parameterContext, final String parameterName) {
        return getComponentsReferencing(parameterContext, parameterName, group -> group.getControllerServices(false));
    }

    @Override
    public List<ParameterReferencedControllerServiceData> getReferencedControllerServiceData(final ParameterContext parameterContext, final String parameterName) {
        final List<ParameterReferencedControllerServiceData> referencedControllerServiceData = new ArrayList<>();

        final String versionedServiceId = parameterContext.getParameter(parameterName)
            .map(Parameter::getValue)
            .map(this.flowManager::getControllerServiceNode)
            .flatMap(VersionedComponent::getVersionedComponentId)
            .orElse(null);


        final ProcessGroup rootGroup = flowManager.getRootGroup();
        final List<ProcessGroup> referencingGroups = rootGroup.findAllProcessGroups(group -> group.referencesParameterContext(parameterContext));

        for (final ProcessGroup group : referencingGroups) {
            for (ProcessorNode processor : group.getProcessors()) {
                referencedControllerServiceData.addAll(getReferencedControllerServiceData(
                    processor,
                    parameterName,
                    versionedServiceId
                ));
            }

            for (ControllerServiceNode controllerService : group.getControllerServices(false)) {
                referencedControllerServiceData.addAll(getReferencedControllerServiceData(
                    controllerService,
                    parameterName,
                    versionedServiceId
                ));
            }
        }

        return referencedControllerServiceData;
    }

    @Override
    public Set<ProcessGroup> getProcessGroupsBound(final ParameterContext parameterContext) {
        final ProcessGroup rootGroup = flowManager.getRootGroup();
        final List<ProcessGroup> referencingGroups = rootGroup.findAllProcessGroups(group -> group.referencesParameterContext(parameterContext));

        return new HashSet<>(referencingGroups);
    }

    private <T extends ComponentNode> Set<T> getComponentsReferencing(final ParameterContext parameterContext, final String parameterName,
                                                                      final Function<ProcessGroup, Collection<T>> componentFunction) {
        final Set<T> referencingComponents = new HashSet<>();

        final ProcessGroup rootGroup = flowManager.getRootGroup();
        final List<ProcessGroup> referencingGroups = rootGroup.findAllProcessGroups(group -> group.referencesParameterContext(parameterContext));

        for (final ProcessGroup group : referencingGroups) {
            for (final T componentNode : componentFunction.apply(group)) {
                if (componentNode.isReferencingParameter(parameterName)) {
                    referencingComponents.add(componentNode);
                    continue;
                }
            }
        }

        return referencingComponents;
    }

    private Set<ParameterReferencedControllerServiceData> getReferencedControllerServiceData(
        final ComponentNode componentNode,
        final String parameterName,
        final String versionedServiceId
    ) {
        Set<ParameterReferencedControllerServiceData> referencedControllerServiceTypes = new HashSet<>();

        for (Map.Entry<PropertyDescriptor, PropertyConfiguration> propertyDescriptorAndPropertyConfiguration : componentNode.getProperties().entrySet()) {
            PropertyDescriptor descriptor = propertyDescriptorAndPropertyConfiguration.getKey();
            PropertyConfiguration configuration = propertyDescriptorAndPropertyConfiguration.getValue();

            Class<? extends ControllerService> referencedControllerServiceType = descriptor.getControllerServiceDefinition();

            if (referencedControllerServiceType == null || configuration == null) {
                continue;
            }

            for (final ParameterReference reference : configuration.getParameterReferences()) {
                if (parameterName.equals(reference.getParameterName())) {
                    referencedControllerServiceTypes.add(new ParameterReferencedControllerServiceData(
                        parameterName,
                        componentNode,
                        descriptor,
                        referencedControllerServiceType,
                        versionedServiceId
                    ));
                }
            }
        }

        return referencedControllerServiceTypes;
    }

}
