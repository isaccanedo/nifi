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
import org.apache.nifi.web.api.entity.AllowableValueEntity;

import jakarta.xml.bind.annotation.XmlType;
import java.util.List;

/**
 * A description of a property.
 */
@XmlType(name = "propertyDescriptor")
public class PropertyDescriptorDTO {

    private String name;
    private String displayName;
    private String description;
    private String defaultValue;
    private List<AllowableValueEntity> allowableValues;
    private Boolean required;
    private Boolean sensitive;
    private Boolean dynamic;
    private Boolean supportsEl;
    private String expressionLanguageScope;
    private String identifiesControllerService;
    private BundleDTO identifiesControllerServiceBundle;
    private List<PropertyDependencyDTO> dependencies;

    /**
     * @return set of allowable values for this property. If empty then the allowable values are not constrained
     */
    @Schema(description = "Allowable values for the property. If empty then the allowed values are not constrained."
    )
    public List<AllowableValueEntity> getAllowableValues() {
        return allowableValues;
    }

    public void setAllowableValues(List<AllowableValueEntity> allowableValues) {
        this.allowableValues = allowableValues;
    }

    /**
     * @return default value for this property
     */
    @Schema(description = "The default value for the property."
    )
    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * @return An explanation of the meaning of the given property. This description is meant to be displayed to a user or simply provide a mechanism of documenting intent
     */
    @Schema(description = "The description for the property. Used to relay additional details to a user or provide a mechanism of documenting intent."
    )
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return property name
     */
    @Schema(description = "The name for the property."
    )
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return human-readable name to display to users
     */
    @Schema(description = "The human readable name for the property."
    )
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return whether the property is required for this processor
     */
    @Schema(description = "Whether the property is required."
    )
    public Boolean isRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    /**
     * @return indicates that the value for this property should be considered sensitive and protected whenever stored or represented
     */
    @Schema(description = "Whether the property is sensitive and protected whenever stored or represented."
    )
    public Boolean isSensitive() {
        return sensitive;
    }

    public void setSensitive(Boolean sensitive) {
        this.sensitive = sensitive;
    }

    /**
     * @return indicates whether this property is dynamic
     */
    @Schema(description = "Whether the property is dynamic (user-defined)."
    )
    public Boolean isDynamic() {
        return dynamic;
    }

    public void setDynamic(Boolean dynamic) {
        this.dynamic = dynamic;
    }

    /**
     * @return specifies whether or not this property support expression language
     */
    @Schema(description = "Whether the property supports expression language."
    )
    public Boolean getSupportsEl() {
        return supportsEl;
    }

    public void setSupportsEl(Boolean supportsEl) {
        this.supportsEl = supportsEl;
    }

    /**
     * @return specifies the scope of expression language evaluation
     */
    @Schema(description = "Scope of the Expression Language evaluation for the property."
    )
    public String getExpressionLanguageScope() {
        return expressionLanguageScope;
    }

    public void setExpressionLanguageScope(String scope) {
        this.expressionLanguageScope = scope;
    }

    /**
     * @return if this property identifies a controller service this returns the fully qualified type, null otherwise
     */
    @Schema(description = "If the property identifies a controller service this returns the fully qualified type."
    )
    public String getIdentifiesControllerService() {
        return identifiesControllerService;
    }

    public void setIdentifiesControllerService(String identifiesControllerService) {
        this.identifiesControllerService = identifiesControllerService;
    }

    /**
     * @return if this property identifies a controller service this returns the bundle of the type, null otherwise
     */
    @Schema(description = "If the property identifies a controller service this returns the bundle of the type, null otherwise."
    )
    public BundleDTO getIdentifiesControllerServiceBundle() {
        return identifiesControllerServiceBundle;
    }

    public void setIdentifiesControllerServiceBundle(BundleDTO identifiesControllerServiceBundle) {
        this.identifiesControllerServiceBundle = identifiesControllerServiceBundle;
    }

    @Schema(description = "A list of dependencies that must be met in order for this Property to be relevant. If any of these dependencies is not met, the property described by this " +
        "Property Descriptor is not relevant.")
    public List<PropertyDependencyDTO> getDependencies() {
        return dependencies;
    }

    public void setDependencies(final List<PropertyDependencyDTO> dependencies) {
        this.dependencies = dependencies;
    }
}
