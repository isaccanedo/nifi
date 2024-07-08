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
package org.apache.nifi.stream.io.util;

import org.apache.nifi.stream.io.util.TextLineDemarcator.OffsetInfo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

@SuppressWarnings("resource")
public class TextLineDemarcatorTest {

    @Test
    public void nullStream() {
        assertThrows(IllegalArgumentException.class, () -> new TextLineDemarcator(null));
    }

    @Test
    public void illegalBufferSize() {
        assertThrows(IllegalArgumentException.class, () -> new TextLineDemarcator(mock(InputStream.class), -234));
    }

    @Test
    public void emptyStreamNoStartWithFilter() throws IOException {
        String data = "";
        InputStream is = stringToIs(data);
        TextLineDemarcator demarcator = new TextLineDemarcator(is);
        assertNull(demarcator.nextOffsetInfo());
    }


    @Test
    public void emptyStreamAndStartWithFilter() throws IOException {
        String data = "";
        InputStream is = stringToIs(data);
        TextLineDemarcator demarcator = new TextLineDemarcator(is);
        assertNull(demarcator.nextOffsetInfo("hello".getBytes()));
    }

    // this test has no assertions. It's success criteria is validated by lack
    // of failure (see NIFI-3278)
    @Test
    public void endsWithCRWithBufferLengthEqualStringLengthA() throws Exception {
        String str = "\r";
        InputStream is = stringToIs(str);
        TextLineDemarcator demarcator = new TextLineDemarcator(is, str.length());
        while (demarcator.nextOffsetInfo() != null) {
        }
    }

    @Test
    public void endsWithCRWithBufferLengthEqualStringLengthB() throws Exception {
        String str = "abc\r";
        InputStream is = stringToIs(str);
        TextLineDemarcator demarcator = new TextLineDemarcator(is, str.length());
        while (demarcator.nextOffsetInfo() != null) {
        }
    }

    @Test
    public void singleCR() throws IOException {
        InputStream is = stringToIs("\r");
        TextLineDemarcator demarcator = new TextLineDemarcator(is);
        OffsetInfo offsetInfo = demarcator.nextOffsetInfo();
        assertEquals(0, offsetInfo.getStartOffset());
        assertEquals(1, offsetInfo.getLength());
        assertEquals(1, offsetInfo.getCrlfLength());
        assertTrue(offsetInfo.isStartsWithMatch());
    }

    @Test
    public void singleLF() throws IOException {
        InputStream is = stringToIs("\n");
        TextLineDemarcator demarcator = new TextLineDemarcator(is);
        OffsetInfo offsetInfo = demarcator.nextOffsetInfo();
        assertEquals(0, offsetInfo.getStartOffset());
        assertEquals(1, offsetInfo.getLength());
        assertEquals(1, offsetInfo.getCrlfLength());
        assertTrue(offsetInfo.isStartsWithMatch());
    }

    @Test
    // essentially validates the internal 'isEol()' operation to ensure it will perform read-ahead
    public void crlfWhereLFdoesNotFitInInitialBuffer() throws Exception {
        InputStream is = stringToIs("oleg\r\njoe");
        TextLineDemarcator demarcator = new TextLineDemarcator(is, 5);
        OffsetInfo offsetInfo = demarcator.nextOffsetInfo();
        assertEquals(0, offsetInfo.getStartOffset());
        assertEquals(6, offsetInfo.getLength());
        assertEquals(2, offsetInfo.getCrlfLength());
        assertTrue(offsetInfo.isStartsWithMatch());

        offsetInfo = demarcator.nextOffsetInfo();
        assertEquals(6, offsetInfo.getStartOffset());
        assertEquals(3, offsetInfo.getLength());
        assertEquals(0, offsetInfo.getCrlfLength());
        assertTrue(offsetInfo.isStartsWithMatch());
    }

    @Test
    public void validateNiFi_3495() throws IOException {
        String str = "he\ra-to-a\rb-to-b\rc-to-c\r\nd-to-d";
        InputStream is = stringToIs(str);
        TextLineDemarcator demarcator = new TextLineDemarcator(is, 10);
        OffsetInfo info = demarcator.nextOffsetInfo();
        assertEquals(0, info.getStartOffset());
        assertEquals(3, info.getLength());
        assertEquals(1, info.getCrlfLength());

        info = demarcator.nextOffsetInfo();
        assertEquals(3, info.getStartOffset());
        assertEquals(7, info.getLength());
        assertEquals(1, info.getCrlfLength());

        info = demarcator.nextOffsetInfo();
        assertEquals(10, info.getStartOffset());
        assertEquals(7, info.getLength());
        assertEquals(1, info.getCrlfLength());

        info = demarcator.nextOffsetInfo();
        assertEquals(17, info.getStartOffset());
        assertEquals(8, info.getLength());
        assertEquals(2, info.getCrlfLength());

        info = demarcator.nextOffsetInfo();
        assertEquals(25, info.getStartOffset());
        assertEquals(6, info.getLength());
        assertEquals(0, info.getCrlfLength());

    }

