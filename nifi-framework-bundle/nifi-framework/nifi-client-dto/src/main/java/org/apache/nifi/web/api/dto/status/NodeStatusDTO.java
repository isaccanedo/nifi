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
import org.apache.nifi.web.api.dto.NodeDTO;

import jakarta.xml.bind.annotation.XmlType;

/**
 * DTO for serializing the node status.
 */
@XmlType(name = "nodeStatus")
public class NodeStatusDTO {

    private NodeDTO node;
    private ProcessGroupStatusDTO controllerStatus;

    /**
     * @return the node
     */
    @Schema(description = "The node."
    )
    public NodeDTO getNode() {
        return node;
    }

    public void setNode(NodeDTO node) {
        this.node = node;
    }

    /**
     * @return the controller status
     */
    @Schema(description = "The controller status for each node."
    )
    public ProcessGroupStatusDTO getControllerStatus() {
        return controllerStatus;
    }

    public void setControllerStatus(ProcessGroupStatusDTO controllerStatus) {
        this.controllerStatus = controllerStatus;
    }

}
