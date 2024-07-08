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
package org.apache.nifi.apicurio.schemaregistry.client;

import org.apache.commons.io.IOUtils;
import org.apache.nifi.schema.access.SchemaNotFoundException;
import org.apache.nifi.serialization.record.RecordSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApicurioSchemaRegistryClientTest {

    private static final String TEST_URL = "http://test.apicurio-schema-registry.com:8888";
    private static final String ARTIFACT_ID = "artifactId1";
    private static final int VERSION = 3;
    private static final String SCHEMA_ARTIFACT_URL = TEST_URL + "/schema/" + ARTIFACT_ID;
    private static final String SCHEMA_VERSION_URL = TEST_URL + "/schema/versions/" + VERSION;
    @Mock
    private SchemaRegistryApiClient apiClient;
    private ApicurioSchemaRegistryClient schemaRegistryClient;

    @Test
    void testGetSchemaWithIdInvokesApiClientAndReturnsRecordSchema() throws IOException, SchemaNotFoundException {
        doReturn(URI.create(SCHEMA_ARTIFACT_URL)).when(apiClient).buildSchemaArtifactUri(ARTIFACT_ID);
        doReturn(getResource("schema_response_version_latest.json")).when(apiClient).retrieveResponse(URI.create(SCHEMA_ARTIFACT_URL));

        schemaRegistryClient = new ApicurioSchemaRegistryClient(apiClient);

        final RecordSchema schema = schemaRegistryClient.getSchema(ARTIFACT_ID, OptionalInt.empty());

        verify(apiClient).buildSchemaArtifactUri(ARTIFACT_ID);
        verify(apiClient, never()).buildSchemaVersionUri(ARTIFACT_ID, VERSION);

        final String expectedSchemaText = IOUtils.toString(getResource("schema_response_version_latest.json"), Charset.defaultCharset())
                .replace("\n", "")
                .replaceAll(" +", "");
        assertEquals(expectedSchemaText, schema.getSchemaText().orElseThrow(() -> new AssertionError("Schema Text is empty")));
    }

    @Test
    void testGetSchemaWithIdAndVersionInvokesApiClientAndReturnsRecordSchema() throws IOException, SchemaNotFoundException {
        doReturn(URI.create(SCHEMA_VERSION_URL)).when(apiClient).buildSchemaVersionUri(ARTIFACT_ID, VERSION);
        doReturn(getResource("schema_response_version_3.json")).when(apiClient).retrieveResponse(URI.create(SCHEMA_VERSION_URL));

        schemaRegistryClient = new ApicurioSchemaRegistryClient(apiClient);

        final RecordSchema schema = schemaRegistryClient.getSchema(ARTIFACT_ID, OptionalInt.of(VERSION));

        verify(apiClient, never()).buildSchemaArtifactUri(ARTIFACT_ID);
        verify(apiClient).buildSchemaVersionUri(ARTIFACT_ID, VERSION);

        final String expectedSchemaText = IOUtils.toString(getResource("schema_response_version_3.json"), Charset.defaultCharset())
                .replace("\n", "")
                .replaceAll(" +", "");
        assertEquals(expectedSchemaText, schema.getSchemaText().orElseThrow(() -> new AssertionError("Schema Text is empty")));
    }

    private InputStream getResource(final String resourceName) {
        return this.getClass().getClassLoader().getResourceAsStream(resourceName);
    }
}
