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
package org.apache.nifi.authorization;

import org.apache.nifi.attribute.expression.language.StandardPropertyValue;
import org.apache.nifi.authorization.exception.AuthorizerCreationException;
import org.apache.nifi.parameter.ParameterLookup;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.nifi.authorization.CompositeConfigurableUserGroupProvider.PROP_CONFIGURABLE_USER_GROUP_PROVIDER;
import static org.apache.nifi.authorization.CompositeUserGroupProvider.PROP_USER_GROUP_PROVIDER_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CompositeConfigurableUserGroupProviderTest extends CompositeUserGroupProviderTestBase {

    public static final String USER_5_IDENTIFIER = "user-identifier-5";
    public static final String USER_5_IDENTITY = "user-identity-5";

    public static final String CONFIGURABLE_USER_GROUP_PROVIDER = "configurable-user-group-provider";
    public static final String NOT_CONFIGURABLE_USER_GROUP_PROVIDER = "not-configurable-user-group-provider";

    @Test
    public void testNoConfigurableUserGroupProviderSpecified() {
        assertThrows(AuthorizerCreationException.class, () -> {
            initCompositeUserGroupProvider(new CompositeConfigurableUserGroupProvider(), null, configurationContext -> {
                when(configurationContext.getProperty(PROP_CONFIGURABLE_USER_GROUP_PROVIDER)).thenReturn(new StandardPropertyValue(null, null, ParameterLookup.EMPTY));
            });
        });
    }

    @Test
    public void testUnknownConfigurableUserGroupProvider() {
        assertThrows(AuthorizerCreationException.class, () -> {
            initCompositeUserGroupProvider(new CompositeConfigurableUserGroupProvider(), null, configurationContext -> {
                when(configurationContext.getProperty(PROP_CONFIGURABLE_USER_GROUP_PROVIDER)).thenReturn(new StandardPropertyValue("unknown-user-group-provider", null, ParameterLookup.EMPTY));
            });
        });
    }

    @Test
    public void testNonConfigurableUserGroupProvider() {
        assertThrows(AuthorizerCreationException.class, () -> {
            initCompositeUserGroupProvider(new CompositeConfigurableUserGroupProvider(), lookup -> {
                when(lookup.getUserGroupProvider(eq(NOT_CONFIGURABLE_USER_GROUP_PROVIDER))).thenReturn(mock(UserGroupProvider.class));
            }, configurationContext -> {
                when(configurationContext.getProperty(PROP_CONFIGURABLE_USER_GROUP_PROVIDER)).thenReturn(new StandardPropertyValue(NOT_CONFIGURABLE_USER_GROUP_PROVIDER, null, ParameterLookup.EMPTY));
            });
        });
    }

    @Test
    public void testDuplicateProviders() {

        // Mock UserGroupProviderLookup
        UserGroupProvider configurableUserGroupProvider = getConfigurableUserGroupProvider();
        final UserGroupProviderLookup ugpLookup = mock(UserGroupProviderLookup.class);
        when(ugpLookup.getUserGroupProvider(eq(CONFIGURABLE_USER_GROUP_PROVIDER))).thenReturn(configurableUserGroupProvider);

        // Mock AuthorizerInitializationContext
        final AuthorizerInitializationContext initializationContext = mock(AuthorizerInitializationContext.class);
        when(initializationContext.getUserGroupProviderLookup()).thenReturn(ugpLookup);

        // Mock AuthorizerConfigurationContext to introduce the duplicate provider ids
        final AuthorizerConfigurationContext configurationContext = mock(AuthorizerConfigurationContext.class);
        when(configurationContext.getProperty(PROP_CONFIGURABLE_USER_GROUP_PROVIDER)).thenReturn(new StandardPropertyValue(CONFIGURABLE_USER_GROUP_PROVIDER, null, ParameterLookup.EMPTY));
        Map<String, String> configurationContextProperties = new HashMap<>();
        configurationContextProperties.put(PROP_USER_GROUP_PROVIDER_PREFIX + "1", CONFIGURABLE_USER_GROUP_PROVIDER);
        configurationContextProperties.put(PROP_USER_GROUP_PROVIDER_PREFIX + "2", NOT_CONFIGURABLE_USER_GROUP_PROVIDER);
        when(configurationContext.getProperties()).thenReturn(configurationContextProperties);

        // configure (should throw exception)
        assertThrows(AuthorizerCreationException.class, () -> {
            CompositeConfigurableUserGroupProvider provider = new CompositeConfigurableUserGroupProvider();
            provider.initialize(initializationContext);
            provider.onConfigured(configurationContext);
        });
    }

    @Test
    public void testConfigurableUserGroupProviderOnly() {
        final UserGroupProvider userGroupProvider = initCompositeUserGroupProvider(new CompositeConfigurableUserGroupProvider(), lookup -> {
            when(lookup.getUserGroupProvider(eq(CONFIGURABLE_USER_GROUP_PROVIDER))).thenReturn(getConfigurableUserGroupProvider());
        }, configurationContext -> {
            when(configurationContext.getProperty(PROP_CONFIGURABLE_USER_GROUP_PROVIDER)).thenReturn(new StandardPropertyValue(CONFIGURABLE_USER_GROUP_PROVIDER, null, ParameterLookup.EMPTY));
        });

        // users and groups
        assertEquals(2, userGroupProvider.getUsers().size());
        assertEquals(1, userGroupProvider.getGroups().size());

        // unknown
        assertNull(userGroupProvider.getUser(NOT_A_REAL_USER_IDENTIFIER));
        assertNull(userGroupProvider.getUserByIdentity(NOT_A_REAL_USER_IDENTITY));

        final UserAndGroups unknownUserAndGroups = userGroupProvider.getUserAndGroups(NOT_A_REAL_USER_IDENTITY);
        assertNotNull(unknownUserAndGroups);
        assertNull(unknownUserAndGroups.getUser());
        assertNull(unknownUserAndGroups.getGroups());

        // providers
        testConfigurableUserGroupProvider(userGroupProvider);
    }

    @Test
    public void testConfigurableUserGroupProviderWithConflictingUserGroupProvider() {
        final UserGroupProvider userGroupProvider = initCompositeUserGroupProvider(new CompositeConfigurableUserGroupProvider(), lookup -> {
            when(lookup.getUserGroupProvider(eq(CONFIGURABLE_USER_GROUP_PROVIDER))).thenReturn(getConfigurableUserGroupProvider());
        }, configurationContext -> {
            when(configurationContext.getProperty(PROP_CONFIGURABLE_USER_GROUP_PROVIDER)).thenReturn(new StandardPropertyValue(CONFIGURABLE_USER_GROUP_PROVIDER, null, ParameterLookup.EMPTY));
        }, getConflictingUserGroupProvider());

        // users and groups
        assertEquals(3, userGroupProvider.getUsers().size());
        assertEquals(2, userGroupProvider.getGroups().size());

        // unknown
        assertNull(userGroupProvider.getUser(NOT_A_REAL_USER_IDENTIFIER));
        assertNull(userGroupProvider.getUserByIdentity(NOT_A_REAL_USER_IDENTITY));

        final UserAndGroups unknownUserAndGroups = userGroupProvider.getUserAndGroups(NOT_A_REAL_USER_IDENTITY);
        assertNotNull(unknownUserAndGroups);
        assertNull(unknownUserAndGroups.getUser());
        assertNull(unknownUserAndGroups.getGroups());

        // providers
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            testConfigurableUserGroupProvider(userGroupProvider);
        });
        assertTrue(exception.getMessage().contains(USER_1_IDENTITY));

        exception = assertThrows(IllegalStateException.class, () -> {
            testConflictingUserGroupProvider(userGroupProvider);
        });
        assertTrue(exception.getMessage().contains(USER_1_IDENTITY));
    }

    @Test
    public void testConfigurableUserGroupProviderWithCollaboratingUserGroupProvider() {
        final UserGroupProvider userGroupProvider = initCompositeUserGroupProvider(new CompositeConfigurableUserGroupProvider(), lookup -> {
            when(lookup.getUserGroupProvider(eq(CONFIGURABLE_USER_GROUP_PROVIDER))).thenReturn(getConfigurableUserGroupProvider());
        }, configurationContext -> {
            when(configurationContext.getProperty(PROP_CONFIGURABLE_USER_GROUP_PROVIDER)).thenReturn(new StandardPropertyValue(CONFIGURABLE_USER_GROUP_PROVIDER, null, ParameterLookup.EMPTY));
        }, getCollaboratingUserGroupProvider());

        // users and groups
        assertEquals(3, userGroupProvider.getUsers().size());
        assertEquals(2, userGroupProvider.getGroups().size());

        // unknown
        assertNull(userGroupProvider.getUser(NOT_A_REAL_USER_IDENTIFIER));
        assertNull(userGroupProvider.getUserByIdentity(NOT_A_REAL_USER_IDENTITY));

        final UserAndGroups unknownUserAndGroups = userGroupProvider.getUserAndGroups(NOT_A_REAL_USER_IDENTITY);
        assertNotNull(unknownUserAndGroups);
        assertNull(unknownUserAndGroups.getUser());
        assertNull(unknownUserAndGroups.getGroups());

        // providers
        final UserAndGroups user1AndGroups = userGroupProvider.getUserAndGroups(USER_1_IDENTITY);
        assertNotNull(user1AndGroups);
        assertNotNull(user1AndGroups.getUser());
        assertEquals(2, user1AndGroups.getGroups().size()); // from CollaboratingUGP
    }

    private UserGroupProvider getConfigurableUserGroupProvider() {
        final Set<User> users = Stream.of(
                new User.Builder().identifier(USER_1_IDENTIFIER).identity(USER_1_IDENTITY).build(),
                new User.Builder().identifier(USER_5_IDENTIFIER).identity(USER_5_IDENTITY).build()
        ).collect(Collectors.toSet());

        final Set<Group> groups = Stream.of(
                new Group.Builder().identifier(GROUP_1_IDENTIFIER).name(GROUP_1_NAME).addUser(USER_1_IDENTIFIER).build()
        ).collect(Collectors.toSet());

        return new SimpleConfigurableUserGroupProvider(users, groups);
    }

    private void testConfigurableUserGroupProvider(final UserGroupProvider userGroupProvider) {
        assertNotNull(userGroupProvider.getUser(USER_1_IDENTIFIER));
        assertNotNull(userGroupProvider.getUserByIdentity(USER_1_IDENTITY));

        final UserAndGroups user1AndGroups = userGroupProvider.getUserAndGroups(USER_1_IDENTITY);
        assertNotNull(user1AndGroups);
        assertNotNull(user1AndGroups.getUser());
        assertEquals(1, user1AndGroups.getGroups().size());

        assertNotNull(userGroupProvider.getUser(USER_5_IDENTIFIER));
        assertNotNull(userGroupProvider.getUserByIdentity(USER_5_IDENTITY));

        final UserAndGroups user5AndGroups = userGroupProvider.getUserAndGroups(USER_5_IDENTITY);
        assertNotNull(user5AndGroups);
        assertNotNull(user5AndGroups.getUser());
        assertTrue(user5AndGroups.getGroups().isEmpty());

        assertNotNull(userGroupProvider.getGroup(GROUP_1_IDENTIFIER));
        assertEquals(1, userGroupProvider.getGroup(GROUP_1_IDENTIFIER).getUsers().size());
    }
}