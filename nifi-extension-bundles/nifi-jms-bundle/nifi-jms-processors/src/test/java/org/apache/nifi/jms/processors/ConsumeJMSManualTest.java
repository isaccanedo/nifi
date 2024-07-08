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

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQMessage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;

import jakarta.jms.BytesMessage;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.MapMessage;
import jakarta.jms.ObjectMessage;
import jakarta.jms.Session;
import jakarta.jms.StreamMessage;
import jakarta.jms.TextMessage;

@Disabled("Used for manual testing.")
public class ConsumeJMSManualTest {
    @Test
    public void testTextMessage() {
        MessageCreator messageCreator = session -> {
            TextMessage message = session.createTextMessage("textMessageContent");

            return message;
        };

        send(messageCreator);
    }

    @Test
    public void testBytesMessage() {
        MessageCreator messageCreator = session -> {
            BytesMessage message = session.createBytesMessage();

            message.writeBytes("bytesMessageContent".getBytes());

            return message;
        };

        send(messageCreator);
    }

    @Test
    public void testObjectMessage() {
        MessageCreator messageCreator = session -> {
            ObjectMessage message = session.createObjectMessage();

            message.setObject("stringAsObject");

            return message;
        };

        send(messageCreator);
    }

    @Test
    public void testStreamMessage() {
        MessageCreator messageCreator = session -> {
            StreamMessage message = session.createStreamMessage();

            message.writeBoolean(true);
            message.writeByte(Integer.valueOf(1).byteValue());
            message.writeBytes(new byte[] {2, 3, 4});
            message.writeShort((short) 32);
            message.writeInt(64);
            message.writeLong(128L);
            message.writeFloat(1.25F);
            message.writeDouble(100.867);
            message.writeChar('c');
            message.writeString("someString");
            message.writeObject("stringAsObject");

            return message;
        };

        send(messageCreator);
    }

    @Test
    public void testMapMessage() {
        MessageCreator messageCreator = session -> {
            MapMessage message = session.createMapMessage();

            message.setBoolean("boolean", true);
            message.setByte("byte", Integer.valueOf(1).byteValue());
            message.setBytes("bytes", new byte[] {2, 3, 4});
            message.setShort("short", (short) 32);
            message.setInt("int", 64);
            message.setLong("long", 128L);
            message.setFloat("float", 1.25F);
            message.setDouble("double", 100.867);
            message.setChar("char", 'c');
            message.setString("string", "someString");
            message.setObject("object", "stringAsObject");

            return message;
        };

        send(messageCreator);
    }

    @Test
    public void testUnsupportedMessage() {
        MessageCreator messageCreator = session -> new ActiveMQMessage();

        send(messageCreator);
    }

    private void send(MessageCreator messageCreator) {
        final String destinationName = "TEST";

        ConnectionFactory activeMqConnectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        final ConnectionFactory connectionFactory = new CachingConnectionFactory(activeMqConnectionFactory);

        JmsTemplate jmsTemplate = new JmsTemplate(connectionFactory);
        jmsTemplate.setPubSubDomain(false);
        jmsTemplate.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE);
        jmsTemplate.setReceiveTimeout(10L);

        try {
            jmsTemplate.send(destinationName, messageCreator);
        } finally {
            ((CachingConnectionFactory) jmsTemplate.getConnectionFactory()).destroy();
        }
    }
}
