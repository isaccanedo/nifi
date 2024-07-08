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

import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.UriBuilderException;
import java.util.List;
import java.util.stream.Stream;

/**
 * Common utilities related to web development.
 */
public final class WebUtils {

    private static final Logger logger = LoggerFactory.getLogger(WebUtils.class);

    public static final String PROXY_SCHEME_HTTP_HEADER = "X-ProxyScheme";
    public static final String PROXY_HOST_HTTP_HEADER = "X-ProxyHost";
    public static final String PROXY_PORT_HTTP_HEADER = "X-ProxyPort";

    public static final String FORWARDED_PROTO_HTTP_HEADER = "X-Forwarded-Proto";
    public static final String FORWARDED_HOST_HTTP_HEADER = "X-Forwarded-Host";
    public static final String FORWARDED_PORT_HTTP_HEADER = "X-Forwarded-Port";

    public static final String PROXY_CONTEXT_PATH_HTTP_HEADER = "X-ProxyContextPath";
    public static final String FORWARDED_CONTEXT_HTTP_HEADER = "X-Forwarded-Context";
    public static final String FORWARDED_PREFIX_HTTP_HEADER = "X-Forwarded-Prefix";

    private static final String HOST_HEADER = "Host";

    private static final String EMPTY = "";

    private WebUtils() {
    }

    /**
     * Creates a client for non-secure requests. The client will be created
     * using the given configuration. Additionally, the client will be
     * automatically configured for JSON serialization/deserialization.
     *
     * @param config client configuration
     * @return a Client instance
     */
    public static Client createClient(final ClientConfig config) {
        return createClientHelper(config, null);
    }

    /**
     * Creates a client for secure requests. The client will be created using
     * the given configuration and security context. Additionally, the client
     * will be automatically configured for JSON serialization/deserialization.
     *
     * @param config client configuration
     * @param ctx    security context
     * @return a Client instance
     */
    public static Client createClient(final ClientConfig config, final SSLContext ctx) {
        return createClientHelper(config, ctx);
    }

    /**
     * A helper method for creating clients. The client will be created using
     * the given configuration and security context. Additionally, the client
     * will be automatically configured for JSON serialization/deserialization.
     *
     * @param config client configuration
     * @param ctx    security context, which may be null for non-secure client
     *               creation
     * @return a Client instance
     */
    private static Client createClientHelper(final ClientConfig config, final SSLContext ctx) {

        ClientBuilder clientBuilder = ClientBuilder.newBuilder();

        if (config != null) {
            clientBuilder = clientBuilder.withConfig(config);
        }

        if (ctx != null) {

            // Apache http DefaultHostnameVerifier that checks subject alternative names against the hostname of the URI
            clientBuilder = clientBuilder.sslContext(ctx).hostnameVerifier(new DefaultHostnameVerifier());
        }

        clientBuilder = clientBuilder.register(ObjectMapperResolver.class).register(JacksonJaxbJsonProvider.class);

        return clientBuilder.build();

    }

    /**
     * Throws an exception if the provided context path is not in the allowed context paths list.
     *
     * @param allowedContextPaths list of valid context paths
     * @param determinedContextPath   the normalized context path from a header
     * @throws UriBuilderException if the context path is not safe
     */
    public static void verifyContextPath(final List<String> allowedContextPaths, final String determinedContextPath) throws UriBuilderException {
        // If blank, ignore
        if (StringUtils.isBlank(determinedContextPath)) {
            return;
        }

        // Check it against the allowed list
        if (!allowedContextPaths.contains(determinedContextPath)) {
            final String msg = "The provided context path [" + determinedContextPath + "] was not registered as allowed " + allowedContextPaths;
            throw new UriBuilderException(msg);
        }
    }

    /**
     * Returns a normalized context path (leading /, no trailing /). If the parameter is blank, an empty string will be returned.
     *
     * @param determinedContextPath the raw context path
     * @return the normalized context path
     */
    public static String normalizeContextPath(String determinedContextPath) {
        if (StringUtils.isNotBlank(determinedContextPath)) {
            // normalize context path
            if (!determinedContextPath.startsWith("/")) {
                determinedContextPath = "/" + determinedContextPath;
            }

            if (determinedContextPath.endsWith("/")) {
                determinedContextPath = determinedContextPath.substring(0, determinedContextPath.length() - 1);
            }

            return determinedContextPath;
        } else {
            return "";
        }
    }

