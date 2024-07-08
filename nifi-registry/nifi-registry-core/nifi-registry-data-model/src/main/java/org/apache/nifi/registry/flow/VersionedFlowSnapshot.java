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

package org.apache.nifi.registry.flow;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.Objects;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import org.apache.nifi.flow.ExternalControllerServiceReference;
import org.apache.nifi.flow.ParameterProviderReference;
import org.apache.nifi.flow.VersionedParameterContext;
import org.apache.nifi.flow.VersionedProcessGroup;
import org.apache.nifi.registry.bucket.Bucket;

/**
 * <p>
 * Represents a snapshot of a versioned flow. A versioned flow may change many times
 * over the course of its life. Each of these versions that is saved to the registry
 * is saved as a snapshot, representing information such as the name of the flow, the
 * version of the flow, the timestamp when it was saved, the contents of the flow, etc.
 * </p>
 * <p>
 * With the advent of download and upload flow in NiFi, this class is now used outside of
 * NiFi Registry to represent a snapshot of an unversioned flow, which is purely the
 * flow contents without any versioning or snapshot metadata.
 * </p>
 */
@XmlRootElement
public class VersionedFlowSnapshot {

    @Valid
    @NotNull
    private VersionedFlowSnapshotMetadata snapshotMetadata;

    @Valid
    @NotNull
    private VersionedProcessGroup flowContents;

    // optional map of external controller service references
    private Map<String, ExternalControllerServiceReference> externalControllerServices;

    // optional map of parameter provider references, mapped by identifier
    private Map<String, ParameterProviderReference> parameterProviders;

    // optional parameter contexts mapped by their name
    private Map<String, VersionedParameterContext> parameterContexts;

    // optional encoding version that clients may specify to track how the flow contents are encoded
    private String flowEncodingVersion;

    // read-only, only populated from retrieval of a snapshot
    private VersionedFlow flow;

    // read-only, only populated from retrieval of a snapshot
    private Bucket bucket;

    @Schema(description = "The metadata for this snapshot")
    public VersionedFlowSnapshotMetadata getSnapshotMetadata() {
        return snapshotMetadata;
    }

    public void setSnapshotMetadata(VersionedFlowSnapshotMetadata snapshotMetadata) {
        this.snapshotMetadata = snapshotMetadata;
    }

    @Schema(description = "The contents of the versioned flow")
    public VersionedProcessGroup getFlowContents() {
        return flowContents;
    }

    public void setFlowContents(VersionedProcessGroup flowContents) {
        this.flowContents = flowContents;
    }

    @Schema(description = "The information about controller services that exist outside this versioned flow, but are referenced by components within the versioned flow.")
    public Map<String, ExternalControllerServiceReference> getExternalControllerServices() {
        return externalControllerServices;
    }

    public void setExternalControllerServices(Map<String, ExternalControllerServiceReference> externalControllerServices) {
        this.externalControllerServices = externalControllerServices;
    }

    @Schema(description = "Contains basic information about parameter providers referenced in the versioned flow.")
    public Map<String, ParameterProviderReference> getParameterProviders() {
        return parameterProviders;
    }

    public void setParameterProviders(final Map<String, ParameterProviderReference> parameterProviders) {
        this.parameterProviders = parameterProviders;
    }

    @Schema(description = "The flow this snapshot is for", accessMode = Schema.AccessMode.READ_ONLY)
    public VersionedFlow getFlow() {
        return flow;
    }

    public void setFlow(VersionedFlow flow) {
        this.flow = flow;
    }

    @Schema(description = "The bucket where the flow is located", accessMode = Schema.AccessMode.READ_ONLY)
    public Bucket getBucket() {
        return bucket;
    }

    public void setBucket(Bucket bucket) {
        this.bucket = bucket;
    }

    @Schema(description = "The parameter contexts referenced by process groups in the flow contents. " +
            "The mapping is from the name of the context to the context instance, and it is expected that any " +
            "context in this map is referenced by at least one process group in this flow.")
    public Map<String, VersionedParameterContext> getParameterContexts() {
        return parameterContexts;
    }

    public void setParameterContexts(Map<String, VersionedParameterContext> parameterContexts) {
        this.parameterContexts = parameterContexts;
    }

    @Schema(description = "The optional encoding version of the flow contents.")
    public String getFlowEncodingVersion() {
        return flowEncodingVersion;
    }

    public void setFlowEncodingVersion(String flowEncodingVersion) {
        this.flowEncodingVersion = flowEncodingVersion;
    }

    /**
     * This is a convenience method that will return true when flow is populated and when the flow's versionCount
     * is equal to the version of this snapshot.
     *
     * @return true if flow is populated and if this snapshot is the latest version for the flow at the time of retrieval
     */
    @XmlTransient
    public boolean isLatest() {
        return flow != null && snapshotMetadata != null && flow.getVersionCount() == getSnapshotMetadata().getVersion();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.snapshotMetadata);
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        final VersionedFlowSnapshot other = (VersionedFlowSnapshot) obj;
        return Objects.equals(this.snapshotMetadata, other.snapshotMetadata);
    }

    @Override
    public String toString() {
        // snapshotMetadata and flow will be null when this is used to represent an unversioned flow
        if (snapshotMetadata == null) {
            return "VersionedFlowSnapshot[flowContentsId=" + flowContents.getIdentifier() + ", flowContentsName="
                    + flowContents.getName() + ", NoMetadataAvailable]";
        } else {
            final String flowName = (flow == null ? "null" : flow.getName());
            return "VersionedFlowSnapshot[flowId=" + snapshotMetadata.getFlowIdentifier() + ", flowName=" + flowName
                    + ", version=" + snapshotMetadata.getVersion() + ", comments=" + snapshotMetadata.getComments() + "]";
        }
    }
}