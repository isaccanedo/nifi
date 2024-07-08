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
package org.apache.nifi.toolkit.cli.impl.command.nifi.params;

import org.apache.commons.cli.MissingOptionException;
import org.apache.nifi.toolkit.cli.api.CommandException;
import org.apache.nifi.toolkit.cli.api.Context;
import org.apache.nifi.toolkit.cli.impl.client.nifi.NiFiClient;
import org.apache.nifi.toolkit.cli.impl.client.nifi.NiFiClientException;
import org.apache.nifi.toolkit.cli.impl.client.nifi.ParamContextClient;
import org.apache.nifi.toolkit.cli.impl.command.CommandOption;
import org.apache.nifi.toolkit.cli.impl.command.nifi.AbstractNiFiCommand;
import org.apache.nifi.toolkit.cli.impl.result.nifi.ParamContextResult;
import org.apache.nifi.web.api.entity.ParameterContextEntity;

import java.io.IOException;
import java.util.Properties;

public class GetParamContext extends AbstractNiFiCommand<ParamContextResult> {

    public GetParamContext() {
        super("get-param-context", ParamContextResult.class);
    }

    @Override
    public String getDescription() {
        return "Retrieves a parameter context by id and lists each parameter and its value.";
    }

    @Override
    protected void doInitialize(Context context) {
        addOption(CommandOption.PARAM_CONTEXT_ID.createOption());
        addOption(CommandOption.PARAM_CONTEXT_INCLUDE_INHERITED.createOption());
    }

    @Override
    public ParamContextResult doExecute(final NiFiClient client, final Properties properties)
            throws NiFiClientException, IOException, MissingOptionException, CommandException {
        final String paramContextId = getRequiredArg(properties, CommandOption.PARAM_CONTEXT_ID);
        final boolean includeInheritedParameters = hasArg(properties, CommandOption.PARAM_CONTEXT_INCLUDE_INHERITED);
        final ParamContextClient paramContextClient = client.getParamContextClient();
        final ParameterContextEntity parameterContext = paramContextClient.getParamContext(paramContextId, includeInheritedParameters);
        return new ParamContextResult(getResultType(properties), parameterContext, includeInheritedParameters);
    }
}
