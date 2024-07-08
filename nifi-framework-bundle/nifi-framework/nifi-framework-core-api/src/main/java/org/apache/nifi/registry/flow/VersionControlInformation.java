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

import org.apache.nifi.flow.VersionedProcessGroup;

/**
 * <p>
 * Provides a mechanism for conveying which Flow Registry a flow is stored in, and
 * where in the Flow Registry the flow is stored.
 * </p>
 */
public interface VersionControlInformation {

    /**
     * @return the unique identifier of the Flow Registry that this flow is tracking to
     */
    String getRegistryIdentifier();

    /**
     * @return the name of the Flow Registry that this flow is tracking to
     */
    String getRegistryName();

    /**
     * @return the name of the branch that the flow is tracking to
     */
    String getBranch();

    /**
     * @return the unique identifier of the bucket that this flow belongs to
     */
    String getBucketIdentifier();

    /**
     * @return the name of the bucket that this flow belongs to
     */
    String getBucketName();

    /**
     * @return the unique identifier of this flow in the Flow Registry
     */
    String getFlowIdentifier();

    /**
     * @return the name of the flow
     */
    String getFlowName();

    /**
     * @return the description of the flow
     */
    String getFlowDescription();

    /**
     * @return the storage location of the flow
     */
    String getStorageLocation();

    /**
     * @return the version of the flow in the Flow Registry that this flow is based on.
     */
    String getVersion();

    /**
     * @return the current status of the Process Group as it relates to the associated Versioned Flow.
     */
    VersionedFlowStatus getStatus();

    /**
     * @return the snapshot of the flow that was synchronized with the Flow Registry
     */
    VersionedProcessGroup getFlowSnapshot();
}
