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
package org.apache.nifi.controller.service;

import org.apache.nifi.bundle.Bundle;
import org.apache.nifi.bundle.BundleCoordinate;
import org.apache.nifi.components.state.StateManagerProvider;
import org.apache.nifi.components.validation.ValidationTrigger;
import org.apache.nifi.controller.ControllerService;
import org.apache.nifi.controller.ExtensionBuilder;
import org.apache.nifi.controller.NodeTypeProvider;
import org.apache.nifi.controller.ProcessScheduler;
import org.apache.nifi.controller.ReloadComponent;
import org.apache.nifi.controller.flow.FlowManager;
import org.apache.nifi.nar.ExtensionDiscoveringManager;
import org.apache.nifi.nar.ExtensionManager;
import org.apache.nifi.nar.StandardExtensionDiscoveringManager;
import org.apache.nifi.nar.SystemBundle;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.NiFiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class StandardControllerServiceProviderTest {

    private ControllerService proxied;
    private ControllerService implementation;
    private static ExtensionDiscoveringManager extensionManager;
    private static Bundle systemBundle;

    @BeforeAll
    public static void setupSuite() {
        final NiFiProperties nifiProperties = NiFiProperties.createBasicNiFiProperties(StandardControllerServiceProviderTest.class.getResource("/conf/nifi.properties").getFile());

        // load the system bundle
        systemBundle = SystemBundle.create(nifiProperties);
        extensionManager = new StandardExtensionDiscoveringManager();
        extensionManager.discoverExtensions(systemBundle, Collections.emptySet());
    }

    @BeforeEach
    public void setup() throws Exception {
        String id = "id";
        String clazz = "org.apache.nifi.controller.service.util.TestControllerService";
        ControllerServiceProvider provider = new StandardControllerServiceProvider(null, null, Mockito.mock(FlowManager.class), Mockito.mock(ExtensionManager.class));
        ControllerServiceNode node = createControllerService(clazz, id, systemBundle.getBundleDetails().getCoordinate(), provider);
        proxied = node.getProxiedControllerService();
        implementation = node.getControllerServiceImplementation();
    }

    private ControllerServiceNode createControllerService(final String type, final String id, final BundleCoordinate bundleCoordinate, final ControllerServiceProvider serviceProvider) {
        final ControllerServiceNode serviceNode = new ExtensionBuilder()
            .identifier(id)
            .type(type)
            .bundleCoordinate(bundleCoordinate)
            .controllerServiceProvider(serviceProvider)
            .processScheduler(Mockito.mock(ProcessScheduler.class))
            .nodeTypeProvider(Mockito.mock(NodeTypeProvider.class))
            .validationTrigger(Mockito.mock(ValidationTrigger.class))
            .reloadComponent(Mockito.mock(ReloadComponent.class))
            .stateManagerProvider(Mockito.mock(StateManagerProvider.class))
            .extensionManager(extensionManager)
            .buildControllerService();

        return serviceNode;
    }

    @Test
    public void testCallProxiedOnPropertyModified() {
        assertThrows(UnsupportedOperationException.class,
                () -> proxied.onPropertyModified(null, "oldValue", "newValue"));
    }

    @Test
    public void testCallImplementationOnPropertyModified() {
        implementation.onPropertyModified(null, "oldValue", "newValue");
    }

    @Test
    public void testCallProxiedInitialized() throws InitializationException {
        assertThrows(UnsupportedOperationException.class,
                () -> proxied.initialize(null));
    }

    @Test
    public void testCallImplementationInitialized() throws InitializationException {
        implementation.initialize(null);
    }
}
