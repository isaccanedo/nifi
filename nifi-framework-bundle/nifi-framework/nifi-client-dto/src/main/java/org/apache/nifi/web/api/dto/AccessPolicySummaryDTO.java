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
import org.apache.nifi.web.api.entity.ComponentReferenceEntity;

import jakarta.xml.bind.annotation.XmlType;

/**
 * Details for the access configuration.
 */
@XmlType(name = "accessPolicy")
public class AccessPolicySummaryDTO extends ComponentDTO {

    private String resource;
    private String action;
    private ComponentReferenceEntity componentReference;
    private Boolean configurable;

    /**
     * @return The action associated with this access policy.
     */
    @Schema(description = "The action associated with this access policy.",
            allowableValues = "read, write"
    )
    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    /**
     * @return The resource for this access policy.
     */
    @Schema(description = "The resource for this access policy.")
    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    /**
     * @return Component this policy references if applicable.
     */
    @Schema(description = "Component this policy references if applicable.")
    public ComponentReferenceEntity getComponentReference() {
        return componentReference;
    }

    public void setComponentReference(ComponentReferenceEntity componentReference) {
        this.componentReference = componentReference;
    }

    /**
     * @return whether this policy is configurable
     */
    @Schema(description = "Whether this policy is configurable.")
    public Boolean getConfigurable() {
        return configurable;
    }

    public void setConfigurable(Boolean configurable) {
        this.configurable = configurable;
    }
}
