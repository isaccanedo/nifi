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

package org.apache.nifi.toolkit.cli.impl.client.nifi.impl;

import org.apache.nifi.diagnostics.DiagnosticLevel;
import org.apache.nifi.toolkit.cli.impl.client.nifi.NiFiClientException;
import org.apache.nifi.toolkit.cli.impl.client.nifi.RequestConfig;
import org.apache.nifi.toolkit.cli.impl.client.nifi.SystemDiagnosticsClient;
import org.apache.nifi.web.api.entity.SystemDiagnosticsEntity;

import jakarta.ws.rs.client.WebTarget;
import java.io.IOException;

public class JerseySystemDiagnosticsClient extends AbstractJerseyClient implements SystemDiagnosticsClient {
    private final WebTarget systemsDiagnosticsTarget;

    public JerseySystemDiagnosticsClient(final WebTarget baseTarget) {
        this(baseTarget, null);
    }

    public JerseySystemDiagnosticsClient(final WebTarget baseTarget, final RequestConfig requestConfig) {
        super(requestConfig);
        this.systemsDiagnosticsTarget = baseTarget.path("/system-diagnostics");
    }

    @Override
    public SystemDiagnosticsEntity getSystemDiagnostics(final boolean nodewise, final DiagnosticLevel diagnosticLevel) throws NiFiClientException, IOException {
        return executeAction("Error retrieving system diagnostics", () -> {
            final WebTarget target = systemsDiagnosticsTarget
                .queryParam("nodewise", nodewise)
                .queryParam("diagnosticLevel", diagnosticLevel.name());

            return getRequestBuilder(target).get(SystemDiagnosticsEntity.class);
        });
    }
}
