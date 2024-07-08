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
package org.apache.nifi.toolkit.cli.impl.command.registry.tenant;

import org.apache.nifi.registry.authorization.User;
import org.apache.nifi.registry.client.NiFiRegistryException;
import org.apache.nifi.registry.client.TenantsClient;
import org.apache.nifi.toolkit.cli.impl.result.registry.UsersResult;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

/**
 * Command to get the list of users.
 */
public class ListUsers extends AbstractListTenants<User, UsersResult> {
    public ListUsers() {
        super("list-users", UsersResult.class);
    }

    @Override
    public String getDescription() {
        return "Retrieves the list of user.";
    }

    @Override
    protected UsersResult getTenants(Properties properties, TenantsClient tenantsClient) throws NiFiRegistryException, IOException {
        List<User> users = tenantsClient.getUsers();

        return new UsersResult(getResultType(properties), users);
    }
}
