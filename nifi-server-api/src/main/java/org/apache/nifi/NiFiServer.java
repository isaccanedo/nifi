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
package org.apache.nifi;

import org.apache.nifi.bundle.Bundle;
import org.apache.nifi.cluster.ClusterDetailsFactory;
import org.apache.nifi.controller.DecommissionTask;
import org.apache.nifi.controller.status.history.StatusHistoryDumpFactory;
import org.apache.nifi.diagnostics.DiagnosticsFactory;
import org.apache.nifi.nar.ExtensionMapping;
import org.apache.nifi.util.NiFiProperties;

import java.util.Set;

/**
 * The main interface for declaring a NiFi-based server application
 */
public interface NiFiServer {

    void start();

    void initialize(NiFiProperties properties, Bundle systemBundle, Set<Bundle> bundles, ExtensionMapping extensionMapping);

    void stop();

    DiagnosticsFactory getDiagnosticsFactory();

    DiagnosticsFactory getThreadDumpFactory();

    DecommissionTask getDecommissionTask();

    ClusterDetailsFactory getClusterDetailsFactory();

    StatusHistoryDumpFactory getStatusHistoryDumpFactory();
}
