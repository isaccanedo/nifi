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

package org.apache.nifi.controller.queue.clustered.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public interface LoadBalanceProtocol {

    /**
     * Receives FlowFiles from the peer attached to the socket
     *
     * @param socket the socket to read from and write to
     * @param in the InputStream to read from. The Socket's InputStream is wrapped with a BufferedInputStream, which is provided
     * here. If this method were to wrap the InputStream itself, a second call to the method may discard some data that was consumed
     * by the previous call's BufferedInputStream
     * @param out the OutputStream to write to
     *
     * @throws TransactionAbortedException if the transaction was aborted
     * @throws IOException if unable to communicate with the peer
     */
    void receiveFlowFiles(Socket socket, InputStream in, OutputStream out) throws IOException;

}
