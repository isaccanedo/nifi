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
package org.apache.nifi.controller.scheduling;

import org.apache.nifi.connectable.Connectable;
import org.apache.nifi.controller.ReportingTaskNode;
import org.apache.nifi.engine.FlowEngine;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Base implementation of the {@link SchedulingAgent} which encapsulates the
 * updates to the {@link LifecycleState} based on invoked operation and then
 * delegates to the corresponding 'do' methods. For example; By invoking
 * {@link #schedule(Connectable, LifecycleState)} the
 * {@link LifecycleState#setScheduled(boolean)} with value 'true' will be
 * invoked.
 *
 * @see TimerDrivenSchedulingAgent
 * @see CronSchedulingAgent
 */
abstract class AbstractSchedulingAgent implements SchedulingAgent {

    protected final FlowEngine flowEngine;

    protected AbstractSchedulingAgent(FlowEngine flowEngine) {
        this.flowEngine = flowEngine;
    }

    @Override
    public void schedule(Connectable connectable, LifecycleState scheduleState) {
        scheduleState.setScheduled(true);
        this.doSchedule(connectable, scheduleState);
    }

    @Override
    public void scheduleOnce(Connectable connectable, LifecycleState scheduleState, Callable<Future<Void>> stopCallback) {
        scheduleState.setScheduled(true);
        this.doScheduleOnce(connectable, scheduleState, stopCallback);
    }

    @Override
    public void unschedule(Connectable connectable, LifecycleState lifeycleState) {
        lifeycleState.setScheduled(false);
        this.doUnschedule(connectable, lifeycleState);
    }

    @Override
    public void schedule(ReportingTaskNode taskNode, LifecycleState lifecycleState) {
        lifecycleState.setScheduled(true);
        this.doSchedule(taskNode, lifecycleState);
    }

    @Override
    public void unschedule(ReportingTaskNode taskNode, LifecycleState lifecycleState) {
        lifecycleState.setScheduled(false);
        this.doUnschedule(taskNode, lifecycleState);
    }

    /**
     * Schedules the provided {@link Connectable}. Its {@link LifecycleState}
     * will be set to <i>true</i>
     *
     * @param connectable
     *            the instance of {@link Connectable}
     * @param scheduleState
     *            the instance of {@link LifecycleState}
     */
    protected abstract void doSchedule(Connectable connectable, LifecycleState scheduleState);

    /**
     * Schedules the provided {@link Connectable} to run once and then calls the provided stopCallback to stop it.
     * Its {@link LifecycleState} will be set to <i>true</i>
     *
     * @param connectable
     *            the instance of {@link Connectable}
     * @param scheduleState
     *            the instance of {@link LifecycleState}
     * @param stopCallback the callback responsible for stopping connectable after it ran once
     */
    protected abstract void doScheduleOnce(Connectable connectable, LifecycleState scheduleState, Callable<Future<Void>> stopCallback);

    /**
     * Unschedules the provided {@link Connectable}. Its {@link LifecycleState}
     * will be set to <i>false</i>
     *
     * @param connectable
     *            the instance of {@link Connectable}
     * @param scheduleState
     *            the instance of {@link LifecycleState}
     */
    protected abstract void doUnschedule(Connectable connectable, LifecycleState scheduleState);

    /**
     * Schedules the provided {@link ReportingTaskNode}. Its
     * {@link LifecycleState} will be set to <i>true</i>
     *
     * @param connectable
     *            the instance of {@link ReportingTaskNode}
     * @param scheduleState
     *            the instance of {@link LifecycleState}
     */
    protected abstract void doSchedule(ReportingTaskNode connectable, LifecycleState scheduleState);

    /**
     * Unschedules the provided {@link ReportingTaskNode}. Its
     * {@link LifecycleState} will be set to <i>false</i>
     *
     * @param connectable
     *            the instance of {@link ReportingTaskNode}
     * @param scheduleState
     *            the instance of {@link LifecycleState}
     */
    protected abstract void doUnschedule(ReportingTaskNode connectable, LifecycleState scheduleState);
}
