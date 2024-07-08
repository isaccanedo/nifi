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
package org.apache.nifi.controller;

import org.apache.nifi.annotation.behavior.SupportsSensitiveDynamicProperties;
import org.apache.nifi.authorization.AccessDeniedException;
import org.apache.nifi.authorization.AuthorizationResult;
import org.apache.nifi.authorization.AuthorizationResult.Result;
import org.apache.nifi.authorization.Authorizer;
import org.apache.nifi.authorization.RequestAction;
import org.apache.nifi.authorization.resource.Authorizable;
import org.apache.nifi.authorization.resource.ComponentAuthorizable;
import org.apache.nifi.authorization.resource.RestrictedComponentsAuthorizableFactory;
import org.apache.nifi.authorization.user.NiFiUser;
import org.apache.nifi.bundle.BundleCoordinate;
import org.apache.nifi.components.ClassloaderIsolationKeyProvider;
import org.apache.nifi.components.ConfigurableComponent;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.components.validation.ValidationState;
import org.apache.nifi.components.validation.ValidationStatus;
import org.apache.nifi.context.PropertyContext;
import org.apache.nifi.groups.ProcessGroup;
import org.apache.nifi.parameter.ParameterContext;
import org.apache.nifi.parameter.ParameterLookup;
import org.apache.nifi.parameter.ParameterUpdate;

