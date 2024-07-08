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
package org.apache.nifi.jms.processors;

import java.nio.channels.Channel;

import jakarta.jms.Connection;

import org.apache.nifi.logging.ComponentLog;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;


/**
 * Base class for implementing publishing and consuming JMS workers.
 *
 * @see JMSPublisher
 * @see JMSConsumer
 */
abstract class JMSWorker {

    protected final JmsTemplate jmsTemplate;
    protected final ComponentLog processLog;
    private final CachingConnectionFactory connectionFactory;
    private boolean isValid = true;


    /**
     * Creates an instance of this worker initializing it with JMS
     * {@link Connection} and creating a target {@link Channel} used by
     * sub-classes to interact with JMS systems
     *
     * @param jmsTemplate the instance of {@link JmsTemplate}
     * @param processLog the instance of {@link ComponentLog}
     */
    public JMSWorker(CachingConnectionFactory connectionFactory, JmsTemplate jmsTemplate, ComponentLog processLog) {
        this.connectionFactory = connectionFactory;
        this.jmsTemplate = jmsTemplate;
        this.processLog = processLog;
    }

    public void shutdown() {
        connectionFactory.destroy();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[destination:" + this.jmsTemplate.getDefaultDestinationName()
                + "; pub-sub:" + this.jmsTemplate.isPubSubDomain() + ";]";
    }

    public boolean isValid() {
        return isValid;
    }

    public void setValid(boolean isValid) {
        this.isValid = isValid;
    }
}
