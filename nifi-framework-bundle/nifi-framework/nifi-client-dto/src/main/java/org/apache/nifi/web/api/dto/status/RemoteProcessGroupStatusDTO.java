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

package org.apache.nifi.web.api.dto.status;

import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.nifi.web.api.dto.util.TimeAdapter;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Date;
import java.util.List;

@XmlType(name = "remoteProcessGroupStatus")
public class RemoteProcessGroupStatusDTO {
    private String groupId;
    private String id;
    private String name;
    private String targetUri;
    private String transmissionStatus;
    private Date statsLastRefreshed;
    private String validationStatus;

    private RemoteProcessGroupStatusSnapshotDTO aggregateSnapshot;
    private List<NodeRemoteProcessGroupStatusSnapshotDTO> nodeSnapshots;

    @Schema(description = "The unique ID of the process group that the Processor belongs to")
    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    @Schema(description = "The unique ID of the Processor")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Schema(description = "The name of the remote process group.")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Schema(description = "The transmission status of the remote process group.")
    public String getTransmissionStatus() {
        return transmissionStatus;
    }

    public void setTransmissionStatus(String transmissionStatus) {
        this.transmissionStatus = transmissionStatus;
    }

    @Schema(description = "The URI of the target system.")
    public String getTargetUri() {
        return targetUri;
    }

    public void setTargetUri(String targetUri) {
        this.targetUri = targetUri;
    }

    @Schema(description = "A status snapshot that represents the aggregate stats of all nodes in the cluster. If the NiFi instance is "
        + "a standalone instance, rather than a cluster, this represents the stats of the single instance.")
    public RemoteProcessGroupStatusSnapshotDTO getAggregateSnapshot() {
        return aggregateSnapshot;
    }

    public void setAggregateSnapshot(RemoteProcessGroupStatusSnapshotDTO aggregateSnapshot) {
        this.aggregateSnapshot = aggregateSnapshot;
    }

    @Schema(description = "A status snapshot for each node in the cluster. If the NiFi instance is a standalone instance, rather than "
        + "a cluster, this may be null.")
    public List<NodeRemoteProcessGroupStatusSnapshotDTO> getNodeSnapshots() {
        return nodeSnapshots;
    }

    public void setNodeSnapshots(List<NodeRemoteProcessGroupStatusSnapshotDTO> nodeSnapshots) {
        this.nodeSnapshots = nodeSnapshots;
    }

    /**
     * When the status for this process group was calculated.
     *
     * @return The the status was calculated
     */
    @XmlJavaTypeAdapter(TimeAdapter.class)
    @Schema(description = "The time the status for the process group was last refreshed.",
        type = "string"
    )
    public Date getStatsLastRefreshed() {
        return statsLastRefreshed;
    }

    public void setStatsLastRefreshed(Date statsLastRefreshed) {
        this.statsLastRefreshed = statsLastRefreshed;
    }

    @Schema(description = "Indicates whether the component is valid, invalid, or still in the process of validating" +
            " (i.e., it is unknown whether or not the component is valid)",
            accessMode = Schema.AccessMode.READ_ONLY,
            allowableValues = "VALID, INVALID, VALIDATING")
    public String getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }

}
