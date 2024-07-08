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
package org.apache.nifi.security.util;

/**
 * This interface serves as an immutable domain object (acting as an internal DTO) for
 * the various keystore and truststore configuration settings necessary for building
 * {@link javax.net.ssl.SSLContext}s.
 */
public interface TlsConfiguration {
    /**
     * Returns {@code true} if the provided TlsConfiguration is {@code null} or <em>empty</em>
     * (i.e. neither any of the keystore nor truststore properties are populated).
     *
     * @param tlsConfiguration the container object to check
     * @return true if this container is empty or null
     */
    static boolean isEmpty(TlsConfiguration tlsConfiguration) {
        return tlsConfiguration == null || !(tlsConfiguration.isAnyKeystorePopulated() || tlsConfiguration.isAnyTruststorePopulated());
    }

    // Getters & setters

    String getKeystorePath();

    String getKeystorePassword();

    /**
     * Returns {@code "********"} if the keystore password is populated, {@code "null"} if not.
     *
     * @return a loggable String representation of the keystore password
     */
    String getKeystorePasswordForLogging();

    String getKeyPassword();

    /**
     * Returns {@code "********"} if the key password is populated, {@code "null"} if not.
     *
     * @return a loggable String representation of the key password
     */
    String getKeyPasswordForLogging();

    /**
     * Returns the "working" key password -- if the key password is populated, it is returned; otherwise the {@link #getKeystorePassword()} is returned.
     *
     * @return the key or keystore password actually populated
     */
    String getFunctionalKeyPassword();

    /**
     * Returns {@code "********"} if the functional key password is populated, {@code "null"} if not.
     *
     * @return a loggable String representation of the functional key password
     */
    String getFunctionalKeyPasswordForLogging();

    KeystoreType getKeystoreType();

    String getTruststorePath();

    String getTruststorePassword();

    /**
     * Returns {@code "********"} if the truststore password is populated, {@code "null"} if not.
     *
     * @return a loggable String representation of the truststore password
     */
    String getTruststorePasswordForLogging();

    KeystoreType getTruststoreType();

    String getProtocol();

    // Boolean validators for keystore & truststore

    /**
     * Returns {@code true} if the necessary properties are populated to instantiate a <strong>keystore</strong>. This does <em>not</em> validate the values (see {@link #isKeystoreValid()}).
     *
     * @return true if the path, password, and type are present
     */
    boolean isKeystorePopulated();

    /**
     * Returns {@code true} if <em>any</em> of the keystore properties is populated, indicating that the caller expects a valid keystore to be generated.
     *
     * @return true if any keystore properties are present
     */
    boolean isAnyKeystorePopulated();

    /**
     * Returns {@code true} if the necessary properties are populated and the keystore can be successfully instantiated (i.e. the path is valid and the password(s) are correct).
     *
     * @return true if the keystore properties are valid
     */
    boolean isKeystoreValid();

    /**
     * Returns {@code true} if the necessary properties are populated to instantiate a <strong>truststore</strong>. This does <em>not</em> validate the values (see {@link #isTruststoreValid()}).
     *
     * @return true if the path, password, and type are present
     */
    boolean isTruststorePopulated();

    /**
     * Returns {@code true} if <em>any</em> of the truststore properties is populated, indicating that the caller expects a valid truststore to be generated.
     *
     * @return true if any truststore properties are present
     */
    boolean isAnyTruststorePopulated();

    /**
     * Returns {@code true} if the necessary properties are populated and the truststore can be successfully instantiated (i.e. the path is valid and the password is correct).
     *
     * @return true if the truststore properties are valid
     */
    boolean isTruststoreValid();

    /**
     * Returns a {@code String[]} containing the keystore properties for logging. The order is
     * {@link #getKeystorePath()}, {@link #getKeystorePasswordForLogging()},
     * {@link #getFunctionalKeyPasswordForLogging()}, {@link #getKeystoreType()} (using the type or "null").
     *
     * @return a loggable String[]
     */
    String[] getKeystorePropertiesForLogging();

    /**
     * Returns a {@code String[]} containing the truststore properties for logging. The order is
     * {@link #getTruststorePath()}, {@link #getTruststorePasswordForLogging()},
     * {@link #getTruststoreType()} (using the type or "null").
     *
     * @return a loggable String[]
     */
    String[] getTruststorePropertiesForLogging();

    /**
     * Get Enabled TLS Protocol Versions
     *
     * @return Enabled TLS Protocols
     */
    String[] getEnabledProtocols();
}
