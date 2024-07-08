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

package org.apache.nifi.cluster.coordination.http.replication.okhttp;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonInclude.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationIntrospector;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.cluster.coordination.http.replication.HttpReplicationClient;
import org.apache.nifi.cluster.coordination.http.replication.PreparedRequest;
import org.apache.nifi.remote.protocol.http.HttpHeaders;
import org.apache.nifi.stream.io.GZIPOutputStream;
import org.apache.nifi.util.FormatUtils;
import org.apache.nifi.util.NiFiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StreamUtils;

public class OkHttpReplicationClient implements HttpReplicationClient {
    private static final Logger logger = LoggerFactory.getLogger(OkHttpReplicationClient.class);
    private static final Set<String> gzipEncodings = Stream.of("gzip", "x-gzip").collect(Collectors.toSet());

    private final EntitySerializer jsonSerializer;
    private final EntitySerializer xmlSerializer;

    private final ObjectMapper jsonCodec = new ObjectMapper();
    private final OkHttpClient okHttpClient;
    private final SSLContext sslContext;
    private final X509TrustManager trustManager;

    public OkHttpReplicationClient(
            final NiFiProperties properties,
            final SSLContext sslContext,
            final X509TrustManager trustManager
    ) {
        jsonCodec.setDefaultPropertyInclusion(Value.construct(Include.NON_NULL, Include.ALWAYS));
        jsonCodec.setAnnotationIntrospector(new JakartaXmlBindAnnotationIntrospector(jsonCodec.getTypeFactory()));

        jsonSerializer = new JsonEntitySerializer(jsonCodec);
        xmlSerializer = new XmlEntitySerializer();

        this.sslContext = sslContext;
        this.trustManager = trustManager;
        okHttpClient = createOkHttpClient(properties);
    }

    @Override
    public PreparedRequest prepareRequest(final String method, final Map<String, String> headers, final Object entity) {
        final boolean gzip = isUseGzip(headers);
        checkContentLengthHeader(method, headers);
        final RequestBody requestBody = createRequestBody(headers, entity, gzip);

        final Map<String, String> updatedHeaders = gzip ? updateHeadersForGzip(headers) : headers;
        return new OkHttpPreparedRequest(method, updatedHeaders, entity, requestBody);
    }