    /**
     * Returns a "safe" context path value from the request headers to use in a proxy environment.
     * This is used on the JSP to build the resource paths for the external resources (CSS, JS, etc.).
     * If no headers are present specifying this value, it is an empty string.
     *
     * @param request the HTTP request
     * @param allowedContextPaths list of allowed context paths
     * @param jspDisplayName the display name of the resource for log messages
     * @return the context path safe to be printed to the page
     */
    public static String sanitizeContextPath(final ServletRequest request, final List<String> allowedContextPaths, String jspDisplayName) {
        if (StringUtils.isBlank(jspDisplayName)) {
            jspDisplayName = "JSP page";
        }
        String contextPath = normalizeContextPath(determineContextPath((HttpServletRequest) request));
        try {
            verifyContextPath(allowedContextPaths, contextPath);
            return contextPath;
        } catch (UriBuilderException e) {
            logger.error("Error determining context path on {}", jspDisplayName, e);
            return EMPTY;
        }
    }

    /**
     * Determines the context path if populated in {@code X-ProxyContextPath}, {@code X-ForwardContext},
     * or {@code X-Forwarded-Prefix} headers.  If not populated, returns an empty string.
     *
     * @param request the HTTP request
     * @return the provided context path or an empty string
     */
    public static String determineContextPath(final HttpServletRequest request) {
        final String proxyContextPath = request.getHeader(PROXY_CONTEXT_PATH_HTTP_HEADER);
        final String forwardedContext = request.getHeader(FORWARDED_CONTEXT_HTTP_HEADER);
        final String prefix = request.getHeader(FORWARDED_PREFIX_HTTP_HEADER);

        String determinedContextPath = EMPTY;
        if (anyNotBlank(proxyContextPath, forwardedContext, prefix)) {
            // Implementing preferred order here: PCP, FC, FP
            determinedContextPath = Stream.of(proxyContextPath, forwardedContext, prefix)
                    .filter(StringUtils::isNotBlank).findFirst().orElse(EMPTY);
        }

        return determinedContextPath;
    }

