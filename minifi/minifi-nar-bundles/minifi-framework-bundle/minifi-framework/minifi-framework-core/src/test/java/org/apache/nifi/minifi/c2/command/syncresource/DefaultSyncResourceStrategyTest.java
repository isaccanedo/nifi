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

package org.apache.nifi.minifi.c2.command.syncresource;

import static java.lang.Boolean.TRUE;
import static java.nio.file.Files.createTempFile;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static org.apache.nifi.c2.protocol.api.C2OperationState.OperationState.FULLY_APPLIED;
import static org.apache.nifi.c2.protocol.api.C2OperationState.OperationState.NOT_APPLIED;
import static org.apache.nifi.c2.protocol.api.C2OperationState.OperationState.PARTIALLY_APPLIED;
import static org.apache.nifi.c2.protocol.api.ResourceType.ASSET;
import static org.apache.nifi.c2.protocol.api.ResourceType.EXTENSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.nifi.c2.protocol.api.C2OperationState.OperationState;
import org.apache.nifi.c2.protocol.api.ResourceItem;
import org.apache.nifi.c2.protocol.api.ResourceType;
import org.apache.nifi.c2.protocol.api.ResourcesGlobalHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DefaultSyncResourceStrategyTest {

    private static final ResourcesGlobalHash C2_GLOBAL_HASH = resourcesGlobalHash("digest1");

    private static final String FAIL_DOWNLOAD_URL = "fail";

    private static final BiFunction<String, Function<InputStream, Optional<Path>>, Optional<Path>> URL_TO_CONTENT_DOWNLOAD_FUNCTION =
        (url, persistFunction) -> url.endsWith(FAIL_DOWNLOAD_URL) ? empty() : persistFunction.apply(new ByteArrayInputStream(url.getBytes()));

    private static String ENRICH_PREFIX = "pre_";
    private static final Function<String, Optional<String>> PREFIXING_ENRICH_FUNCTION = url -> ofNullable(url).map(arg -> ENRICH_PREFIX + arg);

    @Mock
    private ResourceRepository mockResourceRepository;

    private DefaultSyncResourceStrategy testSyncResourceStrategy;

    @BeforeEach
    public void setup() {
        testSyncResourceStrategy = new DefaultSyncResourceStrategy(mockResourceRepository);
    }

    @Test
    public void testAddingNewItems() {
        List<ResourceItem> c2Items = List.of(
            resourceItem("resource1", "url1", null, ASSET),
            resourceItem("resource2", "url2", "", ASSET),
            resourceItem("resource3", "url3", "path3", ASSET),
            resourceItem("resource4", "url4", null, EXTENSION),
            resourceItem("resource5", "url5", "path5", EXTENSION)
        );
        when(mockResourceRepository.findAllResourceItems()).thenReturn(List.of());
        when(mockResourceRepository.saveResourcesGlobalHash(C2_GLOBAL_HASH)).thenReturn(Optional.of(C2_GLOBAL_HASH));
        c2Items.forEach(resourceItem -> {
            try {
                when(mockResourceRepository.addResourceItem(eq(resourceItem), any())).thenReturn(Optional.of(resourceItem));
            } catch (Exception e) {
            }
        });

        OperationState resultState =
            testSyncResourceStrategy.synchronizeResourceRepository(C2_GLOBAL_HASH, c2Items, URL_TO_CONTENT_DOWNLOAD_FUNCTION, PREFIXING_ENRICH_FUNCTION);

        assertEquals(FULLY_APPLIED, resultState);
        try {
            verify(mockResourceRepository, never()).deleteResourceItem(any());
        } catch (Exception e) {
        }
    }

    @Test
    public void testAddingNewItemWhenBinaryPresent() {
        ResourceItem resourceItem = resourceItem("resource1", "url1", null, ASSET);
        when(mockResourceRepository.findAllResourceItems()).thenReturn(List.of());
        when(mockResourceRepository.saveResourcesGlobalHash(C2_GLOBAL_HASH)).thenReturn(Optional.of(C2_GLOBAL_HASH));
        when(mockResourceRepository.addResourceItem(resourceItem)).thenReturn(Optional.of(resourceItem));
        when(mockResourceRepository.resourceItemBinaryPresent(resourceItem)).thenReturn(TRUE);

        OperationState resultState =
            testSyncResourceStrategy.synchronizeResourceRepository(C2_GLOBAL_HASH, List.of(resourceItem), URL_TO_CONTENT_DOWNLOAD_FUNCTION, PREFIXING_ENRICH_FUNCTION);

        assertEquals(FULLY_APPLIED, resultState);
        try {
            verify(mockResourceRepository, never()).deleteResourceItem(any());
        } catch (Exception e) {
        }
    }

    @ParameterizedTest
    @MethodSource("validResourcePaths")
    public void testAddingNewItemsSuccessWithValidResourcePath(String validResourcePath) {
        List<ResourceItem> c2Items = List.of(
            resourceItem("resource1", "url1", validResourcePath, ASSET)
        );
        when(mockResourceRepository.findAllResourceItems()).thenReturn(List.of());
        when(mockResourceRepository.saveResourcesGlobalHash(C2_GLOBAL_HASH)).thenReturn(Optional.of(C2_GLOBAL_HASH));
        c2Items.forEach(resourceItem -> {
            try {
                when(mockResourceRepository.addResourceItem(eq(resourceItem), any())).thenReturn(Optional.of(resourceItem));
            } catch (Exception e) {
            }
        });

        OperationState resultState =
            testSyncResourceStrategy.synchronizeResourceRepository(C2_GLOBAL_HASH, c2Items, URL_TO_CONTENT_DOWNLOAD_FUNCTION, PREFIXING_ENRICH_FUNCTION);

        assertEquals(FULLY_APPLIED, resultState);
        try {
            verify(mockResourceRepository, never()).deleteResourceItem(any());
        } catch (Exception e) {
        }
    }


    @ParameterizedTest
    @MethodSource("invalidResourcePaths")
    public void testAddingNewItemFailureWhenTypeIsAssetAndPathIsInvalid(String invalidResourcePath) {
        List<ResourceItem> c2Items = List.of(
            resourceItem("resource1", "valid_url", invalidResourcePath, ASSET)
        );
        when(mockResourceRepository.findAllResourceItems()).thenReturn(List.of());

        OperationState resultState =
            testSyncResourceStrategy.synchronizeResourceRepository(C2_GLOBAL_HASH, c2Items, URL_TO_CONTENT_DOWNLOAD_FUNCTION, PREFIXING_ENRICH_FUNCTION);

        assertEquals(NOT_APPLIED, resultState);
        try {
            verify(mockResourceRepository, never()).deleteResourceItem(any());
            verify(mockResourceRepository, never()).addResourceItem(any());
            verify(mockResourceRepository, never()).addResourceItem(any(), any());
            verify(mockResourceRepository, never()).saveResourcesGlobalHash(C2_GLOBAL_HASH);
        } catch (Exception e) {
        }
    }

    @Test
    public void testAddingNewItemFailureDueToIssueWithUrlEnrichment() {
        List<ResourceItem> c2Items = List.of(
            resourceItem("resource1", null, null, ASSET)
        );
        when(mockResourceRepository.findAllResourceItems()).thenReturn(List.of());

        OperationState resultState =
            testSyncResourceStrategy.synchronizeResourceRepository(C2_GLOBAL_HASH, c2Items, URL_TO_CONTENT_DOWNLOAD_FUNCTION, PREFIXING_ENRICH_FUNCTION);

        assertEquals(NOT_APPLIED, resultState);
        try {
            verify(mockResourceRepository, never()).deleteResourceItem(any());
            verify(mockResourceRepository, never()).saveResourcesGlobalHash(C2_GLOBAL_HASH);
        } catch (Exception e) {
        }
    }

    @Test
    public void testAddingNewItemFailureDueToIssueInDownloadFunction() {
        List<ResourceItem> c2Items = List.of(
            resourceItem("resource1", FAIL_DOWNLOAD_URL, null, ASSET)
        );
        when(mockResourceRepository.findAllResourceItems()).thenReturn(List.of());

        OperationState resultState =
            testSyncResourceStrategy.synchronizeResourceRepository(C2_GLOBAL_HASH, c2Items, URL_TO_CONTENT_DOWNLOAD_FUNCTION, PREFIXING_ENRICH_FUNCTION);

        assertEquals(NOT_APPLIED, resultState);
        try {
            verify(mockResourceRepository, never()).deleteResourceItem(any());
            verify(mockResourceRepository, never()).saveResourcesGlobalHash(C2_GLOBAL_HASH);
        } catch (Exception e) {
        }
    }

    @Test
    public void testAddingNewItemFailureDueToIssueInPersistFunction() {
        List<ResourceItem> c2Items = List.of(
            resourceItem("resource1", "url1", null, ASSET)
        );
        when(mockResourceRepository.findAllResourceItems()).thenReturn(List.of());

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> createTempFile(anyString(), eq(null))).thenThrow(IOException.class);

            OperationState resultState =
                testSyncResourceStrategy.synchronizeResourceRepository(C2_GLOBAL_HASH, c2Items, URL_TO_CONTENT_DOWNLOAD_FUNCTION, PREFIXING_ENRICH_FUNCTION);

            assertEquals(NOT_APPLIED, resultState);
            try {
                verify(mockResourceRepository, never()).deleteResourceItem(any());
                verify(mockResourceRepository, never()).saveResourcesGlobalHash(C2_GLOBAL_HASH);
            } catch (Exception e) {
            }
        }
    }

    @Test
    public void testAddingNewItemFailureDueToIssueWhenUpdatingRepository() {
        ResourceItem resourceItem = resourceItem("resource1", "url1", null, ASSET);
        List<ResourceItem> c2Items = List.of(resourceItem);
        when(mockResourceRepository.findAllResourceItems()).thenReturn(List.of());
        try {
            when(mockResourceRepository.addResourceItem(resourceItem)).thenThrow(Exception.class);
        } catch (Exception e) {
        }

        OperationState resultState =
            testSyncResourceStrategy.synchronizeResourceRepository(C2_GLOBAL_HASH, c2Items, URL_TO_CONTENT_DOWNLOAD_FUNCTION, PREFIXING_ENRICH_FUNCTION);

        assertEquals(NOT_APPLIED, resultState);
        try {
            verify(mockResourceRepository, never()).deleteResourceItem(any());
            verify(mockResourceRepository, never()).saveResourcesGlobalHash(C2_GLOBAL_HASH);
        } catch (Exception e) {
        }
    }

    @Test
    public void testDeletingAllItems() {
        List<ResourceItem> c2Items = List.of();
        List<ResourceItem> agentItems = List.of(
            resourceItem("resource1", "url1", null, ASSET),
            resourceItem("resource2", "url2", null, ASSET),
            resourceItem("resource3", "url3", null, EXTENSION)
        );
        when(mockResourceRepository.findAllResourceItems()).thenReturn(agentItems);
        when(mockResourceRepository.saveResourcesGlobalHash(C2_GLOBAL_HASH)).thenReturn(Optional.of(C2_GLOBAL_HASH));
        agentItems.forEach(agentItem -> {
            try {
                when(mockResourceRepository.deleteResourceItem(agentItem)).thenReturn(Optional.of(agentItem));
            } catch (Exception e) {
            }
        });

        OperationState resultState =
            testSyncResourceStrategy.synchronizeResourceRepository(C2_GLOBAL_HASH, c2Items, URL_TO_CONTENT_DOWNLOAD_FUNCTION, PREFIXING_ENRICH_FUNCTION);

        assertEquals(FULLY_APPLIED, resultState);
        try {
            verify(mockResourceRepository, never()).addResourceItem(any());
        } catch (Exception e) {
        }
    }

    @Test
    public void testDeleteFailureDueToIssueWithUpdatingRepository() {
        List<ResourceItem> c2Items = List.of();
        List<ResourceItem> agentItems = List.of(
            resourceItem("resource1", "url1", null, ASSET)
        );
        when(mockResourceRepository.findAllResourceItems()).thenReturn(agentItems);
        agentItems.forEach(agentItem -> {
            try {
                when(mockResourceRepository.deleteResourceItem(agentItem)).thenThrow(Exception.class);
            } catch (Exception e) {
            }
        });

        OperationState resultState =
            testSyncResourceStrategy.synchronizeResourceRepository(C2_GLOBAL_HASH, c2Items, URL_TO_CONTENT_DOWNLOAD_FUNCTION, PREFIXING_ENRICH_FUNCTION);

        assertEquals(NOT_APPLIED, resultState);
        try {
            verify(mockResourceRepository, never()).addResourceItem(any());
            verify(mockResourceRepository, never()).saveResourcesGlobalHash(C2_GLOBAL_HASH);
        } catch (Exception e) {
        }
    }

    @Test
    public void testAddFileSuccessfulButUpdateGlobalHashFails() {
        ResourceItem c2Item = resourceItem("resource1", "url1", null, ASSET);
        when(mockResourceRepository.findAllResourceItems()).thenReturn(List.of());
        try {
            when(mockResourceRepository.addResourceItem(eq(c2Item), any())).thenReturn(Optional.of(c2Item));
            when(mockResourceRepository.saveResourcesGlobalHash(C2_GLOBAL_HASH)).thenThrow(Exception.class);
        } catch (Exception e) {
        }

        OperationState resultState =
            testSyncResourceStrategy.synchronizeResourceRepository(C2_GLOBAL_HASH, List.of(c2Item), URL_TO_CONTENT_DOWNLOAD_FUNCTION, PREFIXING_ENRICH_FUNCTION);

        assertEquals(PARTIALLY_APPLIED, resultState);
        try {
            verify(mockResourceRepository, never()).deleteResourceItem(any());
        } catch (Exception e) {
        }
    }

    private static Stream<Arguments> validResourcePaths() {
        return Stream.of(
                null,
                "",
                "sub-folder",
                "sub-folder/",
                "sub-folder\\",
                "./sub-folder",
                "./sub-folder/",
                ".\\sub-folder",
                ".\\sub-folder\\",
                "./sub-folder/sub-sub-folder",
                "./sub-folder/sub-sub-folder/",
                ".\\sub-folder\\sub-sub-folder",
                ".\\sub-folder\\sub-sub-folder\\",
                "./sub-folder/sub-sub-folder/sub-sub-sub-folder",
                "./sub-folder/sub-sub-folder/sub-sub-sub-folder/",
                ".\\sub-folder\\sub-sub-folder\\sub-sub-sub-folder",
                ".\\sub-folder\\sub-sub-folder\\sub-sub-sub-folder\\"
            )
            .map(Arguments::of);
    }

    private static Stream<Arguments> invalidResourcePaths() {
        return Stream.of(
                "~",
                "~/",
                "~\\",
                "../sub-folder",
                "sub-folder/../..",
                "/relative-path/../..",
                "sub-folder/../sub-sub-folder",
                "/relative-path/../sub-sub-folder",
                "./sub-folder/../sub-sub-folder",
                "..\\sub-folder",
                "sub-folder\\..\\..",
                "\\relative-path\\..\\..",
                "sub-folder\\..\\sub-sub-folder",
                "\\relative-path\\..\\sub-sub-folder",
                ".\\sub-folder\\..\\sub-sub-folder",
                "sub-folder/..",
                "./sub-folder/..",
                "sub-folder\\..",
                ".\\sub-folder\\..",
                "sub-folder/../sub-sub-folder",
                "./sub-folder/../sub-sub-folder",
                "sub-folder\\..\\sub-sub-folder",
                ".\\sub-folder\\..\\sub-sub-folder",
                "invalid-char-in-path-<",
                "invalid-char-in-path->",
                "invalid-char-in-path-:",
                "invalid-char-in-path-|",
                "invalid-char-in-path-?",
                "invalid-char-in-path-*",
                "invalid-char-in-path-~",
                "sub-folder/invalid-char-in-path-~",
                "/absolute-path",
                "/absolute-path/..",
                "/absolute-path/invalid-char-in-path-~",
                "\\absolute-path",
                "\\absolute-path\\..",
                "\\absolute-path\\invalid-char-in-path-~",
                "C:\\",
                "C:\\path",
                "C:/",
                "C:/path"
            )
            .map(Arguments::of);
    }

    private static ResourcesGlobalHash resourcesGlobalHash(String digest) {
        ResourcesGlobalHash resourcesGlobalHash = new ResourcesGlobalHash();
        resourcesGlobalHash.setDigest(digest);
        return resourcesGlobalHash;
    }

    private ResourceItem resourceItem(String name, String url, String path, ResourceType resourceType) {
        ResourceItem resourceItem = new ResourceItem();
        resourceItem.setResourceName(name);
        resourceItem.setUrl(url);
        resourceItem.setResourceType(resourceType);
        resourceItem.setResourcePath(path);
        return resourceItem;
    }
}
