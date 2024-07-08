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
import org.apache.nifi.web.api.dto.ComponentRestrictionPermissionDTO;
import org.apache.nifi.web.api.dto.PermissionsDTO;

import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.Set;

/**
 * A serialized representation of this class can be placed in the entity body of a response to the API. This particular entity holds the users identity.
 */
@XmlRootElement(name = "currentEntity")
public class CurrentUserEntity extends Entity {

    private String identity;
    private boolean anonymous;
    private boolean logoutSupported;

    private PermissionsDTO provenancePermissions;
    private PermissionsDTO countersPermissions;
    private PermissionsDTO tenantsPermissions;
    private PermissionsDTO controllerPermissions;
    private PermissionsDTO policiesPermissions;
    private PermissionsDTO systemPermissions;
    private PermissionsDTO parameterContextPermissions;
    private PermissionsDTO restrictedComponentsPermissions;
    private Set<ComponentRestrictionPermissionDTO> componentRestrictionPermissions;

    private boolean canVersionFlows;

    /**
     * @return the user identity being serialized
     */
    @Schema(description = "The user identity being serialized.")
    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    /**
     * @return if the user is anonymous
     */
    @Schema(description = "Whether the current user is anonymous.")
    public boolean isAnonymous() {
        return anonymous;
    }

    public void setAnonymous(boolean anonymous) {
        this.anonymous = anonymous;
    }

    @Schema(
            description = "Whether the system is configured to support logout operations based on current user authentication status",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    public boolean isLogoutSupported() {
        return logoutSupported;
    }

    public void setLogoutSupported(final boolean logoutSupported) {
        this.logoutSupported = logoutSupported;
    }

    /**
     * @return if the use can query provenance
     */
    @Schema(description = "Permissions for querying provenance.")
    public PermissionsDTO getProvenancePermissions() {
        return provenancePermissions;
    }

    public void setProvenancePermissions(PermissionsDTO provenancePermissions) {
        this.provenancePermissions = provenancePermissions;
    }

    /**
     * @return permissions for accessing counters
     */
    @Schema(description = "Permissions for accessing counters.")
    public PermissionsDTO getCountersPermissions() {
        return countersPermissions;
    }

    public void setCountersPermissions(PermissionsDTO countersPermissions) {
        this.countersPermissions = countersPermissions;
    }

    /**
     * @return permissions for accessing users
     */
    @Schema(description = "Permissions for accessing tenants.")
    public PermissionsDTO getTenantsPermissions() {
        return tenantsPermissions;
    }

    public void setTenantsPermissions(PermissionsDTO tenantsPermissions) {
        this.tenantsPermissions = tenantsPermissions;
    }

    /**
     * @return permissions for accessing the controller
     */
    @Schema(description = "Permissions for accessing the controller.")
    public PermissionsDTO getControllerPermissions() {
        return controllerPermissions;
    }

    public void setControllerPermissions(PermissionsDTO controllerPermissions) {
        this.controllerPermissions = controllerPermissions;
    }

    /**
     * @return permissions for accessing the all policies
     */
    @Schema(description = "Permissions for accessing the policies.")
    public PermissionsDTO getPoliciesPermissions() {
        return policiesPermissions;
    }

    public void setPoliciesPermissions(PermissionsDTO policiesPermissions) {
        this.policiesPermissions = policiesPermissions;
    }

    /**
     * @return permissions for accessing the system
     */
    @Schema(description = "Permissions for accessing system.")
    public PermissionsDTO getSystemPermissions() {
        return systemPermissions;
    }

    public void setSystemPermissions(PermissionsDTO systemPermissions) {
        this.systemPermissions = systemPermissions;
    }

    /**
     * @return permissions for accessing parameter contexts
     */
    @Schema(description = "Permissions for accessing parameter contexts.")
    public PermissionsDTO getParameterContextPermissions() {
        return parameterContextPermissions;
    }

    public void setParameterContextPermissions(PermissionsDTO parameterContextPermissions) {
        this.parameterContextPermissions = parameterContextPermissions;
    }

    /**
     * @return permissions for accessing the restricted components
     */
    @Schema(description = "Permissions for accessing restricted components. Note: the read permission are not used and will always be false.")
    public PermissionsDTO getRestrictedComponentsPermissions() {
        return restrictedComponentsPermissions;
    }

    public void setRestrictedComponentsPermissions(PermissionsDTO restrictedComponentsPermissions) {
        this.restrictedComponentsPermissions = restrictedComponentsPermissions;
    }

    /**
     * @return permissions for specific component restrictions
     */
    @Schema(description = "Permissions for specific component restrictions.")
    public Set<ComponentRestrictionPermissionDTO> getComponentRestrictionPermissions() {
        return componentRestrictionPermissions;
    }

    public void setComponentRestrictionPermissions(Set<ComponentRestrictionPermissionDTO> componentRestrictionPermissions) {
        this.componentRestrictionPermissions = componentRestrictionPermissions;
    }

    /**
     * @return whether the current user can version flows
     */
    @Schema(description = "Whether the current user can version flows.")
    public boolean isCanVersionFlows() {
        return canVersionFlows;
    }

    public void setCanVersionFlows(boolean canVersionFlows) {
        this.canVersionFlows = canVersionFlows;
    }
}
