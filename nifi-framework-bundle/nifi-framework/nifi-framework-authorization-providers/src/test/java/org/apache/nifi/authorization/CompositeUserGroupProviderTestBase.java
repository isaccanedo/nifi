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
import org.apache.nifi.components.PropertyValue;
import org.apache.nifi.parameter.ParameterLookup;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.nifi.authorization.CompositeUserGroupProvider.PROP_USER_GROUP_PROVIDER_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CompositeUserGroupProviderTestBase {

    public static final String USER_1_IDENTIFIER = "user-identifier-1";
    public static final String USER_1_IDENTITY = "user-identity-1";

    public static final String USER_2_IDENTIFIER = "user-identifier-2";
    public static final String USER_2_IDENTITY = "user-identity-2";

    public static final String USER_3_IDENTIFIER = "user-identifier-3";
    public static final String USER_3_IDENTITY = "user-identity-3";

    public static final String USER_4_IDENTIFIER = "user-identifier-4";
    public static final String USER_4_IDENTITY = "user-identity-4";

    public static final String GROUP_1_IDENTIFIER = "group-identifier-1";
    public static final String GROUP_1_NAME = "group-name-1";

    public static final String GROUP_2_IDENTIFIER = "group-identifier-2";
    public static final String GROUP_2_NAME = "group-name-2";

    public static final String NOT_A_REAL_USER_IDENTIFIER = "not-a-real-user-identifier";
    public static final String NOT_A_REAL_USER_IDENTITY = "not-a-real-user-identity";

    protected UserGroupProvider getUserGroupProviderOne() {
        final Set<User> users = Stream.of(
                new User.Builder().identifier(USER_1_IDENTIFIER).identity(USER_1_IDENTITY).build(),
                new User.Builder().identifier(USER_2_IDENTIFIER).identity(USER_2_IDENTITY).build()
        ).collect(Collectors.toSet());

        final Set<Group> groups = Stream.of(
                new Group.Builder().identifier(GROUP_1_IDENTIFIER).name(GROUP_1_NAME).addUser(USER_1_IDENTIFIER).build()
        ).collect(Collectors.toSet());

        return new SimpleUserGroupProvider(users, groups);
    }

    protected void testUserGroupProviderOne(final UserGroupProvider userGroupProvider) {
        // users
        assertNotNull(userGroupProvider.getUser(USER_1_IDENTIFIER));
        assertNotNull(userGroupProvider.getUserByIdentity(USER_1_IDENTITY));

        assertNotNull(userGroupProvider.getUser(USER_2_IDENTIFIER));
        assertNotNull(userGroupProvider.getUserByIdentity(USER_2_IDENTITY));

        // When used with ConflictingUserGroupProvider, we expect the line below to throw IllegalStateException
        final UserAndGroups user1AndGroups = userGroupProvider.getUserAndGroups(USER_1_IDENTITY);
        assertNotNull(user1AndGroups);
        assertNotNull(user1AndGroups.getUser());
        assertEquals(1, user1AndGroups.getGroups().size());

        final UserAndGroups user2AndGroups = userGroupProvider.getUserAndGroups(USER_2_IDENTITY);
        assertNotNull(user2AndGroups);
        assertNotNull(user2AndGroups.getUser());
        assertTrue(user2AndGroups.getGroups().isEmpty());

        // groups
        assertNotNull(userGroupProvider.getGroup(GROUP_1_IDENTIFIER));
        assertEquals(1, userGroupProvider.getGroup(GROUP_1_IDENTIFIER).getUsers().size());
    }

    protected UserGroupProvider getUserGroupProviderTwo() {
        final Set<User> users = Stream.of(
                new User.Builder().identifier(USER_3_IDENTIFIER).identity(USER_3_IDENTITY).build()
        ).collect(Collectors.toSet());

        final Set<Group> groups = Stream.of(
                new Group.Builder().identifier(GROUP_2_IDENTIFIER).name(GROUP_2_NAME).addUser(USER_3_IDENTIFIER).build()
        ).collect(Collectors.toSet());

        return new SimpleUserGroupProvider(users, groups);
    }

    protected void testUserGroupProviderTwo(final UserGroupProvider userGroupProvider) {
        // users
        assertNotNull(userGroupProvider.getUser(USER_3_IDENTIFIER));
        assertNotNull(userGroupProvider.getUserByIdentity(USER_3_IDENTITY));

        final UserAndGroups user3AndGroups = userGroupProvider.getUserAndGroups(USER_3_IDENTITY);
        assertNotNull(user3AndGroups);
        assertNotNull(user3AndGroups.getUser());
        assertEquals(1, user3AndGroups.getGroups().size());

        // groups
        assertNotNull(userGroupProvider.getGroup(GROUP_2_IDENTIFIER));
        assertEquals(1, userGroupProvider.getGroup(GROUP_2_IDENTIFIER).getUsers().size());
    }

    protected UserGroupProvider getConflictingUserGroupProvider() {
        final Set<User> users = Stream.of(
                new User.Builder().identifier(USER_1_IDENTIFIER).identity(USER_1_IDENTITY).build(),
                new User.Builder().identifier(USER_4_IDENTIFIER).identity(USER_4_IDENTITY).build()
        ).collect(Collectors.toSet());

        final Set<Group> groups = Stream.of(
                new Group.Builder().identifier(GROUP_2_IDENTIFIER).name(GROUP_2_NAME).addUser(USER_1_IDENTIFIER).addUser(USER_4_IDENTIFIER).build()
        ).collect(Collectors.toSet());

        return new SimpleUserGroupProvider(users, groups);
    }

    protected void testConflictingUserGroupProvider(final UserGroupProvider userGroupProvider) {
        assertNotNull(userGroupProvider.getUser(USER_4_IDENTIFIER));
        assertNotNull(userGroupProvider.getUserByIdentity(USER_4_IDENTITY));

        assertNotNull(userGroupProvider.getUser(USER_1_IDENTIFIER));
        assertNotNull(userGroupProvider.getUserByIdentity(USER_1_IDENTITY));

        // When used with UserGroupProviderOne, we expect the line below to throw IllegalStateException
        final UserAndGroups user1AndGroups = userGroupProvider.getUserAndGroups(USER_1_IDENTITY);
        assertNotNull(user1AndGroups);
        assertNotNull(user1AndGroups.getUser());
        assertEquals(1, user1AndGroups.getGroups().size());
    }

    protected UserGroupProvider getCollaboratingUserGroupProvider() {
        final Set<User> users = Stream.of(
                new User.Builder().identifier(USER_4_IDENTIFIER).identity(USER_4_IDENTITY).build()
        ).collect(Collectors.toSet());

        final Set<Group> groups = Stream.of(
                // USER_1_IDENTIFIER is from UserGroupProviderOne
                new Group.Builder().identifier(GROUP_2_IDENTIFIER).name(GROUP_2_NAME).addUser(USER_1_IDENTIFIER).addUser(USER_4_IDENTIFIER).build()
        ).collect(Collectors.toSet());

        return new SimpleUserGroupProvider(users, groups);
    }

    protected void testCollaboratingUserGroupProvider(final UserGroupProvider userGroupProvider) {
        // users
        assertNotNull(userGroupProvider.getUser(USER_4_IDENTIFIER));
        assertNotNull(userGroupProvider.getUserByIdentity(USER_4_IDENTITY));

        final UserAndGroups user4AndGroups = userGroupProvider.getUserAndGroups(USER_4_IDENTITY);
        assertNotNull(user4AndGroups);
        assertNotNull(user4AndGroups.getUser());
        assertEquals(1, user4AndGroups.getGroups().size());

        // groups
        assertNotNull(userGroupProvider.getGroup(GROUP_2_IDENTIFIER));
        assertEquals(2, userGroupProvider.getGroup(GROUP_2_IDENTIFIER).getUsers().size());
    }

    protected void mockProperties(final AuthorizerConfigurationContext configurationContext) {
        when(configurationContext.getProperties()).then((invocation) -> {
            final Map<String, String> properties = new LinkedHashMap<>();

            int i = 1;
            while (true) {
                final String key = PROP_USER_GROUP_PROVIDER_PREFIX + i++;
                final PropertyValue value = configurationContext.getProperty(key);
                if (value == null) {
                    break;
                } else {
                    properties.put(key, value.getValue());
                }
            }

            return properties;
        });
    }

    protected UserGroupProvider initCompositeUserGroupProvider(
            final CompositeUserGroupProvider compositeUserGroupProvider, final Consumer<UserGroupProviderLookup> lookupConsumer,
            final Consumer<AuthorizerConfigurationContext> configurationContextConsumer, final UserGroupProvider... providers) {

        // initialization
        final UserGroupProviderLookup lookup = mock(UserGroupProviderLookup.class);

        for (int i = 1; i <= providers.length; i++) {
            when(lookup.getUserGroupProvider(eq(String.valueOf(i)))).thenReturn(providers[i - 1]);
        }

        // allow callers to mock additional providers
        if (lookupConsumer != null) {
            lookupConsumer.accept(lookup);
        }

        final UserGroupProviderInitializationContext initializationContext = mock(UserGroupProviderInitializationContext.class);
        when(initializationContext.getUserGroupProviderLookup()).thenReturn(lookup);

        compositeUserGroupProvider.initialize(initializationContext);

        // configuration
        final AuthorizerConfigurationContext configurationContext = mock(AuthorizerConfigurationContext.class);

        for (int i = 1; i <= providers.length; i++) {
            when(configurationContext.getProperty(eq(PROP_USER_GROUP_PROVIDER_PREFIX + i))).thenReturn(new StandardPropertyValue(String.valueOf(i), null, ParameterLookup.EMPTY));
        }

        // allow callers to mock additional properties
        if (configurationContextConsumer != null) {
            configurationContextConsumer.accept(configurationContext);
        }

        mockProperties(configurationContext);

        compositeUserGroupProvider.onConfigured(configurationContext);

        return compositeUserGroupProvider;
    }
}
