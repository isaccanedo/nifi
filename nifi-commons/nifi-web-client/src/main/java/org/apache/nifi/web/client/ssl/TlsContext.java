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
package org.apache.nifi.web.client.ssl;

import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import java.util.Optional;

/**
 * TLS Context provides components necessary for TLS communication
 */
public interface TlsContext {
    /**
     * Get TLS Protocol
     *
     * @return TLS Protocol
     */
    String getProtocol();

    /**
     * Get X.509 Trust Manager
     *
     * @return X.509 Trust Manager
     */
    X509TrustManager getTrustManager();

    /**
     * Get X.509 Key Manager
     *
     * @return X.509 Key Manager or empty when not configured
     */
    Optional<X509KeyManager> getKeyManager();
}
