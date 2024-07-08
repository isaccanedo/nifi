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

package org.apache.nifi.controller.queue.clustered.client;

import org.apache.nifi.controller.repository.FlowFileRecord;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class StandardLoadBalanceFlowFileCodec implements LoadBalanceFlowFileCodec {

    @Override
    public void encode(final FlowFileRecord flowFile, final OutputStream destination) throws IOException {
        final DataOutputStream out = new DataOutputStream(destination);

        out.writeInt(flowFile.getAttributes().size());
        for (final Map.Entry<String, String> entry : flowFile.getAttributes().entrySet()) {
            writeString(entry.getKey(), out);
            writeString(entry.getValue(), out);
        }

        out.writeLong(flowFile.getLineageStartDate());
        out.writeLong(flowFile.getEntryDate());
        out.writeLong(flowFile.getPenaltyExpirationMillis());
    }

    private void writeString(final String value, final DataOutputStream out) throws IOException {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

}
