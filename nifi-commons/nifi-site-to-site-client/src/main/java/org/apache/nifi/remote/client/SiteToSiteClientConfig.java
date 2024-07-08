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
package org.apache.nifi.remote.client;

import java.io.File;
import java.io.Serializable;
import java.net.InetAddress;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import org.apache.nifi.components.state.StateManager;
import org.apache.nifi.events.EventReporter;
import org.apache.nifi.remote.protocol.DataPacket;
import org.apache.nifi.remote.protocol.SiteToSiteTransportProtocol;
import org.apache.nifi.remote.protocol.http.HttpProxy;

public interface SiteToSiteClientConfig extends Serializable {

    /**
     * SiteToSite implementations should support multiple URLs when establishing a SiteToSite connection with a remote
     * NiFi instance to provide robust connectivity so that it can keep working as long as at least one of
     * the configured URLs is accessible.
     * @return the configured URLs for the remote NiFi instances.
     */
    Set<String> getUrls();

    /**
     * @param timeUnit unit over which to report the timeout
     * @return the communications timeout in given unit
     */
    long getTimeout(final TimeUnit timeUnit);

    /**
     * @param timeUnit the unit for which to report the time
     * @return the amount of time that a connection can remain idle before it is
     * "expired" and shut down
     */
    long getIdleConnectionExpiration(TimeUnit timeUnit);

    /**
     * @param timeUnit unit over which to report the time
     * @return the amount of time that a particular node will be ignored after a
     * communications error with that node occurs
     */
    long getPenalizationPeriod(TimeUnit timeUnit);

    /**
     * @return the SSL Context that is configured for this builder
     * @throws IllegalStateException if an SSLContext is being constructed and an error occurs doing so
     */
    SSLContext getSslContext();

    /**
     * @return the filename to use for the keystore, or <code>null</code> if none is configured
     */
    String getKeystoreFilename();

    /**
     * @return the password to use for the keystore, or <code>null</code> if none is configured
     */
    String getKeystorePassword();

    /**
     * @return the Type of the keystore, or <code>null</code> if none is configured
     */
    KeystoreType getKeystoreType();

    /**
     * @return the filename to use for the truststore, or <code>null</code> if none is configured
     */
    String getTruststoreFilename();

    /**
     * @return the password to use for the truststore, or <code>null</code> if none is configured
     */
    String getTruststorePassword();

    /**
     * @return the type of the truststore, or <code>null</code> if none is configured
     */
    KeystoreType getTruststoreType();

    /**
     * @return the file that is to be used for persisting the nodes of a remote
     *         cluster, if any
     */
    File getPeerPersistenceFile();

    /**
     * @return the StateManager to be used for persisting the nodes of a remote
     */
    StateManager getStateManager();

    /**
     * @return a PeerPersistence implementation based on configured persistent target
     */
    PeerPersistence getPeerPersistence();

    /**
     * @return a boolean indicating whether or not compression will be used to
     * transfer data to and from the remote instance
     */
    boolean isUseCompression();

    /**
     * @return a transport protocol to use
     */
    SiteToSiteTransportProtocol getTransportProtocol();

    /**
     * @return the name of the port that the client is to communicate with
     */
    String getPortName();

    /**
     * @return the identifier of the port that the client is to communicate with
     */
    String getPortIdentifier();

    /**
     * When pulling data from a NiFi instance, the sender chooses how large a
     * Transaction is. However, the client has the ability to request a
     * particular batch size/duration.
     *
     * @param timeUnit unit of time over which to report the duration
     * @return the maximum amount of time that we will request a NiFi instance
     * to send data to us in a Transaction
     */
    long getPreferredBatchDuration(TimeUnit timeUnit);

    /**
     * When pulling data from a NiFi instance, the sender chooses how large a
     * Transaction is. However, the client has the ability to request a
     * particular batch size/duration.
     *
     * @return returns the maximum number of bytes that we will request a NiFi
     * instance to send data to us in a Transaction
     */
    long getPreferredBatchSize();

    /**
     * When pulling data from a NiFi instance, the sender chooses how large a
     * Transaction is. However, the client has the ability to request a
     * particular batch size/duration.
     *
     * @return the maximum number of {@link DataPacket}s that we will request a
     * NiFi instance to send data to us in a Transaction
     */
    int getPreferredBatchCount();

    /**
     * When the contents of a remote NiFi instance are fetched, that information is cached
     * so that many calls that are made in a short period of time do not overwhelm the remote
     * NiFi instance. This method will indicate the number of milliseconds that this information
     * can be cached.
     *
     * @param unit the desired time unit
     * @return the number of milliseconds that the contents of a remote NiFi instance will be cached
     */
    long getCacheExpiration(TimeUnit unit);

    /**
     * @return the EventReporter that is to be used by clients to report events
     */
    EventReporter getEventReporter();

    /**
     * Return Proxy for HTTP Transport Protocol.
     * @return proxy or null if not specified
     */
    HttpProxy getHttpProxy();

    /**
     * @return the InetAddress to bind to for the local address when creating a socket, or
     *         {@code null} to bind to the {@code anyLocal} address.
     */
    InetAddress getLocalAddress();
}