    /**
     * Returns true if any of the provided arguments are not blank.
     *
     * @param strings a variable number of strings
     * @return true if any string has content (not empty or all whitespace)
     */
    private static boolean anyNotBlank(String... strings) {
        for (String s : strings) {
            if (StringUtils.isNotBlank(s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the value for the first key discovered when inspecting the current request. Will
     * return null if there are no keys specified or if none of the specified keys are found.
     *
     * @param httpServletRequest request
     * @param keys http header keys
     * @return the value for the first key found, or null if no matching keys found
     */
    public static String getFirstHeaderValue(final HttpServletRequest httpServletRequest, final String... keys) {
        if (keys == null) {
            return null;
        }

        for (final String key : keys) {
            final String value = httpServletRequest.getHeader(key);

            // if we found an entry for this key, return the value
            if (value != null) {
                return value;
            }
        }

        // unable to find any matching keys
        return null;
    }

    /**
     * Determines the scheme based on considering proxy related headers first and then falling back to the scheme of the servlet request.
     *
     * @param httpServletRequest the request
     * @return the determined scheme
     */
    public static String determineProxiedScheme(final HttpServletRequest httpServletRequest) {
        final String schemeHeaderValue = getFirstHeaderValue(httpServletRequest, PROXY_SCHEME_HTTP_HEADER, FORWARDED_PROTO_HTTP_HEADER);
        return StringUtils.isBlank(schemeHeaderValue) ? httpServletRequest.getScheme() : schemeHeaderValue;
    }

    /**
     * Determines the host based on considering proxy related headers first and falling back to the host of the servlet request.
     *
     * @param httpServletRequest the request
     * @return the determined host
     */
    public static String determineProxiedHost(final HttpServletRequest httpServletRequest) {
        final String hostHeaderValue = getFirstHeaderValue(httpServletRequest, PROXY_HOST_HTTP_HEADER, FORWARDED_HOST_HTTP_HEADER, HOST_HEADER);
        final String proxiedHost = determineProxiedHost(hostHeaderValue);
        return StringUtils.isBlank(proxiedHost) ? httpServletRequest.getServerName() : proxiedHost;
    }

    /**
     * Determines the host from the given header. The header value is intended to come from a header like X-ProxyHost or X-Forwarded-Host.
     *
     * @param hostHeaderValue the header value
     * @return the determined host, or null if a host can't be determined
     */
    public static String determineProxiedHost(final String hostHeaderValue) {
        final String host;
        // check for a port in the proxied host header
        String[] hostSplits = hostHeaderValue == null ? new String[] {} : hostHeaderValue.split(":");
        if (hostSplits.length >= 1 && hostSplits.length <= 2) {
            // zero or one occurrence of ':', this is an IPv4 address
            // strip off the port by reassigning host the 0th split
            host = hostSplits[0];
        } else if (hostSplits.length == 0) {
            // hostHeaderValue passed in was null, no splits
            host = null;
        } else {
            // hostHeaderValue has more than one occurrence of ":", IPv6 address
            host = hostHeaderValue;
        }
        return host;
    }

    /**
     * Get Server Port based on Proxy Headers with fallback to HttpServletRequest.getServerPort()
     *
     * @param httpServletRequest HTTP Servlet Request
     * @return Server port number
     */
    public static int getServerPort(final HttpServletRequest httpServletRequest) {
        final String port = determineProxiedPort(httpServletRequest);
        try {
            return Integer.parseInt(port);
        } catch (final NumberFormatException e) {
            return httpServletRequest.getServerPort();
        }
    }

    /**
     * Determines the port based on first considering proxy related headers and falling back to the port of the servlet request.
     *
     * @param httpServletRequest the request
     * @return the determined port
     */
    public static String determineProxiedPort(final HttpServletRequest httpServletRequest) {
        final String hostHeaderValue = getFirstHeaderValue(httpServletRequest, PROXY_HOST_HTTP_HEADER, FORWARDED_HOST_HTTP_HEADER);
        final String portHeaderValue = getFirstHeaderValue(httpServletRequest, PROXY_PORT_HTTP_HEADER, FORWARDED_PORT_HTTP_HEADER);

        final String proxiedPort = determineProxiedPort(hostHeaderValue, portHeaderValue);
        return StringUtils.isBlank(proxiedPort) ? String.valueOf(httpServletRequest.getServerPort()) : proxiedPort;
    }

    /**
     * Determines the port based on the header values. The header values are intended to come from headers like X-ProxyHost/X-ProxyPort
     * or X-Forwarded-Host/X-Forwarded-Port.
     *
     * @param hostHeaderValue the host header value
     * @param portHeaderValue the host port value
     * @return the determined port, or null if one can't be determined
     */
    public static String determineProxiedPort(final String hostHeaderValue, final String portHeaderValue) {
        final String port;
        // check for a port in the proxied host header
        String[] hostSplits = hostHeaderValue == null ? new String[] {} : hostHeaderValue.split(":");
        // determine the proxied port
        final String portFromHostHeader;
        if (hostSplits.length == 2) {
            // if the port is specified in the proxied host header, it will be overridden by the
            // port specified in X-ProxyPort or X-Forwarded-Port
            portFromHostHeader = hostSplits[1];
        } else {
            portFromHostHeader = null;
        }
        if (StringUtils.isNotBlank(portFromHostHeader) && StringUtils.isNotBlank(portHeaderValue)) {
            logger.warn("Forwarded Host Port [{}] replaced with Forwarded Port [{}]", portFromHostHeader, portHeaderValue);
        }
        port = StringUtils.isNotBlank(portHeaderValue) ? portHeaderValue : (StringUtils.isNotBlank(portFromHostHeader) ? portFromHostHeader : null);
        return port;
    }
}
