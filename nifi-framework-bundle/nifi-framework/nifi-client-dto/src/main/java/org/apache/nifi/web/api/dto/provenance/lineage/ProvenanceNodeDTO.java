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
package org.apache.nifi.web.api.dto.provenance.lineage;

import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.nifi.web.api.dto.util.TimestampAdapter;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Date;
import java.util.List;

/**
 * A node within a provenance lineage. May represent either an event or a flowfile.
 */
@XmlType(name = "provenanceNode")
public class ProvenanceNodeDTO {

    private String id;
    private String flowFileUuid;
    private List<String> parentUuids;
    private List<String> childUuids;
    private String clusterNodeIdentifier;
    private String type;
    private String eventType;
    private Long millis;
    private Date timestamp;

    /**
     * @return id of the node
     */
    @Schema(description = "The id of the node."
    )
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return flowfile uuid for this provenance event
     */
    @Schema(description = "The uuid of the flowfile associated with the provenance event."
    )
    public String getFlowFileUuid() {
        return flowFileUuid;
    }

    public void setFlowFileUuid(String flowFileUuid) {
        this.flowFileUuid = flowFileUuid;
    }

    /**
     * @return parent flowfile uuids for this provenance event
     */
    @Schema(description = "The uuid of the parent flowfiles of the provenance event."
    )
    public List<String> getParentUuids() {
        return parentUuids;
    }

    public void setParentUuids(List<String> parentUuids) {
        this.parentUuids = parentUuids;
    }

    /**
     * @return child flowfile uuids for this provenance event
     */
    @Schema(description = "The uuid of the childrent flowfiles of the provenance event."
    )
    public List<String> getChildUuids() {
        return childUuids;
    }

    public void setChildUuids(List<String> childUuids) {
        this.childUuids = childUuids;
    }

    /**
     * @return node identifier that this event/flowfile originated from
     */
    @Schema(description = "The identifier of the node that this event/flowfile originated from."
    )
    public String getClusterNodeIdentifier() {
        return clusterNodeIdentifier;
    }

    public void setClusterNodeIdentifier(String clusterNodeIdentifier) {
        this.clusterNodeIdentifier = clusterNodeIdentifier;
    }

    /**
     * @return type of node
     */
    @Schema(description = "The type of the node.",
            allowableValues = "FLOWFILE, EVENT"
    )
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * @return this is an event node, this is the type of event
     */
    @Schema(description = "If the type is EVENT, this is the type of event."
    )
    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /**
     * @return timestamp of this node
     */
    @XmlJavaTypeAdapter(TimestampAdapter.class)
    @Schema(description = "The timestamp of the node formatted.",
            type = "string"
    )
    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * @return number of millis since epoch
     */
    @Schema(description = "The timestamp of the node in milliseconds."
    )
    public Long getMillis() {
        return millis;
    }

    public void setMillis(Long millis) {
        this.millis = millis;
    }

}
