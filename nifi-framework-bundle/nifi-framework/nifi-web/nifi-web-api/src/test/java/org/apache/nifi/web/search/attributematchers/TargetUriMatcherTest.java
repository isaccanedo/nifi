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
package org.apache.nifi.web.search.attributematchers;

import org.apache.nifi.groups.RemoteProcessGroup;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

public class TargetUriMatcherTest extends AbstractAttributeMatcherTest {
    private static final String TARGET_URIS = "www.lorem.ipsum.com";

    @Mock
    private RemoteProcessGroup component;

    @Test
    public void testMatching() {
        // given
        final TargetUriMatcher testSubject = new TargetUriMatcher();
        Mockito.when(component.getTargetUris()).thenReturn(TARGET_URIS);
        givenSearchTerm("lorem");

        // when
        testSubject.match(component, searchQuery, matches);

        // then
        thenMatchConsistsOf("URLs: " + TARGET_URIS);
    }
}