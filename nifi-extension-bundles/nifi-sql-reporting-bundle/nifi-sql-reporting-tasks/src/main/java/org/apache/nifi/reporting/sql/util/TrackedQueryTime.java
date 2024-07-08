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
package org.apache.nifi.reporting.sql.util;

public enum TrackedQueryTime {

    BULLETIN_START_TIME("$bulletinStartTime"),
    BULLETIN_END_TIME("$bulletinEndTime"),
    PROVENANCE_START_TIME("$provenanceStartTime"),
    PROVENANCE_END_TIME("$provenanceEndTime"),
    FLOW_CONFIG_HISTORY_START_TIME("$flowConfigHistoryStartTime"),
    FLOW_CONFIG_HISTORY_END_TIME("$flowConfigHistoryEndTime");

    private final String sqlPlaceholder;

    TrackedQueryTime(final String sqlPlaceholder) {
        this.sqlPlaceholder = sqlPlaceholder;
    }

    public String getSqlPlaceholder() {
        return sqlPlaceholder;
    }
}
