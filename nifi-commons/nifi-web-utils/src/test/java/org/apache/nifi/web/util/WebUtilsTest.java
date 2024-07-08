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
package org.apache.nifi.web.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class WebUtilsTest {

    @Mock(strictness = Mock.Strictness.LENIENT)
    private HttpServletRequest request;

    @Test
    public void testDeterminedProxiedSchemeWhenNoHeaders() {
        when(request.getHeader(any())).thenReturn(null);
        when(request.getScheme()).thenReturn("https");
        assertEquals("https", WebUtils.determineProxiedScheme(request));
    }

    @Test
    public void testDeterminedProxiedSchemeWhenXProxySchemeAvailable() {
        when(request.getHeader(eq(WebUtils.PROXY_SCHEME_HTTP_HEADER))).thenReturn("http");
        assertEquals("http", WebUtils.determineProxiedScheme(request));
    }

    @Test
    public void testDeterminedProxiedSchemeWhenXForwardedProtoAvailable() {
        when(request.getHeader(eq(WebUtils.FORWARDED_PROTO_HTTP_HEADER))).thenReturn("http");
        assertEquals("http", WebUtils.determineProxiedScheme(request));
    }

    @Test
    public void testDetermineProxiedHostWhenNoHeaders() {
        when(request.getHeader(any())).thenReturn(null);
        when(request.getServerName()).thenReturn("localhost");
        assertEquals("localhost", WebUtils.determineProxiedHost(request));
    }

    @Test
    public void testDetermineProxiedHostWhenXProxyHostAvailable() {
        when(request.getHeader(eq(WebUtils.PROXY_HOST_HTTP_HEADER))).thenReturn("x-proxy-host");
        assertEquals("x-proxy-host", WebUtils.determineProxiedHost(request));
    }

    @Test
    public void testDetermineProxiedHostWhenXProxyHostAvailableWithPort() {
        when(request.getHeader(eq(WebUtils.PROXY_HOST_HTTP_HEADER))).thenReturn("x-proxy-host:443");
        assertEquals("x-proxy-host", WebUtils.determineProxiedHost(request));
    }

    @Test
    public void testDetermineProxiedHostWhenXForwardedHostAvailable() {
        when(request.getHeader(eq(WebUtils.FORWARDED_HOST_HTTP_HEADER))).thenReturn("x-forwarded-host");
        assertEquals("x-forwarded-host", WebUtils.determineProxiedHost(request));
    }

    @Test
    public void testDetermineProxiedHostWhenXForwardedHostAvailableWithPort() {
        when(request.getHeader(eq(WebUtils.FORWARDED_HOST_HTTP_HEADER))).thenReturn("x-forwarded-host:443");
        assertEquals("x-forwarded-host", WebUtils.determineProxiedHost(request));
    }

    @Test
    public void testDetermineProxiedPortWhenNoHeaders() {
        when(request.getServerPort()).thenReturn(443);
        assertEquals("443", WebUtils.determineProxiedPort(request));
    }

    @Test
    public void testDetermineProxiedPortWhenXProxyPortAvailable() {
        when(request.getHeader(eq(WebUtils.PROXY_HOST_HTTP_HEADER))).thenReturn("x-proxy-host");
        when(request.getHeader(eq(WebUtils.PROXY_PORT_HTTP_HEADER))).thenReturn("8443");
        assertEquals("8443", WebUtils.determineProxiedPort(request));
    }

    @Test
    public void testDetermineProxiedPortWhenPortInXProxyHost() {
        when(request.getHeader(eq(WebUtils.PROXY_HOST_HTTP_HEADER))).thenReturn("x-proxy-host:1234");
        assertEquals("1234", WebUtils.determineProxiedPort(request));
    }

    @Test
    public void testDetermineProxiedPortWhenXProxyPortOverridesXProxy() {
        when(request.getHeader(eq(WebUtils.PROXY_HOST_HTTP_HEADER))).thenReturn("x-proxy-host:1234");
        when(request.getHeader(eq(WebUtils.PROXY_PORT_HTTP_HEADER))).thenReturn("8443");
        assertEquals("8443", WebUtils.determineProxiedPort(request));
    }

    @Test
    public void testDetermineProxiedPortWhenXForwardedPortAvailable() {
        when(request.getHeader(eq(WebUtils.FORWARDED_HOST_HTTP_HEADER))).thenReturn("x-forwarded-host");
        when(request.getHeader(eq(WebUtils.FORWARDED_PORT_HTTP_HEADER))).thenReturn("8443");
        assertEquals("8443", WebUtils.determineProxiedPort(request));
    }

    @Test
    public void testDetermineProxiedPortWhenPortInXForwardedHost() {
        when(request.getHeader(eq(WebUtils.FORWARDED_HOST_HTTP_HEADER))).thenReturn("x-forwarded-host:1234");
        assertEquals("1234", WebUtils.determineProxiedPort(request));
    }

    @Test
    public void testDetermineProxiedPortWhenXForwardedPortOverridesXForwardedHost() {
        when(request.getHeader(eq(WebUtils.FORWARDED_HOST_HTTP_HEADER))).thenReturn("x-forwarded-host:1234");
        when(request.getHeader(eq(WebUtils.FORWARDED_PORT_HTTP_HEADER))).thenReturn("8443");
        assertEquals("8443", WebUtils.determineProxiedPort(request));
    }

}