    @Test
    public void mixedCRLF() throws Exception {
        InputStream is = stringToIs("oleg\rjoe\njack\r\nstacymike\r\n");
        TextLineDemarcator demarcator = new TextLineDemarcator(is, 4);
        OffsetInfo offsetInfo = demarcator.nextOffsetInfo();
        assertEquals(0, offsetInfo.getStartOffset());
        assertEquals(5, offsetInfo.getLength());
        assertEquals(1, offsetInfo.getCrlfLength());
        assertTrue(offsetInfo.isStartsWithMatch());

        offsetInfo = demarcator.nextOffsetInfo();
        assertEquals(5, offsetInfo.getStartOffset());
        assertEquals(4, offsetInfo.getLength());
        assertEquals(1, offsetInfo.getCrlfLength());
        assertTrue(offsetInfo.isStartsWithMatch());

        offsetInfo = demarcator.nextOffsetInfo();
        assertEquals(9, offsetInfo.getStartOffset());
        assertEquals(6, offsetInfo.getLength());
        assertEquals(2, offsetInfo.getCrlfLength());
        assertTrue(offsetInfo.isStartsWithMatch());

        offsetInfo = demarcator.nextOffsetInfo();
        assertEquals(15, offsetInfo.getStartOffset());
        assertEquals(11, offsetInfo.getLength());
        assertEquals(2, offsetInfo.getCrlfLength());
        assertTrue(offsetInfo.isStartsWithMatch());
    }

    @Test
    public void consecutiveAndMixedCRLF() throws Exception {
        InputStream is = stringToIs("oleg\r\r\njoe\n\n\rjack\n\r\nstacymike\r\n\n\n\r");
        TextLineDemarcator demarcator = new TextLineDemarcator(is, 4);

        OffsetInfo offsetInfo = demarcator.nextOffsetInfo(); // oleg\r
        assertEquals(5, offsetInfo.getLength());
        assertEquals(1, offsetInfo.getCrlfLength());

        offsetInfo = demarcator.nextOffsetInfo();        // \r\n
        assertEquals(2, offsetInfo.getLength());
        assertEquals(2, offsetInfo.getCrlfLength());

        offsetInfo = demarcator.nextOffsetInfo();        // joe\n
        assertEquals(4, offsetInfo.getLength());
        assertEquals(1, offsetInfo.getCrlfLength());

        offsetInfo = demarcator.nextOffsetInfo();        // \n
        assertEquals(1, offsetInfo.getLength());
        assertEquals(1, offsetInfo.getCrlfLength());

        offsetInfo = demarcator.nextOffsetInfo();        // \r
        assertEquals(1, offsetInfo.getLength());
        assertEquals(1, offsetInfo.getCrlfLength());

        offsetInfo = demarcator.nextOffsetInfo();        // jack\n
        assertEquals(5, offsetInfo.getLength());
        assertEquals(1, offsetInfo.getCrlfLength());

        offsetInfo = demarcator.nextOffsetInfo();        // \r\n
        assertEquals(2, offsetInfo.getLength());
        assertEquals(2, offsetInfo.getCrlfLength());

        offsetInfo = demarcator.nextOffsetInfo();        // stacymike\r\n
        assertEquals(11, offsetInfo.getLength());
        assertEquals(2, offsetInfo.getCrlfLength());

        offsetInfo = demarcator.nextOffsetInfo();        // \n
        assertEquals(1, offsetInfo.getLength());
        assertEquals(1, offsetInfo.getCrlfLength());

        offsetInfo = demarcator.nextOffsetInfo();        // \n
        assertEquals(1, offsetInfo.getLength());
        assertEquals(1, offsetInfo.getCrlfLength());

        offsetInfo = demarcator.nextOffsetInfo();        // \r
        assertEquals(1, offsetInfo.getLength());
        assertEquals(1, offsetInfo.getCrlfLength());
    }