    /**
     * Checks the content length header on DELETE requests to ensure it is set to '0', avoiding request timeouts on replicated requests.
     *
     * @param method  the HTTP method of the request
     * @param headers the header keys and values
     */
    private void checkContentLengthHeader(String method, Map<String, String> headers) {
        // Only applies to DELETE requests
        if (HttpMethod.DELETE.equalsIgnoreCase(method)) {
            // Find the Content-Length header if present
            final String CONTENT_LENGTH_HEADER_KEY = "Content-Length";
            Map.Entry<String, String> contentLengthEntry = headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(CONTENT_LENGTH_HEADER_KEY)).findFirst().orElse(null);
            // If no CL header, do nothing
            if (contentLengthEntry != null) {
                // If the provided CL value is non-zero, override it
                if (contentLengthEntry.getValue() != null && !contentLengthEntry.getValue().equalsIgnoreCase("0")) {
                    logger.warn("This is a DELETE request; the provided Content-Length was {}; setting Content-Length to 0", contentLengthEntry.getValue());
                    headers.put(CONTENT_LENGTH_HEADER_KEY, "0");
                }
            }
        }
    }

    @Override
    public Response replicate(final PreparedRequest request, final String uri) throws IOException {
        if (!(Objects.requireNonNull(request) instanceof OkHttpPreparedRequest)) {
            throw new IllegalArgumentException("Replication Client is only able to replicate requests that the client itself has prepared");
        }

        return replicate((OkHttpPreparedRequest) request, uri);
    }

    private Response replicate(final OkHttpPreparedRequest request, final String uri) throws IOException {
        logger.debug("Replicating request {} to {}", request, uri);
        final Call call = createCall(request, uri);
        final okhttp3.Response callResponse = call.execute();

        final byte[] responseBytes = getResponseBytes(callResponse);
        final MultivaluedMap<String, String> responseHeaders = getHeaders(callResponse);
        logger.debug("Received response code {} with headers {} for request {} to {}", callResponse.code(), responseHeaders, request, uri);

        final Response response = new JacksonResponse(jsonCodec, responseBytes, responseHeaders, URI.create(uri), callResponse.code(), callResponse::close);
        return response;
    }

    private MultivaluedMap<String, String> getHeaders(final okhttp3.Response callResponse) {
        final Headers headers = callResponse.headers();
        final MultivaluedMap<String, String> headerMap = new MultivaluedHashMap<>();
        for (final String name : headers.names()) {
            final List<String> values = headers.values(name);
            headerMap.addAll(name, values);
        }

        return headerMap;
    }

    private byte[] getResponseBytes(final okhttp3.Response callResponse) throws IOException {
        final byte[] rawBytes = callResponse.body().bytes();

        final String contentEncoding = callResponse.header("Content-Encoding");
        if (gzipEncodings.contains(contentEncoding)) {
            try (final InputStream gzipIn = new GZIPInputStream(new ByteArrayInputStream(rawBytes));
                 final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                StreamUtils.copy(gzipIn, baos);
                return baos.toByteArray();
            }
        } else {
            return rawBytes;
        }
    }

    private Call createCall(final OkHttpPreparedRequest request, final String uri) {
        Request.Builder requestBuilder = new Request.Builder();

        final HttpUrl url = buildUrl(request, uri);
        requestBuilder = requestBuilder.url(url);

        // build the request body
        final String method = request.getMethod().toUpperCase();
        switch (method) {
            case "POST":
            case "PUT":
            case "PATCH":
                requestBuilder = requestBuilder.method(method, request.getRequestBody());
                break;
            default:
                requestBuilder = requestBuilder.method(method, null);
                break;
        }

        // Add appropriate headers
        for (final Map.Entry<String, String> header : request.getHeaders().entrySet()) {
            requestBuilder = requestBuilder.addHeader(header.getKey(), header.getValue());
        }

        // Build the request
        final Request okHttpRequest = requestBuilder.build();
        final Call call = okHttpClient.newCall(okHttpRequest);
        return call;
    }


    @SuppressWarnings("unchecked")
    private HttpUrl buildUrl(final OkHttpPreparedRequest request, final String uri) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(uri).newBuilder();
        switch (request.getMethod().toUpperCase()) {
            case HttpMethod.DELETE:
            case HttpMethod.HEAD:
            case HttpMethod.GET:
            case HttpMethod.OPTIONS:
                if (request.getEntity() instanceof MultivaluedMap) {
                    final MultivaluedMap<String, String> entityMap = (MultivaluedMap<String, String>) request.getEntity();

                    for (final Entry<String, List<String>> queryEntry : entityMap.entrySet()) {
                        final String queryName = queryEntry.getKey();
                        for (final String queryValue : queryEntry.getValue()) {
                            urlBuilder = urlBuilder.addQueryParameter(queryName, queryValue);
                        }
                    }
                }

                break;
        }

        return urlBuilder.build();
    }

    private RequestBody createRequestBody(final Map<String, String> headers, final Object entity, final boolean gzip) {
        final String contentType = getContentType(headers, "application/json");
        final byte[] serialized = serializeEntity(entity, contentType, gzip);

        final MediaType mediaType = MediaType.parse(contentType);
        return RequestBody.create(serialized, mediaType);
    }

    private String getContentType(final Map<String, String> headers, final String defaultValue) {
        for (final Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase("content-type")) {
                return entry.getValue();
            }
        }

        return defaultValue;
    }

    private byte[] serializeEntity(final Object entity, final String contentType, final boolean gzip) {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
             final OutputStream out = gzip ? new GZIPOutputStream(baos, 1) : baos) {

            getSerializer(contentType).serialize(entity, out);
            out.close();

            return baos.toByteArray();
        } catch (final IOException e) {
            // This should never happen with a ByteArrayOutputStream
            throw new RuntimeException("Failed to serialize entity for cluster replication", e);
        }
    }

    private EntitySerializer getSerializer(final String contentType) {
        switch (contentType.toLowerCase()) {
            case "application/xml":
                return xmlSerializer;
            case "application/json":
            default:
                return jsonSerializer;
        }
    }


    private Map<String, String> updateHeadersForGzip(final Map<String, String> headers) {
        final String encodingHeader = headers.get("Content-Encoding");
        if (gzipEncodings.contains(encodingHeader)) {
            return headers;
        }

        final Map<String, String> updatedHeaders = new HashMap<>(headers);
        updatedHeaders.put("Content-Encoding", "gzip");
        return updatedHeaders;
    }


    private boolean isUseGzip(final Map<String, String> headers) {
        String rawAcceptEncoding = headers.get(HttpHeaders.ACCEPT_ENCODING);

        if (rawAcceptEncoding == null) {
            rawAcceptEncoding = headers.get(HttpHeaders.ACCEPT_ENCODING.toLowerCase());
        }

        if (rawAcceptEncoding == null) {
            return false;
        } else {
            final String[] acceptEncodingTokens = rawAcceptEncoding.split(",");
            return Stream.of(acceptEncodingTokens)
                    .map(String::trim)
                    .filter(StringUtils::isNotEmpty)
                    .map(String::toLowerCase)
                    .anyMatch(gzipEncodings::contains);
        }
    }

    private OkHttpClient createOkHttpClient(final NiFiProperties properties) {
        final String connectionTimeout = properties.getClusterNodeConnectionTimeout();
        final long connectionTimeoutMs = (long) FormatUtils.getPreciseTimeDuration(connectionTimeout, TimeUnit.MILLISECONDS);
        final String readTimeout = properties.getClusterNodeReadTimeout();
        final long readTimeoutMs = (long) FormatUtils.getPreciseTimeDuration(readTimeout, TimeUnit.MILLISECONDS);

        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient().newBuilder();
        okHttpClientBuilder.connectTimeout(connectionTimeoutMs, TimeUnit.MILLISECONDS);
        okHttpClientBuilder.readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS);
        okHttpClientBuilder.followRedirects(true);
        final int connectionPoolSize = properties.getClusterNodeMaxConcurrentRequests();
        okHttpClientBuilder.connectionPool(new ConnectionPool(connectionPoolSize, 5, TimeUnit.MINUTES));
        okHttpClientBuilder.eventListener(new RequestReplicationEventListener());

        if (sslContext != null) {
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            okHttpClientBuilder.sslSocketFactory(sslSocketFactory, trustManager);
        }

        return okHttpClientBuilder.build();
    }
}
