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
package org.apache.nifi.registry.web.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Filter for determining appropriate logout location.
 */
public class LogoutFilter implements Filter {

    private static final String OIDC_LOGOUT_URL = "/nifi-registry-api/access/oidc/logout";

    private static final String LOGOUT_COMPLETE_URL = "/nifi-registry-api/access/logout/complete";

    private ServletContext servletContext;

    @Override
    public void init(FilterConfig filterConfig) {
        servletContext = filterConfig.getServletContext();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException {
        final boolean supportsOidc = Boolean.parseBoolean(servletContext.getInitParameter("oidc-supported"));

        final HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        if (supportsOidc) {
            httpServletResponse.sendRedirect(OIDC_LOGOUT_URL);
        } else {
            httpServletResponse.sendRedirect(LOGOUT_COMPLETE_URL);
        }
    }

    @Override
    public void destroy() {
    }
}
