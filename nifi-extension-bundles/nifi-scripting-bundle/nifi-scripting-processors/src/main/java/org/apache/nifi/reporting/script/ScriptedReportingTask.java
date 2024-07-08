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
package org.apache.nifi.reporting.script;

import org.apache.commons.io.IOUtils;
import org.apache.nifi.annotation.behavior.DynamicProperty;
import org.apache.nifi.annotation.behavior.Restricted;
import org.apache.nifi.annotation.behavior.Restriction;
import org.apache.nifi.annotation.behavior.SupportsSensitiveDynamicProperties;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.RequiredPermission;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.metrics.jvm.JmxJvmMetrics;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.processors.script.ScriptRunner;
import org.apache.nifi.reporting.AbstractReportingTask;
import org.apache.nifi.reporting.ReportingContext;
import org.apache.nifi.script.ScriptingComponentHelper;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A Reporting task whose body is provided by a script (via supported JSR-223 script engines)
 */
@SupportsSensitiveDynamicProperties
@Tags({"reporting", "script", "execute", "groovy"})
@CapabilityDescription("Provides reporting and status information to a script. ReportingContext, ComponentLog, and VirtualMachineMetrics objects are made available "
        + "as variables (context, log, and vmMetrics, respectively) to the script for further processing. The context makes various information available such "
        + "as events, provenance, bulletins, controller services, process groups, Java Virtual Machine metrics, etc.")
@DynamicProperty(
        name = "A script engine property to update",
        value = "The value to set it to",
        expressionLanguageScope = ExpressionLanguageScope.ENVIRONMENT,
        description = "Updates a script engine property specified by the Dynamic Property's key with the value "
                + "specified by the Dynamic Property's value")
@Restricted(
        restrictions = {
                @Restriction(
                        requiredPermission = RequiredPermission.EXECUTE_CODE,
                        explanation = "Provides operator the ability to execute arbitrary code assuming all permissions that NiFi has.")
        }
)
public class ScriptedReportingTask extends AbstractReportingTask {

    protected volatile ScriptingComponentHelper scriptingComponentHelper = new ScriptingComponentHelper();
    private volatile String scriptToRun = null;
    private volatile JmxJvmMetrics vmMetrics;

    /**
     * Returns a list of property descriptors supported by this processor. The list always includes properties such as
     * script engine name, script file name, script body name, script arguments, and an external module path. If the
     * scripted processor also defines supported properties, those are added to the list as well.
     *
     * @return a List of PropertyDescriptor objects supported by this processor
     */
    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        synchronized (scriptingComponentHelper.isInitialized) {
            if (!scriptingComponentHelper.isInitialized.get()) {
                scriptingComponentHelper.createResources();
            }
        }

        return Collections.unmodifiableList(scriptingComponentHelper.getDescriptors());
    }

    /**
     * Returns a PropertyDescriptor for the given name. This is for the user to be able to define their own properties
     * which will be available as variables in the script
     *
     * @param propertyDescriptorName used to lookup if any property descriptors exist for that name
     * @return a PropertyDescriptor object corresponding to the specified dynamic property name
     */
    @Override
    protected PropertyDescriptor getSupportedDynamicPropertyDescriptor(final String propertyDescriptorName) {
        return new PropertyDescriptor.Builder()
                .name(propertyDescriptorName)
                .required(false)
                .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
                .expressionLanguageSupported(ExpressionLanguageScope.ENVIRONMENT)
                .dynamic(true)
                .build();
    }

    @Override
    protected Collection<ValidationResult> customValidate(ValidationContext validationContext) {
        return scriptingComponentHelper.customValidate(validationContext);
    }

    /**
     * Performs setup operations when the processor is scheduled to run. This includes evaluating the processor's
     * properties, as well as reloading the script (from file or the "Script Body" property)
     *
     * @param context the context in which to perform the setup operations
     */
    @OnScheduled
    public void setup(final ConfigurationContext context) {
        scriptingComponentHelper.setupVariables(context);

        // Create a script runner
        scriptToRun = scriptingComponentHelper.getScriptBody();

        try {
            String scriptPath = scriptingComponentHelper.getScriptPath();
            if (scriptToRun == null && scriptPath != null) {
                try (final FileInputStream scriptStream = new FileInputStream(scriptPath)) {
                    scriptToRun = IOUtils.toString(scriptStream, Charset.defaultCharset());
                }
            }
        } catch (IOException ioe) {
            throw new ProcessException(ioe);
        }
        scriptingComponentHelper.setupScriptRunners(1, scriptToRun, getLogger());

        vmMetrics = JmxJvmMetrics.getInstance();
    }

    @Override
    public void onTrigger(final ReportingContext context) {
        synchronized (scriptingComponentHelper.isInitialized) {
            if (!scriptingComponentHelper.isInitialized.get()) {
                scriptingComponentHelper.createResources();
            }
        }
        ScriptRunner scriptRunner = scriptingComponentHelper.scriptRunnerQ.poll();
        ComponentLog log = getLogger();
        if (scriptRunner == null) {
            // No engine available so nothing more to do here
            return;
        }

        try {
            ScriptEngine scriptEngine = scriptRunner.getScriptEngine();
            try {
                Bindings bindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
                if (bindings == null) {
                    bindings = new SimpleBindings();
                }
                bindings.put("context", context);
                bindings.put("log", log);
                bindings.put("vmMetrics", vmMetrics);

                // Find the user-added properties and set them on the script
                for (Map.Entry<PropertyDescriptor, String> property : context.getProperties().entrySet()) {
                    if (property.getKey().isDynamic()) {
                        // Add the dynamic property bound to its full PropertyValue to the script engine
                        if (property.getValue() != null) {
                            bindings.put(property.getKey().getName(), context.getProperty(property.getKey()));
                        }
                    }
                }
                scriptRunner.run(bindings);
                scriptingComponentHelper.scriptRunnerQ.offer(scriptRunner);

            } catch (ScriptException e) {
                // Create a new ScriptRunner to replace the one that caused an exception
                scriptingComponentHelper.setupScriptRunners(1, scriptToRun, getLogger());

                throw new ProcessException(e);
            }
        } catch (final Throwable t) {
            // Mimic AbstractProcessor behavior here
            getLogger().error("{} failed to process due to {}; rolling back session", this, t);
            throw t;
        }
    }
}
