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

package org.apache.nifi.controller.state.providers;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import org.apache.nifi.components.state.StateMap;
import org.apache.nifi.components.state.StateProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * <p>
 * Abstract class that provides a suite of test for State Providers. Each State Provider implementation can simply extend this class,
 * implement the getProvider() method, and have the entire suite of tests run against the provider.
 * </p>
 *
 * <p>
 * It is recommended that implementations create a new provider in a method annotated with @Before and cleanup in a method annotated with @After.
 * </p>
 */
public abstract class AbstractTestStateProvider {
    protected final String componentId = "111111111-1111-1111-1111-111111111111";


    @Test
    public void testSetAndGet() throws IOException {
        final Collection<String> initialStoredComponentIds = getProvider().getStoredComponentIds();
        assertTrue(initialStoredComponentIds.isEmpty());

        getProvider().setState(Collections.singletonMap("testSetAndGet", "value"), componentId);
        assertEquals("value", getProvider().getState(componentId).get("testSetAndGet"));

        final Collection<String> storedComponentIds = getProvider().getStoredComponentIds();
        final Iterator<String> componentIds = storedComponentIds.iterator();
        assertTrue(componentIds.hasNext());
        assertEquals(componentId, componentIds.next());
    }

    @Test
    public void testReplaceSuccessful() throws IOException {
        final String key = "testReplaceSuccessful";
        final StateProvider provider = getProvider();

        StateMap map = provider.getState(componentId);
        assertNotNull(map);
        assertFalse(map.getStateVersion().isPresent());

        assertNotNull(map.toMap());
        assertTrue(map.toMap().isEmpty());
        provider.setState(Collections.singletonMap(key, "value1"), componentId);

        map = provider.getState(componentId);
        assertNotNull(map);
        assertTrue(map.getStateVersion().isPresent());
        assertEquals("value1", map.get(key));
        assertEquals("value1", map.toMap().get(key));

        final Map<String, String> newMap = new HashMap<>(map.toMap());
        newMap.put(key, "value2");
        assertTrue(provider.replace(map, newMap, componentId));

        map = provider.getState(componentId);
        assertEquals("value2", map.get(key));
        assertTrue(map.getStateVersion().isPresent());
    }

    @Test
    public void testReplaceWithWrongVersion() throws IOException {
        final String key = "testReplaceWithWrongVersion";
        final StateProvider provider = getProvider();
        provider.setState(Collections.singletonMap(key, "value1"), componentId);

        StateMap stateMap = provider.getState(componentId);
        assertNotNull(stateMap);
        assertEquals("value1", stateMap.get(key));
        assertTrue(stateMap.getStateVersion().isPresent());

        provider.setState(Collections.singletonMap(key, "intermediate value"), componentId);

        assertFalse(provider.replace(stateMap, Collections.singletonMap(key, "value2"), componentId));
        stateMap = provider.getState(componentId);
        assertEquals(key, stateMap.toMap().keySet().iterator().next());
        assertEquals(1, stateMap.toMap().size());
        assertEquals("intermediate value", stateMap.get(key));
        assertTrue(stateMap.getStateVersion().isPresent());
    }


    @Test
    public void testToMap() throws IOException {
        final String key = "testKeySet";
        final StateProvider provider = getProvider();
        Map<String, String> map = provider.getState(componentId).toMap();
        assertNotNull(map);
        assertTrue(map.isEmpty());

        provider.setState(Collections.singletonMap(key, "value"), componentId);
        map = provider.getState(componentId).toMap();
        assertNotNull(map);
        assertEquals(1, map.size());
        assertEquals("value", map.get(key));

        provider.setState(Collections.emptyMap(), componentId);

        final StateMap stateMap = provider.getState(componentId);
        map = stateMap.toMap();
        assertNotNull(map);
        assertTrue(map.isEmpty());
        assertTrue(stateMap.getStateVersion().isPresent());
    }

