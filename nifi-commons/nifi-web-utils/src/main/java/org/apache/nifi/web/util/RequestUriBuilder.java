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

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Request URI Builder encapsulates URI construction handling supported HTTP proxy request headers
 */
public class RequestUriBuilder {
    private static final String ALLOWED_CONTEXT_PATHS_PARAMETER = "allowedContextPaths";

    private static final String COMMA_SEPARATOR = ",";

    private final String scheme;

    private final String host;

    private final int port;

    private final String contextPath;

    private String path;

    private String fragment = null;

    private RequestUriBuilder(final String scheme, final String host, final int port, final String contextPath) {
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.contextPath = contextPath;
    }

    /**
     * Return Builder from HTTP Servlet Request using Scheme, Host, Port, and Context Path reading from headers
     *
     * @param httpServletRequest HTTP Servlet Request
     * @return Request URI Builder
     */
    public static RequestUriBuilder fromHttpServletRequest(final HttpServletRequest httpServletRequest) {
        final List<String> allowedContextPaths = getAllowedContextPathsConfigured(httpServletRequest);
        return fromHttpServletRequest(httpServletRequest, allowedContextPaths);
    }

    /**
     * Return Builder from HTTP Servlet Request using Scheme, Host, Port, and Context Path reading from headers
     *
     * @param httpServletRequest HTTP Servlet Request
     * @param allowedContextPaths Comma-separated string of allowed context path values for proxy headers
     * @return Request URI Builder
     */
    public static RequestUriBuilder fromHttpServletRequest(final HttpServletRequest httpServletRequest, final List<String> allowedContextPaths) {
        final String scheme = StringUtils.defaultIfEmpty(WebUtils.determineProxiedScheme(httpServletRequest), httpServletRequest.getScheme());
        final String host = WebUtils.determineProxiedHost(httpServletRequest);
        final int port = WebUtils.getServerPort(httpServletRequest);
        final String contextPath = WebUtils.determineContextPath(httpServletRequest);
        WebUtils.verifyContextPath(allowedContextPaths, contextPath);
        return new RequestUriBuilder(scheme, host, port, contextPath);
    }

    /**
     * Set Path appended to Context Path on build
     *
     * @param path Path may be null
     * @return Request URI Builder
     */
    public RequestUriBuilder path(final String path) {
        this.path = path;
        return this;
    }

    /**
     * Set Fragment appended on build
     *
     * @param fragment Fragment may be null
     * @return Request URI Builder
     */
    public RequestUriBuilder fragment(final String fragment) {
        this.fragment = fragment;
        return this;
    }

    /**
     * Build URI using configured properties
     *
     * @return URI
     * @throws IllegalArgumentException Thrown on URI syntax exceptions
     */
    public URI build() {
        final String resourcePath = StringUtils.join(contextPath, path);
        try {
            return new URI(scheme, null, host, port, resourcePath, null, fragment);
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("Build URI Failed", e);
        }
    }

    private static List<String> getAllowedContextPathsConfigured(final HttpServletRequest httpServletRequest) {
        final ServletContext servletContext = httpServletRequest.getServletContext();
        final String allowedContextPathsParameter = servletContext.getInitParameter(ALLOWED_CONTEXT_PATHS_PARAMETER);
        final String[] allowedContextPathsParsed = StringUtils.split(allowedContextPathsParameter, COMMA_SEPARATOR);
        return allowedContextPathsParsed == null ? Collections.emptyList() : Arrays.asList(allowedContextPathsParsed);
    }
}
