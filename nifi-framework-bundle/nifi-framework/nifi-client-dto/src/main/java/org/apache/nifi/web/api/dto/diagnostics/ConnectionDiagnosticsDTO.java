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
package org.apache.nifi.web.api.dto.diagnostics;

import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.nifi.web.api.dto.ConnectionDTO;

import jakarta.xml.bind.annotation.XmlType;
import java.util.List;

@XmlType(name = "connectionDiagnostics")
public class ConnectionDiagnosticsDTO {
    private ConnectionDTO connection;
    private ConnectionDiagnosticsSnapshotDTO aggregateSnapshot;
    private List<ConnectionDiagnosticsSnapshotDTO> nodeSnapshots;

    @Schema(description = "Details about the connection", accessMode = Schema.AccessMode.READ_ONLY)
    public ConnectionDTO getConnection() {
        return connection;
    }

    public void setConnection(final ConnectionDTO connection) {
        this.connection = connection;
    }

    @Schema(description = "Aggregate values for all nodes in the cluster, or for this instance if not clustered",
            accessMode = Schema.AccessMode.READ_ONLY)
    public ConnectionDiagnosticsSnapshotDTO getAggregateSnapshot() {
        return aggregateSnapshot;
    }

    public void setAggregateSnapshot(final ConnectionDiagnosticsSnapshotDTO aggregateSnapshot) {
        this.aggregateSnapshot = aggregateSnapshot;
    }

    @Schema(description = "A list of values for each node in the cluster, if clustered.", accessMode = Schema.AccessMode.READ_ONLY)
    public List<ConnectionDiagnosticsSnapshotDTO> getNodeSnapshots() {
        return nodeSnapshots;
    }

    public void setNodeSnapshots(final List<ConnectionDiagnosticsSnapshotDTO> nodeSnapshots) {
        this.nodeSnapshots = nodeSnapshots;
    }
}
