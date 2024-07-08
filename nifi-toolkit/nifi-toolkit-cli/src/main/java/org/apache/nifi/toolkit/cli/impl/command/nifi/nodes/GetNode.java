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
package org.apache.nifi.toolkit.cli.impl.command.nifi.nodes;

import org.apache.commons.cli.MissingOptionException;
import org.apache.nifi.toolkit.cli.api.Context;
import org.apache.nifi.toolkit.cli.impl.client.nifi.ControllerClient;
import org.apache.nifi.toolkit.cli.impl.client.nifi.NiFiClient;
import org.apache.nifi.toolkit.cli.impl.client.nifi.NiFiClientException;
import org.apache.nifi.toolkit.cli.impl.command.CommandOption;
import org.apache.nifi.toolkit.cli.impl.command.nifi.AbstractNiFiCommand;
import org.apache.nifi.toolkit.cli.impl.result.nifi.NodeResult;
import org.apache.nifi.web.api.entity.NodeEntity;

import java.io.IOException;
import java.util.Properties;

/**
 * Command for retrieving the status of the nodes from the NiFi cluster.
 */
public class GetNode extends AbstractNiFiCommand<NodeResult> {

    public GetNode() {
        super("get-node", NodeResult.class);
    }

    @Override
    public String getDescription() {
        return "Retrieves the status for a node in the NiFi cluster.";
    }

    @Override
    protected void doInitialize(Context context) {
        addOption(CommandOption.NIFI_NODE_ID.createOption());
    }

    @Override
    public NodeResult doExecute(NiFiClient client, Properties properties) throws NiFiClientException, IOException, MissingOptionException {
        final String nodeId = getRequiredArg(properties, CommandOption.NIFI_NODE_ID);
        final ControllerClient controllerClient = client.getControllerClient();

        NodeEntity nodeEntityResult = controllerClient.getNode(nodeId);
        return new NodeResult(getResultType(properties), nodeEntityResult);
    }
}
