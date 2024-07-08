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
package org.apache.nifi.jms.cf;

import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.logging.ComponentLog;

import jakarta.jms.ConnectionFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * Sub-class of {@link JMSConnectionFactoryHandler} only for testing purpose
 */
public class JMSConnectionFactoryHandlerForTest extends JMSConnectionFactoryHandler {

    private final Map<String, Object> configuredProperties = new HashMap<>();

    public JMSConnectionFactoryHandlerForTest(ConfigurationContext context, ComponentLog logger) {
        super(context, logger);
        setConnectionFactoryProperties(null);
    }

    @Override
    void setProperty(ConnectionFactory connectionFactory, String propertyName, Object propertyValue) {
        configuredProperties.put(propertyName, propertyValue);
    }

    public Map<String, Object> getConfiguredProperties() {
        return configuredProperties;
    }
}
