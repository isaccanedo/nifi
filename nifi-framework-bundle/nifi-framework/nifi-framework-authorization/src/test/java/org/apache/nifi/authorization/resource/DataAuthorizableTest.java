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
package org.apache.nifi.authorization.resource;

import org.apache.nifi.authorization.AccessDeniedException;
import org.apache.nifi.authorization.AuthorizationRequest;
import org.apache.nifi.authorization.AuthorizationResult;
import org.apache.nifi.authorization.AuthorizationResult.Result;
import org.apache.nifi.authorization.Authorizer;
import org.apache.nifi.authorization.RequestAction;
import org.apache.nifi.authorization.user.NiFiUser;
import org.apache.nifi.authorization.user.StandardNiFiUser.Builder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DataAuthorizableTest {

    private static final String IDENTITY_1 = "identity-1";
    private static final String PROXY_1 = "proxy-1";
    private static final String PROXY_2 = "proxy-2";

    private Authorizable testProcessorAuthorizable;
    private Authorizer testAuthorizer;
    private DataAuthorizable testDataAuthorizable;

    @BeforeEach
    public void setup() {
        testProcessorAuthorizable = mock(Authorizable.class);
        when(testProcessorAuthorizable.getParentAuthorizable()).thenReturn(null);
        when(testProcessorAuthorizable.getResource()).thenReturn(ResourceFactory.getComponentResource(ResourceType.Processor, "id", "name"));

        testAuthorizer = mock(Authorizer.class);
        when(testAuthorizer.authorize(any(AuthorizationRequest.class))).then(invocation -> {
            final AuthorizationRequest request = invocation.getArgument(0);

            if (IDENTITY_1.equals(request.getIdentity())) {
                return AuthorizationResult.approved();
            } else if (PROXY_1.equals(request.getIdentity())) {
                return AuthorizationResult.approved();
            } else if (PROXY_2.equals(request.getIdentity())) {
                return AuthorizationResult.approved();
            }

            return AuthorizationResult.denied();
        });

        testDataAuthorizable = new DataAuthorizable(testProcessorAuthorizable);
    }

    @Test
    public void testAuthorizeNullUser() {
        assertThrows(AccessDeniedException.class, () ->
                testDataAuthorizable.authorize(testAuthorizer, RequestAction.READ,
                        null, null));
    }

    @Test
    public void testCheckAuthorizationNullUser() {
        final AuthorizationResult result = testDataAuthorizable.checkAuthorization(testAuthorizer, RequestAction.READ, null, null);
        assertEquals(Result.Denied, result.getResult());
    }

    @Test
    public void testAuthorizeUnauthorizedUser() {
        final NiFiUser user = new Builder().identity("unknown").build();

        assertThrows(AccessDeniedException.class, () ->
            testDataAuthorizable.authorize(testAuthorizer, RequestAction.READ, user, null));
    }

    @Test
    public void testCheckAuthorizationUnauthorizedUser() {
        final NiFiUser user = new Builder().identity("unknown").build();
        final AuthorizationResult result = testDataAuthorizable.checkAuthorization(testAuthorizer, RequestAction.READ, user, null);
        assertEquals(Result.Denied, result.getResult());
    }

    @Test
    public void testAuthorizedUser() {
        final NiFiUser user = new Builder().identity(IDENTITY_1).build();
        testDataAuthorizable.authorize(testAuthorizer, RequestAction.READ, user, null);

        verify(testAuthorizer, times(1)).authorize(argThat(o -> IDENTITY_1.equals(o.getIdentity())));
    }

    @Test
    public void testCheckAuthorizationUser() {
        final NiFiUser user = new Builder().identity(IDENTITY_1).build();
        final AuthorizationResult result = testDataAuthorizable.checkAuthorization(testAuthorizer, RequestAction.READ, user, null);

        assertEquals(Result.Approved, result.getResult());
        verify(testAuthorizer, times(1)).authorize(argThat(o -> IDENTITY_1.equals(o.getIdentity())));
    }

    @Test
    public void testAuthorizedUserChain() {
        final NiFiUser proxy2 = new Builder().identity(PROXY_2).build();
        final NiFiUser proxy1 = new Builder().identity(PROXY_1).chain(proxy2).build();
        final NiFiUser user = new Builder().identity(IDENTITY_1).chain(proxy1).build();
        testDataAuthorizable.authorize(testAuthorizer, RequestAction.READ, user, null);

        verify(testAuthorizer, times(3)).authorize(any(AuthorizationRequest.class));
        verifyAuthorizeForUser(IDENTITY_1);
        verifyAuthorizeForUser(PROXY_1);
        verifyAuthorizeForUser(PROXY_2);
    }

    @Test
    public void testCheckAuthorizationUserChain() {
        final NiFiUser proxy2 = new Builder().identity(PROXY_2).build();
        final NiFiUser proxy1 = new Builder().identity(PROXY_1).chain(proxy2).build();
        final NiFiUser user = new Builder().identity(IDENTITY_1).chain(proxy1).build();
        final AuthorizationResult result = testDataAuthorizable.checkAuthorization(testAuthorizer, RequestAction.READ, user, null);

        assertEquals(Result.Approved, result.getResult());
        verify(testAuthorizer, times(3)).authorize(any(AuthorizationRequest.class));
        verifyAuthorizeForUser(IDENTITY_1);
        verifyAuthorizeForUser(PROXY_1);
        verifyAuthorizeForUser(PROXY_2);
    }

    private void verifyAuthorizeForUser(final String identity) {
        verify(testAuthorizer, times(1)).authorize(argThat(o -> identity.equals(o.getIdentity())));
    }

}
