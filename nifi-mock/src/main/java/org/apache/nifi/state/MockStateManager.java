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

package org.apache.nifi.state;

import org.apache.nifi.annotation.behavior.Stateful;
import org.apache.nifi.components.state.Scope;
import org.apache.nifi.components.state.StateManager;
import org.apache.nifi.components.state.StateMap;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.apache.nifi.state.MockStateManager.ExecutionMode.CLUSTERED;
import static org.apache.nifi.state.MockStateManager.ExecutionMode.STANDALONE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MockStateManager implements StateManager {

    public enum ExecutionMode {
        // in CLUSTERED mode separate state maps are used for the CLUSTER and the LOCAL scopes
        CLUSTERED,
        // in STANDALONE mode the same state map (the local one) is used for both the CLUSTER and the LOCAL scopes
        STANDALONE
    }

    private final AtomicInteger versionIndex = new AtomicInteger(0);

    private StateMap localStateMap = new MockStateMap(null, -1L);
    private StateMap clusterStateMap = new MockStateMap(null, -1L);

    private volatile boolean failToGetLocalState = false;
    private volatile boolean failToSetLocalState = false;
    private volatile boolean failToGetClusterState = false;
    private volatile boolean failToSetClusterState = false;
    private volatile boolean ignoreAnnotations = false;

    private final AtomicLong localRetrievedCount = new AtomicLong(0L);
    private final AtomicLong clusterRetrievedCount = new AtomicLong(0L);

    private final boolean usesLocalState;
    private final boolean usesClusterState;

    private ExecutionMode executionMode = CLUSTERED;

    public MockStateManager(final Object component) {
        final Stateful stateful = component.getClass().getAnnotation(Stateful.class);
        if (stateful == null) {
            usesLocalState = false;
            usesClusterState = false;
        } else {
            final Scope[] scopes = stateful.scopes();
            boolean local = false;
            boolean cluster = false;

            for (final Scope scope : scopes) {
                if (scope == Scope.LOCAL) {
                    local = true;
                } else if (scope == Scope.CLUSTER) {
                    cluster = true;
                }
            }

            usesLocalState = local;
            usesClusterState = cluster;
        }
    }

    public void reset() {
        clusterStateMap = new MockStateMap(null, -1L);
        localStateMap = new MockStateMap(null, -1L);
    }

    public void setExecutionMode(ExecutionMode executionMode) {
        this.executionMode = executionMode;
    }

    @Override
    public synchronized void setState(final Map<String, String> state, final Scope scope) throws IOException {
        verifyAnnotation(scope);
        verifyCanSet(scope);
        final StateMap stateMap = new MockStateMap(state, versionIndex.incrementAndGet());

        if (scope == Scope.CLUSTER && executionMode == CLUSTERED) {
            clusterStateMap = stateMap;
        } else {
            localStateMap = stateMap;
        }
    }

    @Override
    public synchronized StateMap getState(final Scope scope) throws IOException {
        verifyAnnotation(scope);
        verifyCanGet(scope);
        return retrieveState(scope);
    }

    private synchronized StateMap retrieveState(final Scope scope) {
        verifyAnnotation(scope);
        if (scope == Scope.CLUSTER) {
            clusterRetrievedCount.incrementAndGet();
            return executionMode == CLUSTERED ? clusterStateMap : localStateMap;
        } else {
            localRetrievedCount.incrementAndGet();
            return localStateMap;
        }
    }

    public long getRetrievalCount(final Scope scope) {
        return switch (scope) {
            case CLUSTER -> clusterRetrievedCount.get();
            case LOCAL -> localRetrievedCount.get();
        };
    }

    @Override
    public synchronized boolean replace(final StateMap oldValue, final Map<String, String> newValue, final Scope scope) throws IOException {
        verifyAnnotation(scope);
        if (scope == Scope.CLUSTER) {
            if (executionMode == CLUSTERED && oldValue == clusterStateMap) {
                verifyCanSet(scope);
                clusterStateMap = new MockStateMap(newValue, versionIndex.incrementAndGet());
                return true;
            } else if (executionMode == STANDALONE && oldValue == localStateMap) {
                verifyCanSet(scope);
                localStateMap = new MockStateMap(newValue, versionIndex.incrementAndGet());
                return true;
            }

            return false;
        } else {
            if (oldValue == localStateMap) {
                verifyCanSet(scope);
                localStateMap = new MockStateMap(newValue, versionIndex.incrementAndGet());
                return true;
            }

            return false;
        }
    }

    @Override
    public synchronized void clear(final Scope scope) throws IOException {
        verifyAnnotation(scope);
        setState(Collections.emptyMap(), scope);
    }

    private void verifyCanSet(final Scope scope) throws IOException {
        final boolean failToSet = (scope == Scope.LOCAL) ? failToSetLocalState : failToSetClusterState;
        if (failToSet) {
            throw new IOException("Unit Test configured to throw IOException if " + scope + " State is set");
        }
    }

    private void verifyCanGet(final Scope scope) throws IOException {
        final boolean failToGet = (scope == Scope.LOCAL) ? failToGetLocalState : failToGetClusterState;
        if (failToGet) {
            throw new IOException("Unit Test configured to throw IOException if " + scope + " State is retrieved");
        }
    }

    public void setIgnoreAnnotations(final boolean ignore) {
        this.ignoreAnnotations = ignore;
    }

    private void verifyAnnotation(final Scope scope) {
        if (ignoreAnnotations) {
            return;
        }

        // ensure that the @Stateful annotation is present with the appropriate Scope
        if ((scope == Scope.LOCAL && !usesLocalState) || (scope == Scope.CLUSTER && !usesClusterState)) {
            Assertions.fail("Component is attempting to set or retrieve state with a scope of " + scope + " but does not declare that it will use "
                + scope + " state. A @Stateful annotation should be added to the component with a scope of " + scope);
        }
    }

    private String getValue(final String key, final Scope scope) {
        final StateMap stateMap;
        if (scope == Scope.CLUSTER && executionMode == CLUSTERED) {
            stateMap = clusterStateMap;
        } else {
            stateMap = localStateMap;
        }

        return stateMap.get(key);
    }

    //
    // assertion methods to make unit testing easier
    //
    /**
     * Ensures that the state with the given key and scope is set to the given value, or else the test will fail
     *
     * @param key the state key
     * @param value the expected value
     * @param scope the scope
     */
    public void assertStateEquals(final String key, final String value, final Scope scope) {
        Assertions.assertEquals(value, getValue(key, scope));
    }

    /**
     * Ensures that the state is equal to the given values
     *
     * @param stateValues the values expected
     * @param scope the scope to compare the stateValues against
     */
    public void assertStateEquals(final Map<String, String> stateValues, final Scope scope) {
        final StateMap stateMap = retrieveState(scope);
        Assertions.assertEquals(stateValues, stateMap.toMap());
    }

    /**
     * Ensures that the state is not equal to the given values
     *
     * @param stateValues the unexpected values
     * @param scope the scope to compare the stateValues against
     */
    public void assertStateNotEquals(final Map<String, String> stateValues, final Scope scope) {
        final StateMap stateMap = retrieveState(scope);
        Assertions.assertNotSame(stateValues, stateMap.toMap());
    }

    /**
     * Ensures that the state with the given key and scope is not set to the given value, or else the test will fail
     *
     * @param key the state key
     * @param value the unexpected value
     * @param scope the scope
     */
    public void assertStateNotEquals(final String key, final String value, final Scope scope) {
        Assertions.assertNotEquals(value, getValue(key, scope));
    }

    /**
     * Ensures that some value is set for the given key and scope, or else the test will fail
     *
     * @param key the state key
     * @param scope the scope
     */
    public void assertStateSet(final String key, final Scope scope) {
        Assertions.assertNotNull(getValue(key, scope), "Expected state to be set for key " + key + " and scope " + scope + ", but it was not set");
    }

    /**
     * Ensures that no value is set for the given key and scope, or else the test will fail
     *
     * @param key the state key
     * @param scope the scope
     */
    public void assertStateNotSet(final String key, final Scope scope) {
        Assertions.assertNull(getValue(key, scope), "Expected state not to be set for key " + key + " and scope " + scope + ", but it was set");
    }

    /**
     * Ensures that the state was set for the given scope, regardless of what the value was.
     *
     * @param scope the scope
     */
    public void assertStateSet(final Scope scope) {
        final StateMap stateMap = (scope == Scope.CLUSTER) ? clusterStateMap : localStateMap;
        assertTrue(stateMap.getStateVersion().isPresent(), "Expected state to be set for Scope " + scope + ", but it was not set");
    }

    /**
     * Ensures that state was not set for any scope
     */
    public void assertStateNotSet() {
        assertStateNotSet(Scope.CLUSTER);
        assertStateNotSet(Scope.LOCAL);
    }

    /**
     * Ensures that the state was not set for the given scope
     *
     * @param scope the scope
     */
    public void assertStateNotSet(final Scope scope) {
        final StateMap stateMap = (scope == Scope.CLUSTER) ? clusterStateMap : localStateMap;
        assertFalse(stateMap.getStateVersion().isPresent(), "Expected state not to be set for Scope " + scope + ", but it was set");
    }

    /**
     * Specifies whether or not the State Manager should throw an IOException when state is set for the given scope.
     * Note that calls to {@link #replace(StateMap, Map, Scope)} will fail only if the state would be set (i.e., if
     * we call replace and the StateMap does not match the old value, it will not fail).
     *
     * Also note that if setting state is set to fail, clearing will also fail, as clearing is thought of as setting the
     * state to empty
     *
     * @param scope the scope that should (or should not) fail
     * @param fail whether or not setting state should fail
     */
    public void setFailOnStateSet(final Scope scope, final boolean fail) {
        if (scope == Scope.LOCAL) {
            failToSetLocalState = fail;
        } else {
            failToSetClusterState = fail;
        }
    }

    /**
     * Specifies whether or not the State Manager should throw an IOException when state is retrieved for the given scope.
     *
     * @param scope the scope that should (or should not) fail
     * @param fail whether or not retrieving state should fail
     */
    public void setFailOnStateGet(final Scope scope, final boolean fail) {
        if (scope == Scope.LOCAL) {
            failToGetLocalState = fail;
        } else {
            failToGetClusterState = fail;
        }
    }
}
