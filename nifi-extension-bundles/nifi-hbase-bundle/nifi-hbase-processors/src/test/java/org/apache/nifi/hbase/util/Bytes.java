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
package org.apache.nifi.hbase.util;

import java.nio.charset.Charset;

public class Bytes {

    public static String toString(byte[] b) {
        return b == null ? null : toString(b, 0, b.length);
    }

    public static String toString(byte[] b1, String sep, byte[] b2) {
        return toString(b1, 0, b1.length) + sep + toString(b2, 0, b2.length);
    }

    public static String toString(byte[] b, int off, int len) {
        if (b == null) {
            return null;
        } else {
            return len == 0 ? "" : new String(b, off, len, Charset.forName("UTF-8"));
        }
    }

    public static long toLong(byte[] bytes) {
        return toLong(bytes, 0, 8);
    }

    private static long toLong(byte[] bytes, int offset, int length) {
        if (length == 8 && offset + length <= bytes.length) {
            long l = 0L;

            for (int i = offset; i < offset + length; ++i) {
                l <<= 8;
                l ^= bytes[i] & 255;
            }

            return l;
        } else {
            throw explainWrongLengthOrOffset(bytes, offset, length, 8);
        }
    }

    private static IllegalArgumentException explainWrongLengthOrOffset(byte[] bytes, int offset, int length, int expectedLength) {
        String reason;
        if (length != expectedLength) {
            reason = "Wrong length: " + length + ", expected " + expectedLength;
        } else {
            reason = "offset (" + offset + ") + length (" + length + ") exceed the" + " capacity of the array: " + bytes.length;
        }

        return new IllegalArgumentException(reason);
    }
}
