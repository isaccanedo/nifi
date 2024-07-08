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
package org.apache.nifi.controller;

import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.PrimaryNodeOnly;
import org.apache.nifi.annotation.behavior.SideEffectFree;
import org.apache.nifi.annotation.behavior.SupportsBatching;
import org.apache.nifi.annotation.behavior.TriggerSerially;
import org.apache.nifi.annotation.behavior.TriggerWhenAnyDestinationAvailable;
import org.apache.nifi.annotation.behavior.TriggerWhenEmpty;
import org.apache.nifi.bundle.BundleCoordinate;
import org.apache.nifi.processor.Processor;

/**
 * Holder for StandardProcessorNode to atomically swap out the component.
 */
public class ProcessorDetails {

    private final Processor processor;
    private final Class<?> procClass;
    private final boolean triggerWhenEmpty;
    private final boolean sideEffectFree;
    private final boolean triggeredSerially;
    private final boolean triggerWhenAnyDestinationAvailable;
    private final boolean batchSupported;
    private final boolean executionNodeRestricted;
    private final InputRequirement.Requirement inputRequirement;
    private final TerminationAwareLogger componentLog;
    private final BundleCoordinate bundleCoordinate;

    public ProcessorDetails(final LoggableComponent<Processor> processor) {
        this.processor = processor.getComponent();
        this.componentLog = processor.getLogger();
        this.bundleCoordinate = processor.getBundleCoordinate();

        this.procClass = this.processor.getClass();
        this.triggerWhenEmpty = procClass.isAnnotationPresent(TriggerWhenEmpty.class);
        this.sideEffectFree = procClass.isAnnotationPresent(SideEffectFree.class);
        this.batchSupported = procClass.isAnnotationPresent(SupportsBatching.class);
        this.triggeredSerially = procClass.isAnnotationPresent(TriggerSerially.class);
        this.triggerWhenAnyDestinationAvailable = procClass.isAnnotationPresent(TriggerWhenAnyDestinationAvailable.class);
        this.executionNodeRestricted = procClass.isAnnotationPresent(PrimaryNodeOnly.class);

        final boolean inputRequirementPresent = procClass.isAnnotationPresent(InputRequirement.class);
        if (inputRequirementPresent) {
            this.inputRequirement = procClass.getAnnotation(InputRequirement.class).value();
        } else {
            this.inputRequirement = InputRequirement.Requirement.INPUT_ALLOWED;
        }
    }

    public Processor getProcessor() {
        return processor;
    }

    public Class<?> getProcClass() {
        return procClass;
    }

    public boolean isTriggerWhenEmpty() {
        return triggerWhenEmpty;
    }

    public boolean isSideEffectFree() {
        return sideEffectFree;
    }

    public boolean isTriggeredSerially() {
        return triggeredSerially;
    }

    public boolean isTriggerWhenAnyDestinationAvailable() {
        return triggerWhenAnyDestinationAvailable;
    }

    public boolean isBatchSupported() {
        return batchSupported;
    }

    public boolean isExecutionNodeRestricted() {
        return executionNodeRestricted;
    }

    public InputRequirement.Requirement getInputRequirement() {
        return inputRequirement;
    }

    public TerminationAwareLogger getComponentLog() {
        return componentLog;
    }

    public BundleCoordinate getBundleCoordinate() {
        return bundleCoordinate;
    }
}
