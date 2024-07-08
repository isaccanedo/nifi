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
package org.apache.nifi.processors.asana.utils;

import com.asana.models.Team;
import com.asana.models.User;
import java.util.stream.Stream;
import org.apache.nifi.controller.asana.AsanaClient;

import java.util.HashMap;
import java.util.Map;

public class AsanaTeamMemberFetcher extends GenericAsanaObjectFetcher<User> {
    private static final String TEAM_GID = ".team.gid";

    private final AsanaClient client;
    private final Team team;

    public AsanaTeamMemberFetcher(AsanaClient client, String teamName) {
        super();
        this.client = client;
        this.team = client.getTeamByName(teamName);
    }

    @Override
    public Map<String, String> saveState() {
        Map<String, String> state = new HashMap<>(super.saveState());
        state.put(this.getClass().getName() + TEAM_GID, team.gid);
        return state;
    }

    @Override
    public void loadState(Map<String, String> state) {
        if (!team.gid.equals(state.get(this.getClass().getName() + TEAM_GID))) {
            throw new AsanaObjectFetcherException("Team gid does not match.");
        }
        super.loadState(state);
    }

    @Override
    protected Stream<User> fetchObjects() {
        return client.getTeamMembers(team);
    }
}
