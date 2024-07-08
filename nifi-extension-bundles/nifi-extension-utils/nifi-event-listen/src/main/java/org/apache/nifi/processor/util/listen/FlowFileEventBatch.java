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
package org.apache.nifi.processor.util.listen;

import org.apache.nifi.event.transport.NetworkEvent;
import org.apache.nifi.flowfile.FlowFile;

import java.util.List;

public final class FlowFileEventBatch<E extends NetworkEvent> {

        private FlowFile flowFile;
        private List<E> events;

        public FlowFileEventBatch(final FlowFile flowFile, final List<E> events) {
            this.flowFile = flowFile;
            this.events = events;
        }

        public FlowFile getFlowFile() {
            return flowFile;
        }

        public List<E> getEvents() {
            return events;
        }

        public void setFlowFile(FlowFile flowFile) {
            this.flowFile = flowFile;
        }
}
