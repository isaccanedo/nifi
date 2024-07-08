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
package org.apache.nifi.toolkit.cli.impl.command.nifi.pg;

import org.apache.commons.cli.MissingOptionException;
import org.apache.nifi.toolkit.cli.api.CommandException;
import org.apache.nifi.toolkit.cli.api.Context;
import org.apache.nifi.toolkit.cli.impl.client.nifi.NiFiClient;
import org.apache.nifi.toolkit.cli.impl.client.nifi.NiFiClientException;
import org.apache.nifi.toolkit.cli.impl.command.CommandOption;
import org.apache.nifi.toolkit.cli.impl.command.nifi.AbstractNiFiCommand;
import org.apache.nifi.toolkit.cli.impl.result.nifi.VersionControlInfoResult;
import org.apache.nifi.web.api.entity.ProcessGroupEntity;
import org.apache.nifi.web.api.entity.VersionControlInformationEntity;

import java.io.IOException;
import java.util.Properties;

/**
 * Command to get the version control info for a given process group.
 */
public class PGStopVersionControl extends AbstractNiFiCommand<VersionControlInfoResult> {

    public PGStopVersionControl() {
        super("pg-stop-version-control", VersionControlInfoResult.class);
    }

    @Override
    public String getDescription() {
        return "Stops version control for the specified process group.";
    }

    @Override
    protected void doInitialize(final Context context) {
        addOption(CommandOption.PG_ID.createOption());
    }

    @Override
    public VersionControlInfoResult doExecute(final NiFiClient client, final Properties properties)
            throws NiFiClientException, IOException, MissingOptionException, CommandException {
        final String pgId = getRequiredArg(properties, CommandOption.PG_ID);
        final ProcessGroupEntity pgEntity = client.getProcessGroupClient().getProcessGroup(pgId);
        final VersionControlInformationEntity entity = client.getVersionsClient().stopVersionControl(pgEntity);
        return new VersionControlInfoResult(getResultType(properties), entity);
    }

}
