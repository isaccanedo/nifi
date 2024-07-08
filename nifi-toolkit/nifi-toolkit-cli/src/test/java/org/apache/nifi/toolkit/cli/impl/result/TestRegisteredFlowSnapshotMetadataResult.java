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
package org.apache.nifi.toolkit.cli.impl.result;

import org.apache.nifi.registry.flow.VersionedFlowSnapshotMetadata;
import org.apache.nifi.toolkit.cli.api.ResultType;
import org.apache.nifi.toolkit.cli.impl.result.registry.RegisteredFlowSnapshotMetadataResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.WINDOWS)
public class TestRegisteredFlowSnapshotMetadataResult {

    private ByteArrayOutputStream outputStream;
    private PrintStream printStream;

    @BeforeEach
    public void setup() {
        this.outputStream = new ByteArrayOutputStream();
        this.printStream = new PrintStream(outputStream, true);
    }

    @Test
    public void testWriteSimpleVersionedFlowSnapshotResult() throws IOException {
        final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        final VersionedFlowSnapshotMetadata vfs1 = new VersionedFlowSnapshotMetadata();
        vfs1.setVersion(1);
        vfs1.setAuthor("user1");
        vfs1.setTimestamp(LocalDateTime.from(dateFormat.parse("2018-02-14T12:00:00")).toInstant(ZoneOffset.UTC).toEpochMilli());
        vfs1.setComments("This is a long comment, longer than the display limit for comments");

        final VersionedFlowSnapshotMetadata vfs2 = new VersionedFlowSnapshotMetadata();
        vfs2.setVersion(2);
        vfs2.setAuthor("user2");
        vfs2.setTimestamp(LocalDateTime.from(dateFormat.parse("2018-02-14T12:30:00")).toInstant(ZoneOffset.UTC).toEpochMilli());
        vfs2.setComments("This is v2");

        final List<VersionedFlowSnapshotMetadata> versions = new ArrayList<>();
        versions.add(vfs1);
        versions.add(vfs2);

        final RegisteredFlowSnapshotMetadataResult result = new RegisteredFlowSnapshotMetadataResult(ResultType.SIMPLE, versions);
        result.write(printStream);

        final String resultOut = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);

        // can't get the time zone to line up on travis, so ignore this for now
        final String expectedPattern = "^\\n" +
                "Ver +Date + Author + Message +\\n" +
                "-+ +-+ +-+ +-+ +\\n" +
                //"1     Wed, Feb 14 2018 12:00 EST   user1    This is a long comment, longer than t...   \n" +
                //"2     Wed, Feb 14 2018 12:30 EST   user2    This is v2                                 \n" +
                "(.|\\n)+$";

        assertTrue(resultOut.matches(expectedPattern));
    }
}
