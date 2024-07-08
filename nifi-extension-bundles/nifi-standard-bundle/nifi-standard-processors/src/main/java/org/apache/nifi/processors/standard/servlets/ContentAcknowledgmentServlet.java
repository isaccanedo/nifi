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
package org.apache.nifi.processors.standard.servlets;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.Path;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Processor;
import org.apache.nifi.processors.standard.ListenHTTP;
import org.apache.nifi.processors.standard.ListenHTTP.FlowFileEntryTimeWrapper;
import org.apache.nifi.security.cert.StandardPrincipalFormatter;
import org.apache.nifi.util.FormatUtils;

@Path("/holds/*")
public class ContentAcknowledgmentServlet extends HttpServlet {

    public static final String DEFAULT_FOUND_SUBJECT = "none";
    private static final long serialVersionUID = -2675148117984902978L;

    private Processor processor;
    private Pattern authorizedPattern;
    private ComponentLog logger;
    private ConcurrentMap<String, FlowFileEntryTimeWrapper> flowFileMap;
    private int port;

    @SuppressWarnings("unchecked")
    @Override
    public void init(final ServletConfig config) throws ServletException {
        final ServletContext context = config.getServletContext();
        this.processor = (Processor) context.getAttribute(ListenHTTP.CONTEXT_ATTRIBUTE_PROCESSOR);
        this.logger = (ComponentLog) context.getAttribute(ListenHTTP.CONTEXT_ATTRIBUTE_LOGGER);
        this.authorizedPattern = (Pattern) context.getAttribute(ListenHTTP.CONTEXT_ATTRIBUTE_AUTHORITY_PATTERN);
        this.flowFileMap = (ConcurrentMap<String, FlowFileEntryTimeWrapper>) context.getAttribute(ListenHTTP.CONTEXT_ATTRIBUTE_FLOWFILE_MAP);
        this.port = (int) context.getAttribute(ListenHTTP.CONTEXT_ATTRIBUTE_PORT);
    }

    @Override
    protected void doDelete(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {

        if (request.getLocalPort() != port) {
            super.doDelete(request, response);
            return;
        }

        final X509Certificate[] certs = (X509Certificate[]) request.getAttribute("jakarta.servlet.request.X509Certificate");
        String foundSubject = DEFAULT_FOUND_SUBJECT;
        if (certs != null) {
            for (final X509Certificate cert : certs) {
                foundSubject = StandardPrincipalFormatter.getInstance().getSubject(cert);

                if (authorizedPattern.matcher(foundSubject).matches()) {
                    break;
                } else {
                    logger.warn("rejecting transfer attempt from [{}] because the DN is not authorized", foundSubject);
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "not allowed based on dn");
                    return;
                }
            }
        }

        final String uri = request.getRequestURI();
        final int slashIndex = uri.lastIndexOf("/");
        int questionIndex = uri.indexOf("?");
        if (questionIndex < 0) {
            questionIndex = uri.length();
        }

        final String uuid = uri.substring(slashIndex + 1, questionIndex);
        final FlowFileEntryTimeWrapper timeWrapper = flowFileMap.remove(uuid);
        if (timeWrapper == null) {
            logger.warn("received DELETE for HOLD with ID {} from Remote Host: [{}] Port [{}] SubjectDN [{}], but no HOLD exists with that ID; sending response with Status Code 404",
                    uuid, request.getRemoteHost(), request.getRemotePort(), foundSubject);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try {
            final Set<FlowFile> flowFiles = timeWrapper.getFlowFiles();

            final long transferTime = System.currentTimeMillis() - timeWrapper.getEntryTime();
            long totalFlowFileSize = 0;
            for (final FlowFile flowFile : flowFiles) {
                totalFlowFileSize += flowFile.getSize();
            }

            double seconds = (double) transferTime / 1000D;
            if (seconds <= 0D) {
                seconds = .00000001D;
            }
            final double bytesPerSecond = ((double) totalFlowFileSize / seconds);
            final String transferRate = FormatUtils.formatDataSize(bytesPerSecond) + "/sec";

            logger.info("received {} files/{} bytes from Remote Host: [{}] Port [{}] SubjectDN [{}] in {} milliseconds at a rate of {}; "
                    + "transferring to 'success': {}",
                    new Object[]{flowFiles.size(), totalFlowFileSize, request.getRemoteHost(), request.getRemotePort(), foundSubject, transferTime, transferRate, flowFiles});

            final String sendingSubject = foundSubject;
            final ProcessSession session = timeWrapper.getSession();
            session.transfer(flowFiles, ListenHTTP.RELATIONSHIP_SUCCESS);
            session.commitAsync(() -> {
                try {
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.flushBuffer();
                } catch (final Exception e) {
                    logger.error("Received DELETE for HOLD with ID {} from Remote Host: [{}] Port [{}] SubjectDN [{}]. FlowFiles were released but failed to acknowledge them.",
                        new Object[]{uuid, request.getRemoteHost(), request.getRemotePort(), sendingSubject, e.toString()});
                }
            });
        } catch (final Throwable t) {
            timeWrapper.getSession().rollback();
            logger.error("Received DELETE for HOLD with ID {} from Remote Host: [{}] Port [{}] SubjectDN [{}], but failed to process the request due to {}",
                    new Object[]{uuid, request.getRemoteHost(), request.getRemotePort(), foundSubject, t.toString()});
            if (logger.isDebugEnabled()) {
                logger.error("", t);
            }

            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
