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
package org.apache.nifi.util.security;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Message Digest Utilities for standardized algorithm use within the framework
 */
public final class MessageDigestUtils {
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private static final int BUFFER_LENGTH = 1024;

    private static final int START_READ_INDEX = 0;

    private static final int STREAM_END_INDEX = -1;

    private MessageDigestUtils() {

    }

    /**
     * Get Digest using standard algorithm
     *
     * @param bytes Bytes to be digested
     * @return Computed Digest Bytes
     */
    public static byte[] getDigest(final byte[] bytes) {
        final MessageDigest messageDigest = getMessageDigest();
        messageDigest.update(bytes);
        return messageDigest.digest();
    }

    /**
     * Get Digest using standard algorithm
     *
     * @param inputStream Input Stream to be read and digested
     * @return Computed Digest Bytes
     * @throws IOException Thrown on InputStream.read()
     */
    public static byte[] getDigest(final InputStream inputStream) throws IOException {
        final MessageDigest messageDigest = getMessageDigest();

        final byte[] buffer = new byte[BUFFER_LENGTH];
        int bytesRead = inputStream.read(buffer, START_READ_INDEX, BUFFER_LENGTH);

        while (bytesRead > STREAM_END_INDEX) {
            messageDigest.update(buffer);
            bytesRead = inputStream.read(buffer, START_READ_INDEX, BUFFER_LENGTH);
        }

        return messageDigest.digest();
    }

    private static MessageDigest getMessageDigest() {
        try {
            return MessageDigest.getInstance(DIGEST_ALGORITHM);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(DIGEST_ALGORITHM, e);
        }
    }
}
