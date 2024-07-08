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

package org.apache.nifi.cs.tests.system;

import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.processor.util.StandardValidators;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class StandardCountService extends AbstractControllerService implements CountService {
    static final PropertyDescriptor COUNT_SERVICE = new PropertyDescriptor.Builder()
        .name("Dependent Service")
        .displayName("Dependent Service")
        .description("An additional Controller Service to trigger for counting")
        .required(false)
        .identifiesControllerService(CountService.class)
        .build();
    static final PropertyDescriptor START_VALUE = new PropertyDescriptor.Builder()
        .name("Start Value")
        .displayName("Start Value")
        .description("The value to start counting from")
        .required(true)
        .addValidator(StandardValidators.LONG_VALIDATOR)
        .defaultValue("0")
        .build();

    private final AtomicLong counter = new AtomicLong(0L);

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return Arrays.asList(COUNT_SERVICE, START_VALUE);
    }

    @OnEnabled
    public void onEnabled(final ConfigurationContext context) {
        final long startValue = Long.parseLong(context.getProperty(START_VALUE).getValue());
        getLogger().info("Setting counter to {}", startValue);
        counter.set(startValue);
    }

    @Override
    public long count() {
        final CountService additionalService = getConfigurationContext().getProperty(COUNT_SERVICE).asControllerService(CountService.class);
        final long additionalCount = additionalService == null ? 0L : additionalService.count();

        return additionalCount + counter.incrementAndGet();
    }
}
