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

package org.apache.nifi.c2.client.service.operation;

/**
 * Common interface for classes implementing approach for MiNiFi flow update
 */
@FunctionalInterface
public interface UpdateConfigurationStrategy {

    /**
     * Updates the MiNiFi agent's flow with the flow passed as parameter
     *
     * @param flow the MiNiFi flow config JSON represented as a byte array
     * @return true if the flow update was true, false otherwise
     */
    boolean update(byte[] flow);
}
