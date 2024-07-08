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

import java.util.Set;

import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.nifi.web.api.dto.ComponentDifferenceDTO;

import io.swagger.v3.oas.annotations.media.Schema;

@XmlRootElement(name = "flowComparisonEntity")
public class FlowComparisonEntity extends Entity {
    private Set<ComponentDifferenceDTO> componentDifferences;

    @Schema(description = "The list of differences for each component in the flow that is not the same between the two flows")
    public Set<ComponentDifferenceDTO> getComponentDifferences() {
        return componentDifferences;
    }

    public void setComponentDifferences(Set<ComponentDifferenceDTO> componentDifferences) {
        this.componentDifferences = componentDifferences;
    }
}
