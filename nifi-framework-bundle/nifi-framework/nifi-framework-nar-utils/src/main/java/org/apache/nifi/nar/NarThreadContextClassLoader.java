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
package org.apache.nifi.nar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.nifi.annotation.behavior.RequiresInstanceClassLoading;
import org.apache.nifi.authentication.LoginIdentityProvider;
import org.apache.nifi.authorization.AccessPolicyProvider;
import org.apache.nifi.authorization.Authorizer;
import org.apache.nifi.authorization.UserGroupProvider;
import org.apache.nifi.bundle.Bundle;
import org.apache.nifi.components.Validator;
import org.apache.nifi.components.state.StateProvider;
import org.apache.nifi.controller.ControllerService;
import org.apache.nifi.controller.repository.ContentRepository;
import org.apache.nifi.controller.repository.FlowFileRepository;
import org.apache.nifi.controller.repository.FlowFileSwapManager;
import org.apache.nifi.controller.status.history.StatusHistoryRepository;
import org.apache.nifi.flowanalysis.FlowAnalysisRule;
import org.apache.nifi.flowfile.FlowFilePrioritizer;
import org.apache.nifi.parameter.ParameterProvider;
import org.apache.nifi.processor.Processor;
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.io.StreamCallback;
import org.apache.nifi.provenance.ProvenanceRepository;
import org.apache.nifi.registry.flow.FlowRegistryClient;
import org.apache.nifi.reporting.ReportingTask;
import org.apache.nifi.util.NiFiProperties;

/**
 * THREAD SAFE
 */
public class NarThreadContextClassLoader extends URLClassLoader {

    private final ClassLoader forward = ClassLoader.getSystemClassLoader();
    private static final List<Class<?>> narSpecificClasses = new ArrayList<>();

    static {
        narSpecificClasses.add(Processor.class);
        narSpecificClasses.add(FlowFilePrioritizer.class);
        narSpecificClasses.add(ReportingTask.class);
        narSpecificClasses.add(FlowAnalysisRule.class);
        narSpecificClasses.add(ParameterProvider.class);
        narSpecificClasses.add(Validator.class);
        narSpecificClasses.add(InputStreamCallback.class);
        narSpecificClasses.add(OutputStreamCallback.class);
        narSpecificClasses.add(StreamCallback.class);
        narSpecificClasses.add(ControllerService.class);
        narSpecificClasses.add(Authorizer.class);
        narSpecificClasses.add(UserGroupProvider.class);
        narSpecificClasses.add(AccessPolicyProvider.class);
        narSpecificClasses.add(LoginIdentityProvider.class);
        narSpecificClasses.add(ProvenanceRepository.class);
        narSpecificClasses.add(StatusHistoryRepository.class);
        narSpecificClasses.add(FlowFileRepository.class);
        narSpecificClasses.add(FlowFileSwapManager.class);
        narSpecificClasses.add(ContentRepository.class);
        narSpecificClasses.add(StateProvider.class);
        narSpecificClasses.add(FlowRegistryClient.class);
    }

    private NarThreadContextClassLoader() {
        super(new URL[0]);
    }

    @Override
    public void clearAssertionStatus() {
        lookupClassLoader().clearAssertionStatus();
    }

