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

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.controller.ControllerService;
import org.apache.nifi.controller.ControllerServiceInitializationContext;
import org.apache.nifi.controller.service.ControllerServiceNode;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class ControllerServiceNodeMatcherTest extends AbstractAttributeMatcherTest {

    @Mock
    private ControllerServiceNode component;

    @Test
    public void testMatching() {
        // given
        final ControllerServiceNodeMatcher testSubject = new ControllerServiceNodeMatcher();
        givenDefaultSearchTerm();
        Mockito.when(component.getIdentifier()).thenReturn("LoremId");
        Mockito.when(component.getVersionedComponentId()).thenReturn(Optional.of("LoremVersionId"));
        Mockito.when(component.getName()).thenReturn("LoremName");
        Mockito.when(component.getComments()).thenReturn("LoremComment");
        Mockito.when(component.getControllerServiceImplementation()).thenReturn(new LoremControllerService());
        Mockito.when(component.getComponentType()).thenReturn("LoremControllerService");
        // when
        testSubject.match(component, searchQuery, matches);

        // then
        thenMatchConsistsOf("Id: LoremId", //
                "Version Control ID: LoremVersionId", //
                "Name: LoremName", //
                "Comments: LoremComment",
                "Type: LoremControllerService");
    }

    private static class LoremControllerService implements ControllerService {

        @Override
        public Collection<ValidationResult> validate(ValidationContext context) {
            return null;
        }

        @Override
        public PropertyDescriptor getPropertyDescriptor(String name) {
            return null;
        }

        @Override
        public void onPropertyModified(PropertyDescriptor descriptor, String oldValue, String newValue) {
        }

        @Override
        public List<PropertyDescriptor> getPropertyDescriptors() {
            return null;
        }

        @Override
        public String getIdentifier() {
            return null;
        }

        @Override
        public void initialize(ControllerServiceInitializationContext context) {
        }
    }
}