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
package org.apache.nifi.toolkit.cli.impl.result.registry;

import org.apache.nifi.registry.extension.repo.ExtensionRepoVersionSummary;
import org.apache.nifi.toolkit.cli.api.ResultType;
import org.apache.nifi.toolkit.cli.impl.result.AbstractWritableResult;
import org.apache.nifi.toolkit.cli.impl.result.writer.DynamicTableWriter;
import org.apache.nifi.toolkit.cli.impl.result.writer.Table;
import org.apache.nifi.toolkit.cli.impl.result.writer.TableWriter;

import java.io.PrintStream;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class ExtensionRepoVersionSummariesResult extends AbstractWritableResult<List<ExtensionRepoVersionSummary>> {

    private final List<ExtensionRepoVersionSummary> bundleVersions;

    public ExtensionRepoVersionSummariesResult(final ResultType resultType, final List<ExtensionRepoVersionSummary> bundleVersions) {
        super(resultType);
        this.bundleVersions = Objects.requireNonNull(bundleVersions);

        this.bundleVersions.sort(
                Comparator.comparing(ExtensionRepoVersionSummary::getBucketName)
                        .thenComparing(ExtensionRepoVersionSummary::getGroupId)
                        .thenComparing(ExtensionRepoVersionSummary::getArtifactId)
                        .thenComparing(ExtensionRepoVersionSummary::getVersion)
        );
    }

    @Override
    protected void writeSimpleResult(final PrintStream output) {
        if (bundleVersions.isEmpty()) {
            return;
        }

        final Table table = new Table.Builder()
                .column("#", 3, 3, false)
                .column("Bucket", 40, 400, false)
                .column("Group", 40, 200, false)
                .column("Artifact", 40, 200, false)
                .column("Version", 8, 100, false)
                .build();

        for (int i = 0; i < bundleVersions.size(); ++i) {
            final ExtensionRepoVersionSummary version = bundleVersions.get(i);
            table.addRow(String.valueOf(i + 1), version.getBucketName(), version.getGroupId(), version.getArtifactId(), version.getVersion());
        }

        final TableWriter tableWriter = new DynamicTableWriter();
        tableWriter.write(table, output);
    }

    @Override
    public List<ExtensionRepoVersionSummary> getResult() {
        return bundleVersions;
    }

}
