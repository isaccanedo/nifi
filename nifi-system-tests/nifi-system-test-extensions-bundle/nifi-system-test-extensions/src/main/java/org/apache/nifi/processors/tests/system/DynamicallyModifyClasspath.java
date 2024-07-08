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

package org.apache.nifi.processors.tests.system;

import org.apache.nifi.annotation.behavior.RequiresInstanceClassLoading;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.resource.ResourceCardinality;
import org.apache.nifi.components.resource.ResourceType;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RequiresInstanceClassLoading
public class DynamicallyModifyClasspath extends AbstractProcessor {

    static final PropertyDescriptor URLS = new PropertyDescriptor.Builder()
        .name("URLs to Load")
        .description("URLs to load onto the classpath")
        .required(false)
        .dynamicallyModifiesClasspath(true)
        .identifiesExternalResource(ResourceCardinality.MULTIPLE, ResourceType.URL, ResourceType.FILE, ResourceType.DIRECTORY)
        .build();

    static final PropertyDescriptor CLASS_TO_LOAD = new PropertyDescriptor.Builder()
        .name("Class to Load")
        .description("The name of the Class to load")
        .required(true)
        .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
        .build();

    static final PropertyDescriptor SLEEP_DURATION = new PropertyDescriptor.Builder()
        .name("Sleep Duration")
        .description("Amount of time to sleep in the onTrigger method")
        .required(false)
        .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
        .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
        .defaultValue("0 sec")
        .build();


    static final Relationship REL_SUCCESS = new Relationship.Builder()
        .name("success")
        .description("FlowFiles are routed to this relationship if the specified class can be loaded")
        .build();
    static final Relationship REL_FAILURE = new Relationship.Builder()
        .name("failure")
        .description("FlowFiles are routed to this relationship if the specified class cannot be loaded")
        .build();



    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return Arrays.asList(URLS, CLASS_TO_LOAD, SLEEP_DURATION);
    }

    @Override
    public Set<Relationship> getRelationships() {
        return new HashSet<>(Arrays.asList(REL_SUCCESS, REL_FAILURE));
    }


    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
        FlowFile flowFile = session.get();
        if (flowFile == null) {
            return;
        }

        final long sleepMillis = context.getProperty(SLEEP_DURATION).evaluateAttributeExpressions(flowFile).asTimePeriod(TimeUnit.MILLISECONDS);
        if (sleepMillis > 0) {
            getLogger().info("Sleeping for {} millis", sleepMillis);

            try {
                Thread.sleep(sleepMillis);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            getLogger().info("Finished sleeping");
        }

        final String classToLoad = context.getProperty(CLASS_TO_LOAD).getValue();
        try {
            final Class<?> clazz = Class.forName(classToLoad);
            try (final OutputStream out = session.write(flowFile);
                 final OutputStreamWriter streamWriter = new OutputStreamWriter(out);
                 final BufferedWriter writer = new BufferedWriter(streamWriter)) {

                writer.write(clazz.getName());
                writer.newLine();
                writer.write(clazz.getClassLoader().toString());
            }

            session.transfer(flowFile, REL_SUCCESS);
            getLogger().info("Transferred {} to success", flowFile);
        } catch (final Exception e) {
            getLogger().error("Failed to update {}", flowFile, e);

            session.transfer(flowFile, REL_FAILURE);
            getLogger().info("Transferred {} to failure", flowFile);
        }
    }
}
