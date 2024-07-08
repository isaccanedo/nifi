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
package org.apache.nifi.processors.mqtt.common;

import org.apache.nifi.security.util.TlsConfiguration;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

public class MqttClientProperties {
    private BrokerUriHolder brokerUriHolder;

    private String clientId;

    private MqttVersion mqttVersion;

    private int keepAliveInterval;
    private int connectionTimeout;

    private boolean cleanSession;
    private Long sessionExpiryInterval;

    private TlsConfiguration tlsConfiguration;

    private String lastWillTopic;
    private String lastWillMessage;
    private Boolean lastWillRetain;
    private Integer lastWillQos;

    private String username;
    private String password;

    public List<URI> getBrokerUris() {
        return brokerUriHolder.getBrokerUris();
    }

    public String getRawBrokerUris() {
        return brokerUriHolder.getRawBrokerUris();
    }

    public String getProvenanceFormattedBrokerUris() {
        return brokerUriHolder.getProvenanceFormattedUris();
    }

    public void setBrokerUris(List<URI> brokerUris) {
        this.brokerUriHolder = new BrokerUriHolder(brokerUris);
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public MqttVersion getMqttVersion() {
        return mqttVersion;
    }

    public void setMqttVersion(MqttVersion mqttVersion) {
        this.mqttVersion = mqttVersion;
    }

    public int getKeepAliveInterval() {
        return keepAliveInterval;
    }

    public void setKeepAliveInterval(int keepAliveInterval) {
        this.keepAliveInterval = keepAliveInterval;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public boolean isCleanSession() {
        return cleanSession;
    }

    public void setCleanSession(boolean cleanSession) {
        this.cleanSession = cleanSession;
    }

    public Long getSessionExpiryInterval() {
        return sessionExpiryInterval;
    }

    public void setSessionExpiryInterval(Long sessionExpiryInterval) {
        this.sessionExpiryInterval = sessionExpiryInterval;
    }

    public TlsConfiguration getTlsConfiguration() {
        return tlsConfiguration;
    }

    public void setTlsConfiguration(TlsConfiguration tlsConfiguration) {
        this.tlsConfiguration = tlsConfiguration;
    }

    public String getLastWillTopic() {
        return lastWillTopic;
    }

    public void setLastWillTopic(String lastWillTopic) {
        this.lastWillTopic = lastWillTopic;
    }

    public String getLastWillMessage() {
        return lastWillMessage;
    }

    public void setLastWillMessage(String lastWillMessage) {
        this.lastWillMessage = lastWillMessage;
    }

    public Boolean getLastWillRetain() {
        return lastWillRetain;
    }

    public void setLastWillRetain(Boolean lastWillRetain) {
        this.lastWillRetain = lastWillRetain;
    }

    public Integer getLastWillQos() {
        return lastWillQos;
    }

    public void setLastWillQos(Integer lastWillQos) {
        this.lastWillQos = lastWillQos;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    private static class BrokerUriHolder {
        private final List<URI> brokerUris;

        private final String rawBrokerUris;
        private final String provenanceFormattedUris;

        public BrokerUriHolder(List<URI> brokerUris) {
            this.brokerUris = brokerUris;

            rawBrokerUris = createRawUris(brokerUris);
            provenanceFormattedUris = createProvenanceFormattedUris(brokerUris);
        }

        public List<URI> getBrokerUris() {
            return brokerUris;
        }

        public String getRawBrokerUris() {
            return rawBrokerUris;
        }

        public String getProvenanceFormattedUris() {
            return provenanceFormattedUris;
        }

        private String createRawUris(List<URI> brokerUris) {
            return brokerUris.stream().map(URI::toString).collect(Collectors.joining(","));
        }

        private String createProvenanceFormattedUris(List<URI> brokerUris) {
            final String base = createRawUris(brokerUris);

            if (brokerUris.size() > 1) {
                return "[" + base + "]";
            } else {
                return base;
            }
        }
    }
}
