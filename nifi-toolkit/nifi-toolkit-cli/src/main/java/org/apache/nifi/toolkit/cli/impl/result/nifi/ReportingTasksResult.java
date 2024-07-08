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
package org.apache.nifi.toolkit.cli.impl.result.nifi;

import org.apache.nifi.toolkit.cli.api.ResultType;
import org.apache.nifi.toolkit.cli.impl.result.AbstractWritableResult;
import org.apache.nifi.toolkit.cli.impl.result.writer.DynamicTableWriter;
import org.apache.nifi.toolkit.cli.impl.result.writer.Table;
import org.apache.nifi.toolkit.cli.impl.result.writer.TableWriter;
import org.apache.nifi.web.api.dto.ReportingTaskDTO;
import org.apache.nifi.web.api.entity.ReportingTaskEntity;
import org.apache.nifi.web.api.entity.ReportingTasksEntity;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Result for ReportingTasksEntity.
 */
public class ReportingTasksResult extends AbstractWritableResult<ReportingTasksEntity> {

    private final ReportingTasksEntity reportingTasksEntity;

    public ReportingTasksResult(final ResultType resultType, final ReportingTasksEntity reportingTasksEntity) {
        super(resultType);
        this.reportingTasksEntity = Objects.requireNonNull(reportingTasksEntity);
    }

    @Override
    protected void writeSimpleResult(final PrintStream output) throws IOException {
        final Set<ReportingTaskEntity> tasksEntities = reportingTasksEntity.getReportingTasks();
        if (tasksEntities == null) {
            return;
        }

        final List<ReportingTaskDTO> taskDTOS = tasksEntities.stream()
                .map(ReportingTaskEntity::getComponent)
                .sorted(Comparator.comparing(ReportingTaskDTO::getName))
                .collect(Collectors.toList());

        final Table table = new Table.Builder()
                .column("#", 3, 3, false)
                .column("Name", 5, 40, true)
                .column("ID", 36, 36, false)
                .column("Type", 5, 40, true)
                .column("Run Status", 10, 20, false)
                .build();

        for (int i = 0; i < taskDTOS.size(); i++) {
            final ReportingTaskDTO taskDTO = taskDTOS.get(i);
            final String[] typeSplit = taskDTO.getType().split("\\.", -1);
            table.addRow(
                    String.valueOf(i + 1),
                    taskDTO.getName(),
                    taskDTO.getId(),
                    typeSplit[typeSplit.length - 1],
                    taskDTO.getState()
            );
        }

        final TableWriter tableWriter = new DynamicTableWriter();
        tableWriter.write(table, output);
    }

    @Override
    public ReportingTasksEntity getResult() {
        return reportingTasksEntity;
    }
}
