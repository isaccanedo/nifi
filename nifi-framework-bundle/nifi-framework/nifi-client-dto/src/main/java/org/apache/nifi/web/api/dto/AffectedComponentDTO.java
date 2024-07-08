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

package org.apache.nifi.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.xml.bind.annotation.XmlType;
import java.util.Collection;

@XmlType(name = "affectedComponent")
public class AffectedComponentDTO {
    public static final String COMPONENT_TYPE_PROCESSOR = "PROCESSOR";
    public static final String COMPONENT_TYPE_CONTROLLER_SERVICE = "CONTROLLER_SERVICE";
    public static final String COMPONENT_TYPE_INPUT_PORT = "INPUT_PORT";
    public static final String COMPONENT_TYPE_OUTPUT_PORT = "OUTPUT_PORT";
    public static final String COMPONENT_TYPE_REMOTE_INPUT_PORT = "REMOTE_INPUT_PORT";
    public static final String COMPONENT_TYPE_REMOTE_OUTPUT_PORT = "REMOTE_OUTPUT_PORT";
    public static final String COMPONENT_TYPE_STATELESS_GROUP = "STATELESS_GROUP";

    private String processGroupId;
    private String id;
    private String referenceType;
    private String name;
    private String state;
    private Integer activeThreadCount;

    private Collection<String> validationErrors;

    @Schema(description = "The UUID of the Process Group that this component is in")
    public String getProcessGroupId() {
        return processGroupId;
    }

    public void setProcessGroupId(final String processGroupId) {
        this.processGroupId = processGroupId;
    }

    @Schema(description = "The UUID of this component")
    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    @Schema(description = "The type of this component",
        allowableValues = COMPONENT_TYPE_PROCESSOR + "," + COMPONENT_TYPE_CONTROLLER_SERVICE + ", "
            + COMPONENT_TYPE_INPUT_PORT + ", " + COMPONENT_TYPE_OUTPUT_PORT + ", "
            + COMPONENT_TYPE_REMOTE_INPUT_PORT + ", " + COMPONENT_TYPE_REMOTE_OUTPUT_PORT + ", " + COMPONENT_TYPE_STATELESS_GROUP)
    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(final String referenceType) {
        this.referenceType = referenceType;
    }

    @Schema(description = "The name of this component.")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return active thread count for the referencing component
     */
    @Schema(description = "The number of active threads for the referencing component."
    )
    public Integer getActiveThreadCount() {
        return activeThreadCount;
    }

    public void setActiveThreadCount(Integer activeThreadCount) {
        this.activeThreadCount = activeThreadCount;
    }

    /**
     * @return Any validation error associated with this component
     */
    @Schema(description = "The validation errors for the component."
    )
    public Collection<String> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(Collection<String> validationErrors) {
        this.validationErrors = validationErrors;
    }

    @Schema(description = "The scheduled state of a processor or reporting task referencing a controller service. If this component is another controller "
        + "service, this field represents the controller service state.")
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return "AffectedComponent[type=" + referenceType + ", id=" + id + ", name=" + name + ", processGroup=" + processGroupId + "]";
    }
}
