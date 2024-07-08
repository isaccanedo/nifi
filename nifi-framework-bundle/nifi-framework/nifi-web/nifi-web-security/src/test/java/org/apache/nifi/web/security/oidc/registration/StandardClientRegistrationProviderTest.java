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
package org.apache.nifi.web.security.oidc.registration;

import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.SubjectType;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import org.apache.nifi.util.NiFiProperties;
import org.apache.nifi.web.security.oidc.OidcConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.web.client.RestOperations;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StandardClientRegistrationProviderTest {
    private static final String DISCOVERY_URL = "http://localhost/.well-known/openid-configuration";

    private static final String ISSUER = "http://localhost";

    private static final URI JWK_SET_URI = URI.create("http://localhost/jwks");

    private static final URI TOKEN_ENDPOINT_URI = URI.create("http://localhost/oauth2/v1/token");

    private static final URI USER_INFO_URI = URI.create("http://localhost/oauth2/v1/userinfo");

    private static final URI AUTHORIZATION_ENDPOINT_URI = URI.create("http://localhost/oauth2/v1/authorize");

    private static final String CLIENT_ID = "client-id";

    private static final String CLIENT_SECRET = "client-secret";

    private static final String USER_NAME_ATTRIBUTE_NAME = "sub";

    private static final Set<String> EXPECTED_SCOPES = new LinkedHashSet<>(Arrays.asList(OidcScopes.OPENID, OidcScopes.EMAIL, OidcScopes.PROFILE));

    private static final String INVALID_CONFIGURATION = "{}";

    @Mock
    RestOperations restOperations;

    @Test
    void testGetClientRegistration() {
        final NiFiProperties properties = getProperties();
        final StandardClientRegistrationProvider provider = new StandardClientRegistrationProvider(properties, restOperations);

        final OIDCProviderMetadata providerMetadata = getProviderMetadata();
        final String serializedMetadata = providerMetadata.toString();

        when(restOperations.getForObject(eq(DISCOVERY_URL), eq(String.class))).thenReturn(serializedMetadata);

        final ClientRegistration clientRegistration = provider.getClientRegistration();

        assertNotNull(clientRegistration);
        assertEquals(CLIENT_ID, clientRegistration.getClientId());
        assertEquals(CLIENT_SECRET, clientRegistration.getClientSecret());

        final ClientRegistration.ProviderDetails providerDetails = clientRegistration.getProviderDetails();
        assertEquals(ISSUER, providerDetails.getIssuerUri());
        assertEquals(JWK_SET_URI.toString(), providerDetails.getJwkSetUri());
        assertEquals(AUTHORIZATION_ENDPOINT_URI.toString(), providerDetails.getAuthorizationUri());
        assertEquals(TOKEN_ENDPOINT_URI.toString(), providerDetails.getTokenUri());

        final ClientRegistration.ProviderDetails.UserInfoEndpoint userInfoEndpoint = providerDetails.getUserInfoEndpoint();
        assertEquals(USER_INFO_URI.toString(), userInfoEndpoint.getUri());
        assertEquals(USER_NAME_ATTRIBUTE_NAME, userInfoEndpoint.getUserNameAttributeName());
        assertEquals(AuthenticationMethod.HEADER, userInfoEndpoint.getAuthenticationMethod());

        final Set<String> scopes = clientRegistration.getScopes();
        assertEquals(EXPECTED_SCOPES, scopes);
    }

    @Test
    void testGetClientRegistrationRetrievalFailed() {
        final NiFiProperties properties = getProperties();
        final StandardClientRegistrationProvider provider = new StandardClientRegistrationProvider(properties, restOperations);

        when(restOperations.getForObject(eq(DISCOVERY_URL), eq(String.class))).thenThrow(new RuntimeException());

        assertThrows(OidcConfigurationException.class, provider::getClientRegistration);
    }

    @Test
    void testGetClientRegistrationParsingFailed() {
        final NiFiProperties properties = getProperties();
        final StandardClientRegistrationProvider provider = new StandardClientRegistrationProvider(properties, restOperations);

        when(restOperations.getForObject(eq(DISCOVERY_URL), eq(String.class))).thenReturn(INVALID_CONFIGURATION);

        assertThrows(OidcConfigurationException.class, provider::getClientRegistration);
    }

    private NiFiProperties getProperties() {
        final Properties properties = new Properties();
        properties.put(NiFiProperties.SECURITY_USER_OIDC_DISCOVERY_URL, DISCOVERY_URL);
        properties.put(NiFiProperties.SECURITY_USER_OIDC_CLIENT_ID, CLIENT_ID);
        properties.put(NiFiProperties.SECURITY_USER_OIDC_CLIENT_SECRET, CLIENT_SECRET);
        properties.put(NiFiProperties.SECURITY_USER_OIDC_CLAIM_IDENTIFYING_USER, USER_NAME_ATTRIBUTE_NAME);
        properties.put(NiFiProperties.SECURITY_USER_OIDC_ADDITIONAL_SCOPES, OidcScopes.PROFILE);
        return NiFiProperties.createBasicNiFiProperties(null, properties);
    }

    private OIDCProviderMetadata getProviderMetadata() {
        final Issuer issuer = new Issuer(ISSUER);
        final List<SubjectType> subjectTypes = Collections.singletonList(SubjectType.PUBLIC);
        final OIDCProviderMetadata providerMetadata = new OIDCProviderMetadata(issuer, subjectTypes, JWK_SET_URI);
        providerMetadata.setTokenEndpointURI(TOKEN_ENDPOINT_URI);
        providerMetadata.setUserInfoEndpointURI(USER_INFO_URI);
        providerMetadata.setAuthorizationEndpointURI(AUTHORIZATION_ENDPOINT_URI);
        final Scope scopes = new Scope();
        scopes.add(OidcScopes.OPENID);
        scopes.add(OidcScopes.EMAIL);
        scopes.add(OidcScopes.PROFILE);
        scopes.add(OidcScopes.ADDRESS);
        providerMetadata.setScopes(scopes);
        return providerMetadata;
    }
}
