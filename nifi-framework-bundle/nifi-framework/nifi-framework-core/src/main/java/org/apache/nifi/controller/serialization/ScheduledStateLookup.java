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

package org.apache.nifi.controller.serialization;

import org.apache.nifi.connectable.Port;
import org.apache.nifi.controller.ProcessorNode;
import org.apache.nifi.controller.ScheduledState;
import org.apache.nifi.groups.ProcessGroup;
import org.apache.nifi.groups.StatelessGroupScheduledState;

public interface ScheduledStateLookup {

    ScheduledState getScheduledState(ProcessorNode procNode);

    ScheduledState getScheduledState(Port port);

    ScheduledState getScheduledState(ProcessGroup processGroup);


    ScheduledStateLookup IDENTITY_LOOKUP = new ScheduledStateLookup() {
        @Override
        public ScheduledState getScheduledState(final ProcessorNode procNode) {
            return procNode.getDesiredState();
        }

        @Override
        public ScheduledState getScheduledState(final Port port) {
            return port.getScheduledState();
        }

        @Override
        public ScheduledState getScheduledState(final ProcessGroup processGroup) {
            return processGroup.getDesiredStatelessScheduledState() == StatelessGroupScheduledState.RUNNING ? ScheduledState.RUNNING : ScheduledState.STOPPED;
        }
    };
}