import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public interface ComponentNode extends ComponentAuthorizable {
    @Override
    String getIdentifier();

    String getName();

    void setName(String name);

    String getAnnotationData();

    void setAnnotationData(String data);

    default void setProperties(Map<String, String> properties) {
        setProperties(properties, false, Collections.emptySet());
    }

    void setProperties(Map<String, String> properties, boolean allowRemovalOfRequiresProperties, Set<String> sensitiveDynamicPropertyNames);

    void verifyCanUpdateProperties(final Map<String, String> properties);

    /**
     * @return the Set of names of all Parameters that are referenced by this component
     */
    Set<String> getReferencedParameterNames();

    /**
     * Determines whether the component is referencing any parameter
     * @return true if there is any parameter reference
     **/
    boolean isReferencingParameter();

    /**
     * Determines whether the component is referencing the given parameter
     * @param parameterName Parameter Name
     * @return true if parameter is referenced
     **/
    boolean isReferencingParameter(final String parameterName);

    /**
     * Notifies the Component that the value of a parameter has changed
     * @param parameterUpdates a Map of Parameter name to a ParameterUpdate that describes how the Parameter changed
     */
    void onParametersModified(Map<String, ParameterUpdate> parameterUpdates);

    /**
     * @return the Set of all attributes that are explicitly referenced by Expression Language used in the property values
     */
    Set<String> getReferencedAttributeNames();

    /**
     * <p>
     * Pause triggering asynchronous validation to occur when the component is updated. Often times, it is necessary
     * to update several aspects of a component, such as the properties and annotation data, at once. When this occurs,
     * we don't want to trigger validation for each update, so we can follow the pattern:
     * </p>
     *
     * <pre>
     * <code>
     * componentNode.pauseValidationTrigger();
     * try {
     *   componentNode.setProperties(properties);
     *   componentNode.setAnnotationData(annotationData);
     * } finally {
     *   componentNode.resumeValidationTrigger();
     * }
     * </code>
     * </pre>
     *
     * <p>
     * When calling this method, it is imperative that {@link #resumeValidationTrigger()} is always called within a {@code finally} block to
     * ensure that validation occurs.
     * </p>
     */
    void pauseValidationTrigger();

    /**
     * Resume triggering asynchronous validation to occur when the component is updated. This method is to be used in conjunction
     * with {@link #pauseValidationTrigger()} as illustrated in its documentation. When this method is called, if the component's Validation Status
     * is {@link ValidationStatus#VALIDATING}, component validation will immediately be triggered asynchronously.
     */
    void resumeValidationTrigger();

    Map<PropertyDescriptor, String> getRawPropertyValues();

    Map<PropertyDescriptor, String> getEffectivePropertyValues();

    PropertyConfiguration getProperty(PropertyDescriptor property);

    String getEffectivePropertyValue(PropertyDescriptor property);

    String getRawPropertyValue(PropertyDescriptor property);

    Map<PropertyDescriptor, PropertyConfiguration> getProperties();

    void reload(Set<URL> additionalUrls) throws Exception;

    void refreshProperties();

    Set<URL> getAdditionalClasspathResources(List<PropertyDescriptor> propertyDescriptors);

    BundleCoordinate getBundleCoordinate();

    ConfigurableComponent getComponent();

    TerminationAwareLogger getLogger();

    boolean isExtensionMissing();

    void setExtensionMissing(boolean extensionMissing);

    void verifyCanUpdateBundle(BundleCoordinate bundleCoordinate) throws IllegalStateException;

    boolean isReloadAdditionalResourcesNecessary();

    void reloadAdditionalResourcesIfNecessary();

    void resetValidationState();

    /**
     * @return the any validation errors for this connectable
     */
    Collection<ValidationResult> getValidationErrors();

    /**
     * @return the type of the component. I.e., the class name of the implementation
     */
    String getComponentType();

    /**
     * @return the class of the underlying
     */
    Class<?> getComponentClass();

    /**
     * @return the Canonical Class Name of the component
     */
    String getCanonicalClassName();

    /**
     * @return whether or not the underlying implementation has any restrictions
     */
    boolean isRestricted();

    /**
     * @return whether or not the underlying implementation is deprecated
     */
    boolean isDeprecated();

    /**
     * Indicates whether or not validation should be run on the component, based on its current state.
     *
     * @return <code>true</code> if the component needs validation, <code>false</code> otherwise
     */
    boolean isValidationNecessary();

    /**
     * Indicates whether the Component supports sensitive dynamic properties
     *
     * @return Support status for Sensitive Dynamic Properties
     */
    default boolean isSupportsSensitiveDynamicProperties() {
        return getComponent().getClass().isAnnotationPresent(SupportsSensitiveDynamicProperties.class);
    }

    /**
     * Returns the processor's current Validation Status
     *
     * @return the processor's current Validation Status
     */
    ValidationStatus getValidationStatus();

    /**
     * Returns the processor's Validation Status, waiting up to the given amount of time for the Validation to complete
     * if it is currently in the process of validating. If the processor is currently in the process of validation and
     * the validation logic does not complete in the given amount of time, or if the thread is interrupted, then a Validation Status
     * of {@link ValidationStatus#VALIDATING VALIDATING} will be returned.
     *
     * @param timeout the max amount of time to wait
     * @param unit the time unit
     * @return the ValidationStatus
     */
    ValidationStatus getValidationStatus(long timeout, TimeUnit unit);

    /**
     * Validates the component against the current configuration
     */
    ValidationStatus performValidation();

    /**
     * Validates the component against the given ValidationContext
     * @param validationContext the validation context to validate against
     * @return the ValidationState that is the result of validating against the given context
     */
    ValidationState performValidation(ValidationContext validationContext);

    /**
     * Performs validation against the given set of properties, annotation data, and parameters
     * @param properties the processor configuration
     * @param annotationData the annotation data
     * @param parameterContext the set of parameters
     * @return the validation state that results from validating against the given values
     */
    ValidationState performValidation(Map<PropertyDescriptor, PropertyConfiguration> properties, String annotationData, ParameterContext parameterContext);

    /**
     * Returns a {@link List} of all {@link PropertyDescriptor}s that this
     * component supports.
     *
     * @return PropertyDescriptor objects this component currently supports
     */
    List<PropertyDescriptor> getPropertyDescriptors();

    /**
     * @param name to lookup the descriptor
     * @return the PropertyDescriptor with the given name, if it exists;
     *         otherwise, returns <code>null</code>
     */
    PropertyDescriptor getPropertyDescriptor(String name);

    /**
     * @param name Property Name
     * @return Sensitive Dynamic Property status
     */
    boolean isSensitiveDynamicProperty(String name);

    /**
     * @return the Process Group that this component belongs to, or an empty Optional if this component does not belong to a Process Group.
     * Note that some components, such as Reporting Tasks, will never belong to a Process Group.
     */
    Optional<ProcessGroup> getParentProcessGroup();

    @Override
    default AuthorizationResult checkAuthorization(Authorizer authorizer, RequestAction action, NiFiUser user, Map<String, String> resourceContext) {
        // if this is a modification request and the reporting task is restricted ensure the user has elevated privileges. if this
        // is not a modification request, we just want to use the normal rules
        if (RequestAction.WRITE.equals(action) && isRestricted()) {
            final Set<Authorizable> restrictedComponentsAuthorizables = RestrictedComponentsAuthorizableFactory.getRestrictedComponentsAuthorizable(getComponentClass());

            for (final Authorizable restrictedComponentsAuthorizable : restrictedComponentsAuthorizables) {
                final AuthorizationResult result = restrictedComponentsAuthorizable.checkAuthorization(authorizer, RequestAction.WRITE, user, resourceContext);
                if (Result.Denied.equals(result.getResult())) {
                    return result;
                }
            }
        }

        // defer to the base authorization check
        return ComponentAuthorizable.super.checkAuthorization(authorizer, action, user, resourceContext);
    }

    @Override
    default void authorize(Authorizer authorizer, RequestAction action, NiFiUser user, Map<String, String> resourceContext) throws AccessDeniedException {
        // if this is a modification request and the reporting task is restricted ensure the user has elevated privileges. if this
        // is not a modification request, we just want to use the normal rules
        if (RequestAction.WRITE.equals(action) && isRestricted()) {
            final Set<Authorizable> restrictedComponentsAuthorizables = RestrictedComponentsAuthorizableFactory.getRestrictedComponentsAuthorizable(getComponentClass());

            for (final Authorizable restrictedComponentsAuthorizable : restrictedComponentsAuthorizables) {
                restrictedComponentsAuthorizable.authorize(authorizer, RequestAction.WRITE, user, resourceContext);
            }
        }

        // defer to the base authorization check
        ComponentAuthorizable.super.authorize(authorizer, action, user, resourceContext);
    }

    ParameterLookup getParameterLookup();

    default String getClassLoaderIsolationKey(final PropertyContext context) {
        final ConfigurableComponent component = getComponent();
        if (!(component instanceof ClassloaderIsolationKeyProvider)) {
            return null;
        }

        try {
            return ((ClassloaderIsolationKeyProvider) component).getClassloaderIsolationKey(context);
        } catch (final Exception e) {
            getLogger().error("Failed to determine ClassLoader Isolation Key for {}. This could result in unexpected behavior by this processor.", this, e);
            return null;
        }
    }

}
