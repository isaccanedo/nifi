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

import org.apache.nifi.authorization.resource.Authorizable;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.parameter.ParameterContext;

import java.util.List;
import java.util.Set;

/**
 * Authorizable for a component that references a ControllerService.
 */
public interface ComponentAuthorizable extends AuthorizableHolder {
    /**
     * Returns whether or not the underlying configurable component is restricted.
     *
     * @return whether or not the underlying configurable component is restricted
     */
    boolean isRestricted();

    /**
     * Returns all component restriction authorizables for this component.
     *
     * @return all component restriction authorizables
     */
    Set<Authorizable> getRestrictedAuthorizables();

    /**
     * @return the Parameter Context that is bound to this component's Process Group, as an Authorizable, or <code>null</code> if no Parameter Context is bound
     */
    ParameterContext getParameterContext();

    /**
     * Returns the property descriptor for the specified property.
     *
     * @param propertyName property name
     * @return property descriptor
     */
    PropertyDescriptor getPropertyDescriptor(String propertyName);

    /**
     * Returns the property descriptors for this configurable component.
     *
     * @return property descriptors
     */
    List<PropertyDescriptor> getPropertyDescriptors();

    /**
     * Returns the current value of the specified property.
     *
     * @param propertyDescriptor property descriptor
     * @return value
     */
    String getValue(PropertyDescriptor propertyDescriptor);

    /**
     * Returns the currently configured value of the specified property without performing Parameter substitution
     *
     * @param propertyDescriptor property descriptor
     * @return value
     */
    String getRawValue(PropertyDescriptor propertyDescriptor);

    /**
     * Cleans up any resources resulting from the creation of these temporary components.
     */
    void cleanUpResources();
}
