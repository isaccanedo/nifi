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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StandardTlsConfigurationTest {
    private static final String KEY_STORE_PATH = UUID.randomUUID().toString();

    private static final String KEY_STORE_PASSWORD = UUID.randomUUID().toString();

    private static final String KEY_PASSWORD = UUID.randomUUID().toString();

    private static final String KEY_STORE_TYPE = KeystoreType.PKCS12.getType();

    private static final String TRUST_STORE_PATH = UUID.randomUUID().toString();

    private static final String TRUST_STORE_PASSWORD = UUID.randomUUID().toString();

    private static final String TRUST_STORE_TYPE = KeystoreType.JKS.getType();

    private static final String PROTOCOL = TlsPlatform.getLatestProtocol();

    private static TlsConfiguration tlsConfiguration;

    @BeforeAll
    public static void setTlsConfiguration() {
        tlsConfiguration = new TemporaryKeyStoreBuilder().build();
    }

    @Test
    public void testIsKeyStoreValid() {
        assertTrue(tlsConfiguration.isKeystoreValid());
    }

    @Test
    public void testIsTrustStoreValid() {
        assertTrue(tlsConfiguration.isTruststoreValid());
    }

    @Test
    public void testEqualsNullProperties() {
        final StandardTlsConfiguration configuration = new StandardTlsConfiguration();
        assertEquals(new StandardTlsConfiguration(), configuration);
    }

    @Test
    public void testHashCodeNullProperties() {
        final StandardTlsConfiguration configuration = new StandardTlsConfiguration();
        assertEquals(new StandardTlsConfiguration().hashCode(), configuration.hashCode());
    }

    @Test
    public void testFunctionalKeyPasswordFromKeyStorePassword() {
        final TlsConfiguration configuration = new StandardTlsConfiguration(
                KEY_STORE_PATH,
                KEY_STORE_PASSWORD,
                null,
                KEY_STORE_TYPE,
                TRUST_STORE_PATH,
                TRUST_STORE_PASSWORD,
                TRUST_STORE_TYPE,
                PROTOCOL
        );
        assertEquals(KEY_STORE_PASSWORD, configuration.getFunctionalKeyPassword());
    }

    @Test
    public void testIsKeyStorePopulated() {
        final TlsConfiguration configuration = new StandardTlsConfiguration(
                KEY_STORE_PATH,
                KEY_STORE_PASSWORD,
                KEY_PASSWORD,
                KEY_STORE_TYPE,
                null,
                null,
                null,
                PROTOCOL
        );
        assertTrue(configuration.isKeystorePopulated());
        assertTrue(configuration.isAnyKeystorePopulated());
    }

    @Test
    public void testIsKeyStorePopulatedMissingProperties() {
        final TlsConfiguration configuration = new StandardTlsConfiguration(
                null,
                null,
                null,
                null,
                TRUST_STORE_PATH,
                TRUST_STORE_PASSWORD,
                TRUST_STORE_TYPE,
                PROTOCOL
        );
        assertFalse(configuration.isKeystorePopulated());
        assertFalse(configuration.isAnyKeystorePopulated());
    }

    @Test
    public void testIsTrustStorePopulated() {
        final TlsConfiguration configuration = new StandardTlsConfiguration(
                null,
                null,
                null,
                null,
                TRUST_STORE_PATH,
                TRUST_STORE_PASSWORD,
                TRUST_STORE_TYPE,
                PROTOCOL
        );
        assertTrue(configuration.isTruststorePopulated());
        assertTrue(configuration.isAnyTruststorePopulated());
    }

    @Test
    public void testIsTrustStorePopulatedMissingProperties() {
        final TlsConfiguration configuration = new StandardTlsConfiguration(
                KEY_STORE_PATH,
                KEY_STORE_PASSWORD,
                KEY_PASSWORD,
                KEY_STORE_TYPE,
                null,
                null,
                null,
                PROTOCOL
        );
        assertFalse(configuration.isTruststorePopulated());
        assertFalse(configuration.isAnyTruststorePopulated());
    }

    @Test
    public void testGetEnabledProtocolsVersionMatched() {
        final TlsConfiguration configuration = new StandardTlsConfiguration(
                KEY_STORE_PATH,
                KEY_STORE_PASSWORD,
                KEY_PASSWORD,
                KEY_STORE_TYPE,
                TRUST_STORE_PATH,
                TRUST_STORE_PASSWORD,
                TRUST_STORE_TYPE,
                PROTOCOL
        );

        final String[] enabledProtocols = configuration.getEnabledProtocols();
        assertArrayEquals(new String[]{PROTOCOL}, enabledProtocols);
    }

    @Test
    public void testGetEnabledProtocolsTlsProtocol() {
        final TlsConfiguration configuration = new StandardTlsConfiguration(
                KEY_STORE_PATH,
                KEY_STORE_PASSWORD,
                KEY_PASSWORD,
                KEY_STORE_TYPE,
                TRUST_STORE_PATH,
                TRUST_STORE_PASSWORD,
                TRUST_STORE_TYPE,
                StandardTlsConfiguration.TLS_PROTOCOL
        );

        final String[] enabledProtocols = configuration.getEnabledProtocols();
        assertArrayEquals(TlsPlatform.getPreferredProtocols().toArray(new String[0]), enabledProtocols);
    }

    @Test
    public void testGetEnabledProtocolsSslProtocol() {
        final TlsConfiguration configuration = new StandardTlsConfiguration(
                KEY_STORE_PATH,
                KEY_STORE_PASSWORD,
                KEY_PASSWORD,
                KEY_STORE_TYPE,
                TRUST_STORE_PATH,
                TRUST_STORE_PASSWORD,
                TRUST_STORE_TYPE,
                StandardTlsConfiguration.SSL_PROTOCOL
        );

        final String[] enabledProtocols = configuration.getEnabledProtocols();

        assertArrayEquals(TlsPlatform.getSupportedProtocols().toArray(new String[0]), enabledProtocols);
    }
}
