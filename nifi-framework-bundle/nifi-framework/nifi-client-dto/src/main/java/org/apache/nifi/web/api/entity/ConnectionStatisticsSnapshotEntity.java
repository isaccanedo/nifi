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
package org.apache.nifi.web.api.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.nifi.web.api.dto.ReadablePermission;
import org.apache.nifi.web.api.dto.status.ConnectionStatisticsSnapshotDTO;

/**
 * A serialized representation of this class can be placed in the entity body of a request or response to or from the API.
 * This particular entity holds a reference to a ConnectionStatisticsSnapshotDTO.
 */
public class ConnectionStatisticsSnapshotEntity extends Entity implements ReadablePermission, Cloneable {
    private String id;
    private ConnectionStatisticsSnapshotDTO connectionStatisticsSnapshot;
    private Boolean canRead;

    /**
     * @return The connection id
     */
    @Schema(description = "The id of the connection.")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * The ConnectionStatisticsSnapshotDTO that is being serialized.
     *
     * @return The ConnectionStatisticsSnapshotDTO object
     */
    public ConnectionStatisticsSnapshotDTO getConnectionStatisticsSnapshot() {
        return connectionStatisticsSnapshot;
    }

    public void setConnectionStatisticsSnapshot(ConnectionStatisticsSnapshotDTO connectionStatusSnapshot) {
        this.connectionStatisticsSnapshot = connectionStatusSnapshot;
    }

    @Override
    public Boolean getCanRead() {
        return canRead;
    }

    @Override
    public void setCanRead(Boolean canRead) {
        this.canRead = canRead;
    }

    @Override
    public ConnectionStatisticsSnapshotEntity clone() {
        final ConnectionStatisticsSnapshotEntity other = new ConnectionStatisticsSnapshotEntity();
        other.setId(this.getId());
        other.setCanRead(this.getCanRead());
        other.setConnectionStatisticsSnapshot(this.getConnectionStatisticsSnapshot().clone());

        return other;
    }
}