    @Test
    public void startWithNoMatchOnWholeStream() throws Exception {
        InputStream is = stringToIs("oleg\rjoe\njack\r\nstacymike\r\n");
        TextLineDemarcator demarcator = new TextLineDemarcator(is, 4);

        OffsetInfo offsetInfo = demarcator.nextOffsetInfo("foojhkj".getBytes());
        assertEquals(0, offsetInfo.getStartOffset());
        assertEquals(5, offsetInfo.getLength());
        assertEquals(1, offsetInfo.getCrlfLength());
        assertFalse(offsetInfo.isStartsWithMatch());

        offsetInfo = demarcator.nextOffsetInfo("foo".getBytes());
        assertEquals(5, offsetInfo.getStartOffset());
        assertEquals(4, offsetInfo.getLength());
        assertEquals(1, offsetInfo.getCrlfLength());
        assertFalse(offsetInfo.isStartsWithMatch());

        offsetInfo = demarcator.nextOffsetInfo("joe".getBytes());
        assertEquals(9, offsetInfo.getStartOffset());
        assertEquals(6, offsetInfo.getLength());
        assertEquals(2, offsetInfo.getCrlfLength());
        assertFalse(offsetInfo.isStartsWithMatch());

        offsetInfo = demarcator.nextOffsetInfo("stasy".getBytes());
        assertEquals(15, offsetInfo.getStartOffset());
        assertEquals(11, offsetInfo.getLength());
        assertEquals(2, offsetInfo.getCrlfLength());
        assertFalse(offsetInfo.isStartsWithMatch());
    }

    @Test
    public void startWithSomeMatches() throws Exception {
        InputStream is = stringToIs("oleg\rjoe\njack\r\nstacymike\r\n");
        TextLineDemarcator demarcator = new TextLineDemarcator(is, 7);

        OffsetInfo offsetInfo = demarcator.nextOffsetInfo("foojhkj".getBytes());
        assertEquals(0, offsetInfo.getStartOffset());
        assertEquals(5, offsetInfo.getLength());
        assertEquals(1, offsetInfo.getCrlfLength());
        assertFalse(offsetInfo.isStartsWithMatch());

        offsetInfo = demarcator.nextOffsetInfo("jo".getBytes());
        assertEquals(5, offsetInfo.getStartOffset());
        assertEquals(4, offsetInfo.getLength());
        assertEquals(1, offsetInfo.getCrlfLength());
        assertTrue(offsetInfo.isStartsWithMatch());

        offsetInfo = demarcator.nextOffsetInfo("joe".getBytes());
        assertEquals(9, offsetInfo.getStartOffset());
        assertEquals(6, offsetInfo.getLength());
        assertEquals(2, offsetInfo.getCrlfLength());
        assertFalse(offsetInfo.isStartsWithMatch());

        offsetInfo = demarcator.nextOffsetInfo("stacy".getBytes());
        assertEquals(15, offsetInfo.getStartOffset());
        assertEquals(11, offsetInfo.getLength());
        assertEquals(2, offsetInfo.getCrlfLength());
        assertTrue(offsetInfo.isStartsWithMatch());
    }

    @Test
    public void testOnBufferSplitNoTrailingDelimiter() throws IOException {
        final byte[] inputData = "Yes\nNo".getBytes(StandardCharsets.UTF_8);
        final ByteArrayInputStream is = new ByteArrayInputStream(inputData);
        final TextLineDemarcator demarcator = new TextLineDemarcator(is, 3);

        final OffsetInfo first = demarcator.nextOffsetInfo();
        final OffsetInfo second = demarcator.nextOffsetInfo();
        final OffsetInfo third = demarcator.nextOffsetInfo();
        assertNotNull(first);
        assertNotNull(second);
        assertNull(third);

        assertEquals(0, first.getStartOffset());
        assertEquals(4, first.getLength());
        assertEquals(1, first.getCrlfLength());

        assertEquals(4, second.getStartOffset());
        assertEquals(2, second.getLength());
        assertEquals(0, second.getCrlfLength());
    }

    @Test
    public void validateStartsWithLongerThanLastToken() throws IOException {
        final byte[] inputData = "This is going to be a spectacular test\nThis is".getBytes(StandardCharsets.UTF_8);
        final byte[] startsWith = "This is going to be".getBytes(StandardCharsets.UTF_8);

        try (final InputStream is = new ByteArrayInputStream(inputData);
                final TextLineDemarcator demarcator = new TextLineDemarcator(is)) {

            final OffsetInfo first = demarcator.nextOffsetInfo(startsWith);
            assertNotNull(first);
            assertEquals(0, first.getStartOffset());
            assertEquals(39, first.getLength());
            assertEquals(1, first.getCrlfLength());
            assertTrue(first.isStartsWithMatch());

            final OffsetInfo second = demarcator.nextOffsetInfo(startsWith);
            assertNotNull(second);
            assertEquals(39, second.getStartOffset());
            assertEquals(7, second.getLength());
            assertEquals(0, second.getCrlfLength());
            assertFalse(second.isStartsWithMatch());
        }
    }

    private InputStream stringToIs(String data) {
        return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
    }
}
