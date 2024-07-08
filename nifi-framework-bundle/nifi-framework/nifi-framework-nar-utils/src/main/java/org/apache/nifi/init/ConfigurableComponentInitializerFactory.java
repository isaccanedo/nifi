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
package org.apache.nifi.init;

import org.apache.nifi.FlowRegistryClientInitializer;
import org.apache.nifi.components.ConfigurableComponent;
import org.apache.nifi.controller.ControllerService;
import org.apache.nifi.flowanalysis.FlowAnalysisRule;
import org.apache.nifi.nar.ExtensionManager;
import org.apache.nifi.parameter.ParameterProvider;
import org.apache.nifi.processor.Processor;
import org.apache.nifi.registry.flow.FlowRegistryClient;
import org.apache.nifi.reporting.ReportingTask;

public class ConfigurableComponentInitializerFactory {

    /**
     * Returns a ConfigurableComponentInitializer for the type of component.
     * Currently Processor, ControllerService and ReportingTask are supported.
     *
     * @param componentClass the class that requires a ConfigurableComponentInitializer
     * @return a ConfigurableComponentInitializer capable of initializing that specific type of class
     */
    public static ConfigurableComponentInitializer createComponentInitializer(
            final ExtensionManager extensionManager, final Class<? extends ConfigurableComponent> componentClass) {
        if (Processor.class.isAssignableFrom(componentClass)) {
            return new ProcessorInitializer(extensionManager);
        } else if (ControllerService.class.isAssignableFrom(componentClass)) {
            return new ControllerServiceInitializer(extensionManager);
        } else if (ReportingTask.class.isAssignableFrom(componentClass)) {
            return new ReportingTaskInitializer(extensionManager);
        } else if (FlowAnalysisRule.class.isAssignableFrom(componentClass)) {
            return new FlowAnalysisRuleInitializer(extensionManager);
        } else if (ParameterProvider.class.isAssignableFrom(componentClass)) {
            return new ParameterProviderInitializer(extensionManager);
        } else if (FlowRegistryClient.class.isAssignableFrom(componentClass)) {
            return new FlowRegistryClientInitializer(extensionManager);
        }

        return null;
    }
}
