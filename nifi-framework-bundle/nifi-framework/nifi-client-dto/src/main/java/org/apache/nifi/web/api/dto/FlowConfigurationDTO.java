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
import org.apache.nifi.web.api.dto.util.TimeAdapter;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Date;

/**
 * Details for the controller configuration.
 */
@XmlType(name = "flowConfiguration")
public class FlowConfigurationDTO {

    private Boolean supportsManagedAuthorizer;
    private Boolean supportsConfigurableAuthorizer;
    private Boolean supportsConfigurableUsersAndGroups;

    private Date currentTime;
    private Integer timeOffset;

    private Long defaultBackPressureObjectThreshold;
    private String defaultBackPressureDataSizeThreshold;

    /**
     * @return whether this NiFi supports a managed authorizer. Managed authorizers can visualize users, groups,
     * and policies in the UI. This value is read only
     */
    @Schema(description = "Whether this NiFi supports a managed authorizer. Managed authorizers can visualize users, groups, and policies in the UI.",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    public Boolean getSupportsManagedAuthorizer() {
        return supportsManagedAuthorizer;
    }

    public void setSupportsManagedAuthorizer(Boolean supportsManagedAuthorizer) {
        this.supportsManagedAuthorizer = supportsManagedAuthorizer;
    }

    /**
     * @return whether this NiFi supports configurable users and groups. This value is read only
     */
    @Schema(description = "Whether this NiFi supports configurable users and groups.",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    public Boolean getSupportsConfigurableUsersAndGroups() {
        return supportsConfigurableUsersAndGroups;
    }

    public void setSupportsConfigurableUsersAndGroups(Boolean supportsConfigurableUsersAndGroups) {
        this.supportsConfigurableUsersAndGroups = supportsConfigurableUsersAndGroups;
    }

    /**
     * @return whether this NiFi supports a configurable authorizer. This value is read only
     */
    @Schema(description = "Whether this NiFi supports a configurable authorizer.",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    public Boolean getSupportsConfigurableAuthorizer() {
        return supportsConfigurableAuthorizer;
    }

    public void setSupportsConfigurableAuthorizer(Boolean supportsConfigurableAuthorizer) {
        this.supportsConfigurableAuthorizer = supportsConfigurableAuthorizer;
    }

    /**
     * @return current time on the server
     */
    @XmlJavaTypeAdapter(TimeAdapter.class)
    @Schema(description = "The current time on the system.",
            type = "string"
    )
    public Date getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(Date currentTime) {
        this.currentTime = currentTime;
    }

    /**
     * @return time offset of the server
     */
    @Schema(description = "The time offset of the system."
    )
    public Integer getTimeOffset() {
        return timeOffset;
    }

    public void setTimeOffset(Integer timeOffset) {
        this.timeOffset = timeOffset;
    }

    /**
     * @return the default back pressure object threshold
     */
    @Schema(description = "The default back pressure object threshold."
    )
    public Long getDefaultBackPressureObjectThreshold() {
        return defaultBackPressureObjectThreshold;
    }

    public void setDefaultBackPressureObjectThreshold(Long backPressureObjectThreshold) {
        this.defaultBackPressureObjectThreshold = backPressureObjectThreshold;
    }

    /**
     * @return the default back pressure data size threshold
     */
    @Schema(description = "The default back pressure data size threshold."
    )
    public String getDefaultBackPressureDataSizeThreshold() {
        return defaultBackPressureDataSizeThreshold;
    }

    public void setDefaultBackPressureDataSizeThreshold(String backPressureDataSizeThreshold) {
        this.defaultBackPressureDataSizeThreshold = backPressureDataSizeThreshold;
    }
}