    @Override
    public URL getResource(String name) {
        return lookupClassLoader().getResource(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return lookupClassLoader().getResourceAsStream(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        return lookupClassLoader().getResources(name);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return lookupClassLoader().loadClass(name);
    }

    @Override
    public void setClassAssertionStatus(String className, boolean enabled) {
        lookupClassLoader().setClassAssertionStatus(className, enabled);
    }

    @Override
    public void setDefaultAssertionStatus(boolean enabled) {
        lookupClassLoader().setDefaultAssertionStatus(enabled);
    }

    @Override
    public void setPackageAssertionStatus(String packageName, boolean enabled) {
        lookupClassLoader().setPackageAssertionStatus(packageName, enabled);
    }

    private ClassLoader lookupClassLoader() {
        // When new Threads are created, the new Thread inherits the ClassLoaderContext of
        // the caller. However, the call stack of that new Thread may not trace back to any NiFi-specific
        // code. Therefore, the NarThreadContextClassLoader will be unable to find the appropriate NAR
        // ClassLoader. As a result, we want to set the ContextClassLoader to the NAR ClassLoader that
        // contains the class or resource that we are looking for.
        // This locks the current Thread into the appropriate NAR ClassLoader Context. The framework will change
        // the ContextClassLoader back to the NarThreadContextClassLoader as appropriate via the
        // {@link FlowEngine.beforeExecute(Thread, Runnable)} and
        // {@link FlowEngine.afterExecute(Thread, Runnable)} methods.

        final StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        final Optional<ClassLoader> callerClassLoader = walker.walk(s ->
            s.map(StackWalker.StackFrame::getDeclaringClass)
                .map(this::findNarClass)
                .filter(Objects::nonNull)
                .map(Class::getClassLoader)
                .map(cl -> cl instanceof NarClassLoader ? cl : null)
                .filter(Objects::nonNull)
                .findFirst());
        callerClassLoader.ifPresent(Thread.currentThread()::setContextClassLoader);
        return callerClassLoader.orElse(forward);
    }

    private Class<?> findNarClass(final Class<?> cls) {
        for (final Class<?> narClass : narSpecificClasses) {
            if (narClass.isAssignableFrom(cls)) {
                return cls;
            } else if (cls.getEnclosingClass() != null) {
                return findNarClass(cls.getEnclosingClass());
            }
        }

        return null;
    }

    private static class SingletonHolder {

        public static final NarThreadContextClassLoader instance = new NarThreadContextClassLoader();
    }

    public static NarThreadContextClassLoader getInstance() {
        return SingletonHolder.instance;
    }

    /**
     * Constructs an instance of the given type using either default no args
     * constructor or a constructor which takes a NiFiProperties object
     * (preferred).
     *
     * @param <T> the type to create an instance for
     * @param implementationClassName the implementation class name
     * @param typeDefinition the type definition
     * @param nifiProperties the NiFiProperties instance
     * @return constructed instance
     * @throws InstantiationException if there is an error instantiating the class
     * @throws IllegalAccessException if there is an error accessing the type
     * @throws ClassNotFoundException if the class cannot be found
     */
    public static <T> T createInstance(final ExtensionManager extensionManager, final String implementationClassName, final Class<T> typeDefinition, final NiFiProperties nifiProperties)
        throws InstantiationException, IllegalAccessException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException {
        return createInstance(extensionManager, implementationClassName, typeDefinition, nifiProperties, UUID.randomUUID().toString());
    }

    /**
     * Constructs an instance of the given type using either default no args
     * constructor or a constructor which takes a NiFiProperties object
     * (preferred).
     *
     * @param <T> the type to create an instance for
     * @param implementationClassName the implementation class name
     * @param typeDefinition the type definition
     * @param nifiProperties the NiFiProperties instance
     * @param instanceId the UUID of the instance
     * @return constructed instance
     * @throws InstantiationException if there is an error instantiating the class
     * @throws IllegalAccessException if there is an error accessing the type
     * @throws ClassNotFoundException if the class cannot be found
     */
    public static <T> T createInstance(final ExtensionManager extensionManager, final String implementationClassName, final Class<T> typeDefinition, final NiFiProperties nifiProperties,
                                       final String instanceId) throws InstantiationException, IllegalAccessException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException {
        final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            final List<Bundle> bundles = extensionManager.getBundles(implementationClassName);
            if (bundles.size() == 0) {
                throw new IllegalStateException(String.format("The specified implementation class '%s' is not known to this nifi.", implementationClassName));
            }
            if (bundles.size() > 1) {
                throw new IllegalStateException(String.format("More than one bundle was found for the specified implementation class '%s', only one is allowed.", implementationClassName));
            }

            final Bundle bundle = bundles.get(0);
            final ClassLoader instanceClassLoader = createClassLoader(implementationClassName, instanceId, bundle, extensionManager);
            final Class<?> instanceClass = Class.forName(implementationClassName, true, instanceClassLoader);

            Thread.currentThread().setContextClassLoader(instanceClassLoader);
            final Class<?> desiredClass = instanceClass.asSubclass(typeDefinition);
            if (nifiProperties == null) {
                return typeDefinition.cast(desiredClass.getDeclaredConstructor().newInstance());
            }
            Constructor<?> constructor = null;

            try {
                constructor = desiredClass.getConstructor(NiFiProperties.class);
            } catch (NoSuchMethodException nsme) {
                try {
                    constructor = desiredClass.getConstructor();
                } catch (NoSuchMethodException nsme2) {
                    throw new IllegalStateException("Failed to find constructor which takes NiFiProperties as argument as well as the default constructor on "
                            + desiredClass.getName(), nsme2);
                }
            }
            try {
                if (constructor.getParameterTypes().length == 0) {
                    return typeDefinition.cast(constructor.newInstance());
                } else {
                    return typeDefinition.cast(constructor.newInstance(nifiProperties));
                }
            } catch (InvocationTargetException ite) {
                throw new IllegalStateException("Failed to instantiate a component due to (see target exception)", ite);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static ClassLoader createClassLoader(final String implementationClassName, final String instanceId, final Bundle bundle, final ExtensionManager extensionManager)
                throws ClassNotFoundException {

        final ClassLoader bundleClassLoader = bundle.getClassLoader();
        final Class<?> rawClass = Class.forName(implementationClassName, true, bundleClassLoader);

        final RequiresInstanceClassLoading instanceClassLoadingAnnotation = rawClass.getAnnotation(RequiresInstanceClassLoading.class);
        if (instanceClassLoadingAnnotation == null) {
            return bundleClassLoader;
        }

        final Set<URL> instanceUrls = new LinkedHashSet<>();
        final Set<File> narNativeLibDirs = new LinkedHashSet<>();

        final NarClassLoader narBundleClassLoader = (NarClassLoader) bundleClassLoader;
        narNativeLibDirs.add(narBundleClassLoader.getNARNativeLibDir());
        instanceUrls.addAll(Arrays.asList(narBundleClassLoader.getURLs()));

        ClassLoader ancestorClassLoader = narBundleClassLoader.getParent();

        if (instanceClassLoadingAnnotation.cloneAncestorResources()) {
            while (ancestorClassLoader instanceof NarClassLoader) {
                final Bundle ancestorNarBundle = extensionManager.getBundle(ancestorClassLoader);

                // stop including ancestor resources when we reach one of the APIs, or when we hit the Jetty NAR
                if (ancestorNarBundle == null || ancestorNarBundle.getBundleDetails().getCoordinate().getId().equals(NarClassLoaders.JETTY_NAR_ID)) {
                    break;
                }

                final NarClassLoader ancestorNarClassLoader = (NarClassLoader) ancestorClassLoader;

                narNativeLibDirs.add(ancestorNarClassLoader.getNARNativeLibDir());
                Collections.addAll(instanceUrls, ancestorNarClassLoader.getURLs());

                ancestorClassLoader = ancestorNarClassLoader.getParent();
            }
        }

        final InstanceClassLoader instanceClassLoader = new InstanceClassLoader(instanceId, implementationClassName, instanceUrls,
            Collections.emptySet(), narNativeLibDirs, ancestorClassLoader);
        extensionManager.registerInstanceClassLoader(instanceId, instanceClassLoader);
        return instanceClassLoader;
    }
}
