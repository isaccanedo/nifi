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

package org.apache.nifi.registry.flow.diff;

import org.apache.nifi.flow.VersionedControllerService;
import org.apache.nifi.flow.VersionedFlowAnalysisRule;
import org.apache.nifi.flow.VersionedFlowRegistryClient;
import org.apache.nifi.flow.VersionedParameterProvider;
import org.apache.nifi.flow.VersionedProcessGroup;
import org.apache.nifi.flow.VersionedReportingTask;
import org.apache.nifi.flow.VersionedParameterContext;

import java.util.Set;

public interface ComparableDataFlow {
    String getName();

    VersionedProcessGroup getContents();

    Set<VersionedControllerService> getControllerLevelServices();

    Set<VersionedReportingTask> getReportingTasks();

    Set<VersionedFlowAnalysisRule> getFlowAnalysisRules();

    Set<VersionedParameterContext> getParameterContexts();

    Set<VersionedParameterProvider> getParameterProviders();

    Set<VersionedFlowRegistryClient> getFlowRegistryClients();
}
