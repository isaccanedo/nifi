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
package org.apache.nifi.headless;

import org.apache.nifi.action.Action;
import org.apache.nifi.admin.service.AuditService;
import org.apache.nifi.history.History;
import org.apache.nifi.history.HistoryQuery;
import org.apache.nifi.history.PreviousValue;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Headless Audit Service implementation does not persist actions
 */
public class HeadlessAuditService implements AuditService {
    @Override
    public void addActions(Collection<Action> actions) {

    }

    @Override
    public Map<String, List<PreviousValue>> getPreviousValues(String componentId) {
        return null;
    }

    @Override
    public void deletePreviousValues(String propertyName, String componentId) {

    }

    @Override
    public History getActions(HistoryQuery actionQuery) {
        return null;
    }

    @Override
    public History getActions(int firstActionId, int maxActions) {
        return null;
    }

    @Override
    public Action getAction(Integer actionId) {
        return null;
    }

    @Override
    public void purgeActions(Date end, Action purgeAction) {

    }
}
