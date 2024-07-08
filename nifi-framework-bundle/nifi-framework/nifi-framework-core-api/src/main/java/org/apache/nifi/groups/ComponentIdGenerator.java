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

package org.apache.nifi.groups;

public interface ComponentIdGenerator {
    /**
     * Generates a UUID based on the proposed component identifier and the UUID of the Process Group into which the component
     * is going to be placed
     *
     * @param proposedId the proposed id, which is not guaranteed to be unique
     * @param instanceId the instance ID of the Versioned component, or null if no instance ID is set
     * @param destinationGroupId the UUID of the Process Group that the component is to be added to
     * @return a UUID for the component
     */
    String generateUuid(String proposedId, String instanceId, String destinationGroupId);
}
