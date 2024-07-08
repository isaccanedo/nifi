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
package org.apache.nifi.c2.protocol.component.api;

import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.nifi.components.resource.ResourceCardinality;
import org.apache.nifi.components.resource.ResourceType;

import java.io.Serializable;
import java.util.Set;

public class PropertyResourceDefinition implements Serializable {
    private static final long serialVersionUID = 1L;

    private ResourceCardinality cardinality;
    private Set<ResourceType> resourceTypes;

    @Schema(description = "The cardinality of the resource definition (i.e. single or multiple)")
    public ResourceCardinality getCardinality() {
        return cardinality;
    }

    public void setCardinality(ResourceCardinality cardinality) {
        this.cardinality = cardinality;
    }

    @Schema(description = "The types of resources that can be referenced")
    public Set<ResourceType> getResourceTypes() {
        return resourceTypes;
    }

    public void setResourceTypes(Set<ResourceType> resourceTypes) {
        this.resourceTypes = resourceTypes;
    }

}
