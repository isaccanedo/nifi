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
package org.apache.nifi.web.api.config;

import org.apache.nifi.web.NiFiCoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps NiFi core exceptions into client responses.
 */
@Provider
public class NiFiCoreExceptionMapper implements ExceptionMapper<NiFiCoreException> {

    private static final Logger logger = LoggerFactory.getLogger(NiFiCoreExceptionMapper.class);

    @Override
    public Response toResponse(NiFiCoreException exception) {
        // log the error
        logger.warn("{}. Returning {} response.", exception, Response.Status.CONFLICT, exception);
        return Response.status(Response.Status.CONFLICT).entity(exception.getMessage()).type("text/plain").build();
    }

}
