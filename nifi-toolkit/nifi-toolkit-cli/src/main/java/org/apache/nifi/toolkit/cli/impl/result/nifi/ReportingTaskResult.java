/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.toolkit.cli.impl.result.nifi;

import org.apache.nifi.toolkit.cli.api.ResultType;
import org.apache.nifi.toolkit.cli.impl.result.AbstractWritableResult;
import org.apache.nifi.web.api.dto.BundleDTO;
import org.apache.nifi.web.api.dto.ReportingTaskDTO;
import org.apache.nifi.web.api.entity.ReportingTaskEntity;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;

public class ReportingTaskResult extends AbstractWritableResult<ReportingTaskEntity> {

    private final ReportingTaskEntity reportingTaskEntity;

    public ReportingTaskResult(final ResultType resultType, final ReportingTaskEntity reportingTaskEntity) {
        super(resultType);
        this.reportingTaskEntity = Objects.requireNonNull(reportingTaskEntity);
    }

    @Override
    public ReportingTaskEntity getResult() {
        return reportingTaskEntity;
    }

    @Override
    protected void writeSimpleResult(final PrintStream output) throws IOException {
        final ReportingTaskDTO reportingTaskDTO = reportingTaskEntity.getComponent();

        final BundleDTO bundle = reportingTaskDTO.getBundle();
        output.printf("Name  : %s\nID    : %s\nType  : %s\nBundle: %s - %s %s\nState : %s\n",
                reportingTaskDTO.getName(), reportingTaskDTO.getId(), reportingTaskDTO.getType(),
                bundle.getGroup(), bundle.getArtifact(), bundle.getVersion(), reportingTaskDTO.getState());
    }
}