    @Test
    public void testClear() throws IOException {
        final StateProvider provider = getProvider();
        StateMap stateMap = provider.getState(componentId);
        assertNotNull(stateMap);
        assertFalse(stateMap.getStateVersion().isPresent());
        assertTrue(stateMap.toMap().isEmpty());

        provider.setState(Collections.singletonMap("testClear", "value"), componentId);

        stateMap = provider.getState(componentId);
        assertNotNull(stateMap);
        assertTrue(stateMap.getStateVersion().isPresent());
        assertEquals("value", stateMap.get("testClear"));

        provider.clear(componentId);

        stateMap = provider.getState(componentId);
        assertNotNull(stateMap);
        assertTrue(stateMap.getStateVersion().isPresent());
        assertTrue(stateMap.toMap().isEmpty());
    }

    @Test
    public void testReplaceWithNonExistingValue() throws Exception {
        final String key = "testReplaceWithNonExistingValue";
        final String value = "value";
        final StateProvider provider = getProvider();
        StateMap stateMap = provider.getState(componentId);
        assertNotNull(stateMap);

        final Map<String, String> newValue = new HashMap<>();
        newValue.put(key, value);

        final boolean replaced = provider.replace(stateMap, newValue, componentId);
        assertTrue(replaced);

        StateMap map = provider.getState(componentId);
        assertEquals(value, map.get(key));
        assertTrue(map.getStateVersion().isPresent());
    }

    @Test
    public void testReplaceWithNonExistingValueAndVersionGreaterThanNegativeOne() throws Exception {
        final StateProvider provider = getProvider();
        final StateMap stateMap = new StateMap() {
            @Override
            public Optional<String> getStateVersion() {
                return Optional.of("4");
            }

            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public Map<String, String> toMap() {
                return Collections.emptyMap();
            }
        };

        final Map<String, String> newValue = new HashMap<>();
        newValue.put("value", "value");

        final boolean replaced = provider.replace(stateMap, newValue, componentId);
        assertFalse(replaced);
    }

    @Test
    void testReplaceConcurrentCreate() throws IOException {
        final StateProvider provider = getProvider();

        final StateMap stateMap1 = provider.getState(componentId);
        final StateMap stateMap2 = provider.getState(componentId);

        final boolean replaced1 = provider.replace(stateMap1, Collections.emptyMap(), componentId);

        assertTrue(replaced1);

        final StateMap replacedStateMap = provider.getState(componentId);
        final Optional<String> replacedVersion = replacedStateMap.getStateVersion();
        assertTrue(replacedVersion.isPresent());
        assertEquals("0", replacedVersion.get());

        final boolean replaced2 = provider.replace(stateMap2, Collections.emptyMap(), componentId);

        assertFalse(replaced2);
    }

    @Test
    void testReplaceConcurrentUpdate() throws IOException {
        final StateProvider provider = getProvider();

        provider.setState(Collections.singletonMap("key", "0"), componentId);

        final StateMap stateMap1 = provider.getState(componentId);
        final StateMap stateMap2 = provider.getState(componentId);

        final boolean replaced1 = provider.replace(stateMap1, Collections.singletonMap("key", "1"), componentId);

        assertTrue(replaced1);

        final StateMap replacedStateMap = provider.getState(componentId);
        final Optional<String> replacedVersion = replacedStateMap.getStateVersion();
        assertTrue(replacedVersion.isPresent());
        assertEquals("1", replacedVersion.get());

        final boolean replaced2 = provider.replace(stateMap2, Collections.singletonMap("key", "2"), componentId);

        assertFalse(replaced2);
    }

    @Test
    public void testOnComponentRemoved() throws IOException, InterruptedException {
        final StateProvider provider = getProvider();
        final Map<String, String> newValue = new HashMap<>();
        newValue.put("value", "value");

        provider.setState(newValue, componentId);
        final StateMap stateMap = provider.getState(componentId);
        assertTrue(stateMap.getStateVersion().isPresent());

        provider.onComponentRemoved(componentId);

        // wait for the background process to complete
        Thread.sleep(1000L);

        final StateMap stateMapAfterRemoval = provider.getState(componentId);

        // version should not be present because the state has been removed entirely.
        assertFalse(stateMapAfterRemoval.getStateVersion().isPresent());
    }

    protected abstract StateProvider getProvider();
}
