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

import org.apache.nifi.annotation.notification.PrimaryNodeState;
import org.apache.nifi.components.ConfigVerificationResult;
import org.apache.nifi.controller.scheduling.LifecycleState;
import org.apache.nifi.controller.service.ControllerServiceNode;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.migration.ControllerServiceFactory;
import org.apache.nifi.nar.ExtensionManager;
import org.apache.nifi.reporting.ReportingContext;
import org.apache.nifi.reporting.ReportingTask;
import org.apache.nifi.scheduling.SchedulingStrategy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public interface ReportingTaskNode extends ComponentNode {

    void setSchedulingStrategy(SchedulingStrategy schedulingStrategy);

    SchedulingStrategy getSchedulingStrategy();

    /**
     * @return a string representation of the time between each scheduling
     * period
     */
    String getSchedulingPeriod();

    long getSchedulingPeriod(TimeUnit timeUnit);

    /**
     * Updates how often the ReportingTask should be triggered to run
     *
     * @param schedulingPeriod new period
     */
    void setSchedulingPeriod(String schedulingPeriod);

    ReportingTask getReportingTask();

    void setReportingTask(LoggableComponent<ReportingTask> reportingTask);

    ReportingContext getReportingContext();

    ConfigurationContext getConfigurationContext();

    boolean isRunning();

    /**
     * @return the number of threads (concurrent tasks) currently being used by
     * this ReportingTask
     */
    int getActiveThreadCount();

    /**
     * @return Indicates the {@link ScheduledState} of this <code>ReportingTask</code>.
     * A value of stopped does NOT indicate that the <code>ReportingTask</code>
     * has no active threads, only that it is not currently scheduled to be
     * given any more threads. To determine whether or not the
     * <code>ReportingTask</code> has any active threads, see
     * {@link ProcessScheduler#getActiveThreadCount(Object)}
     */
    ScheduledState getScheduledState();

    void setScheduledState(ScheduledState state);

    String getComments();

    void setComments(String comment);

    /**
     * Verifies that this Reporting Task can be enabled if the provided set of
     * services are enabled. This is introduced because we need to verify that
     * all components can be started before starting any of them. In order to do
     * that, we need to know that this component can be started if the given
     * services are enabled, as we will then enable the given services before
     * starting this component.
     *
     * @param ignoredReferences to ignore
     */
    void verifyCanStart(Set<ControllerServiceNode> ignoredReferences);

    void verifyCanStart();

    void verifyCanStop();

    void verifyCanDisable();

    void verifyCanEnable();

    void verifyCanDelete();

    void verifyCanUpdate();

    void verifyCanClearState();

    /**
     * Verifies that the Reporting Task is in a state in which it can verify a configuration by calling
     * {@link #verifyConfiguration(ConfigurationContext, ComponentLog, ExtensionManager)}.
     *
     * @throws IllegalStateException if not in a state in which configuration can be verified
     */
    void verifyCanPerformVerification();

    /**
     * Verifies that the given configuration is valid for the Reporting Task
     *
     * @param context the configuration to verify
     * @param logger a logger that can be used when performing verification
     * @param extensionManager extension manager that is used for obtaining appropriate NAR ClassLoaders
     * @return a list of results indicating whether or not the given configuration is valid
     */
    List<ConfigVerificationResult> verifyConfiguration(ConfigurationContext context, ComponentLog logger, ExtensionManager extensionManager);

    void start();

    void stop();

    void enable();

    void disable();

    void notifyPrimaryNodeChanged(PrimaryNodeState primaryNodeState, LifecycleState lifecycleState);

    void migrateConfiguration(Map<String, String> originalPropertyValues, ControllerServiceFactory controllerServiceFactory);
}
