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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;


@SuppressWarnings("resource")
public class StreamDemarcatorTest {
    @Test
    public void validateInitializationFailure() {
        assertThrows(IllegalArgumentException.class, () -> new StreamDemarcator(null, null, -1));

        assertThrows(IllegalArgumentException.class, () -> new StreamDemarcator(mock(InputStream.class), null, -1));

        assertThrows(IllegalArgumentException.class, () -> new StreamDemarcator(mock(InputStream.class), null, 10, -1));

        assertThrows(IllegalArgumentException.class, () -> new StreamDemarcator(mock(InputStream.class), new byte[0], 10, 1));
    }

    @Test
    public void validateLargeBufferSmallMaxSize() throws IOException {
        final byte[] inputData = "A Great Benefit To Us All".getBytes(StandardCharsets.UTF_8);

        try (final InputStream is = new ByteArrayInputStream(inputData);
            final StreamDemarcator demarcator = new StreamDemarcator(is, "B".getBytes(StandardCharsets.UTF_8), 24, 4096)) {

            final byte[] first = demarcator.nextToken();
            assertNotNull(first);
            assertEquals("A Great ", new String(first));

            final byte[] second = demarcator.nextToken();
            assertNotNull(second);
            assertEquals("enefit To Us All", new String(second));

            assertNull(demarcator.nextToken());

        }
    }

    @Test
    public void vaidateOnPartialMatchThenSubsequentPartialMatch() throws IOException {
        final byte[] inputData = "A Great Big Boy".getBytes(StandardCharsets.UTF_8);
        final byte[] delimBytes = "AB".getBytes(StandardCharsets.UTF_8);

        try (final InputStream is = new ByteArrayInputStream(inputData);
                final StreamDemarcator demarcator = new StreamDemarcator(is, delimBytes, 4096)) {

            final byte[] bytes = demarcator.nextToken();
            assertArrayEquals(inputData, bytes);

            assertNull(demarcator.nextToken());
        }
    }

    @Test
    public void validateNoDelimiter() throws IOException {
        String data = "Learn from yesterday, live for today, hope for tomorrow. The important thing is not to stop questioning.";
        ByteArrayInputStream is = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        StreamDemarcator scanner = new StreamDemarcator(is, null, 1000);
        assertTrue(Arrays.equals(data.getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
        // validate that subsequent invocations of nextToken() do not result in exception
        assertNull(scanner.nextToken());
        assertNull(scanner.nextToken());
    }

    @Test
    public void validateNoDelimiterSmallInitialBuffer() throws IOException {
        String data = "Learn from yesterday, live for today, hope for tomorrow. The important thing is not to stop questioning.";
        ByteArrayInputStream is = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        StreamDemarcator scanner = new StreamDemarcator(is, null, 1000, 1);
        assertTrue(Arrays.equals(data.getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
    }

    @Test
    public void validateSingleByteDelimiter() throws IOException {
        String data = "Learn from yesterday, live for today, hope for tomorrow. The important thing is not to stop questioning.";
        ByteArrayInputStream is = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        StreamDemarcator scanner = new StreamDemarcator(is, ",".getBytes(StandardCharsets.UTF_8), 1000);
        assertTrue(Arrays.equals("Learn from yesterday".getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
        assertTrue(Arrays.equals(" live for today".getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
        assertTrue(Arrays.equals(" hope for tomorrow. The important thing is not to stop questioning.".getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
        assertNull(scanner.nextToken());
    }

    @Test
    public void validateDelimiterAtTheBeginning() throws IOException {
        String data = ",Learn from yesterday, live for today, hope for tomorrow. The important thing is not to stop questioning.";
        ByteArrayInputStream is = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        StreamDemarcator scanner = new StreamDemarcator(is, ",".getBytes(StandardCharsets.UTF_8), 1000);
        assertTrue(Arrays.equals("Learn from yesterday".getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
        assertTrue(Arrays.equals(" live for today".getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
        assertTrue(Arrays.equals(" hope for tomorrow. The important thing is not to stop questioning.".getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
        assertNull(scanner.nextToken());
    }

    @Test
    public void validateEmptyDelimiterSegments() throws IOException {
        String data = ",,,,,Learn from yesterday, live for today, hope for tomorrow. The important thing is not to stop questioning.";
        ByteArrayInputStream is = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        StreamDemarcator scanner = new StreamDemarcator(is, ",".getBytes(StandardCharsets.UTF_8), 1000);
        assertTrue(Arrays.equals("Learn from yesterday".getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
        assertTrue(Arrays.equals(" live for today".getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
        assertTrue(Arrays.equals(" hope for tomorrow. The important thing is not to stop questioning.".getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
        assertNull(scanner.nextToken());
    }

    @Test
    public void validateSingleByteDelimiterSmallInitialBuffer() throws IOException {
        String data = "Learn from yesterday, live for today, hope for tomorrow. The important thing is not to stop questioning.";
        ByteArrayInputStream is = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        StreamDemarcator scanner = new StreamDemarcator(is, ",".getBytes(StandardCharsets.UTF_8), 1000, 2);
        assertTrue(Arrays.equals("Learn from yesterday".getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
        assertTrue(Arrays.equals(" live for today".getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
        assertTrue(Arrays.equals(" hope for tomorrow. The important thing is not to stop questioning.".getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
        assertNull(scanner.nextToken());
    }

    @Test
    public void validateWithMultiByteDelimiter() throws IOException {
        String data = "foodaabardaabazzz";
        ByteArrayInputStream is = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        StreamDemarcator scanner = new StreamDemarcator(is, "daa".getBytes(StandardCharsets.UTF_8), 1000);
        assertTrue(Arrays.equals("foo".getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
        assertTrue(Arrays.equals("bar".getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
        assertTrue(Arrays.equals("bazzz".getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
        assertNull(scanner.nextToken());
    }

    @Test
    public void validateWithMultiByteDelimiterAtTheBeginning() throws IOException {
        String data = "daafoodaabardaabazzz";
        ByteArrayInputStream is = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        StreamDemarcator scanner = new StreamDemarcator(is, "daa".getBytes(StandardCharsets.UTF_8), 1000);
        assertTrue(Arrays.equals("foo".getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
        assertTrue(Arrays.equals("bar".getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
        assertTrue(Arrays.equals("bazzz".getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
        assertNull(scanner.nextToken());
    }

    @Test
    public void validateWithMultiByteDelimiterSmallInitialBuffer() throws IOException {
        String data = "foodaabarffdaabazz";
        ByteArrayInputStream is = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        StreamDemarcator scanner = new StreamDemarcator(is, "daa".getBytes(StandardCharsets.UTF_8), 1000, 1);
        assertTrue(Arrays.equals("foo".getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
        assertTrue(Arrays.equals("barff".getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
        assertTrue(Arrays.equals("bazz".getBytes(StandardCharsets.UTF_8), scanner.nextToken()));
        assertNull(scanner.nextToken());
    }

    @Test
    public void validateWithMultiByteCharsNoDelimiter() throws IOException {
        String data = "僠THIS IS MY NEW TEXT.僠IT HAS A NEWLINE.";
        ByteArrayInputStream is = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        StreamDemarcator scanner = new StreamDemarcator(is, null, 1000);
        byte[] next = scanner.nextToken();
        assertNotNull(next);
        assertEquals(data, new String(next, StandardCharsets.UTF_8));
        assertNull(scanner.nextToken());
    }

    @Test
    public void validateWithMultiByteCharsNoDelimiterSmallInitialBuffer() throws IOException {
        String data = "僠THIS IS MY NEW TEXT.僠IT HAS A NEWLINE.";
        ByteArrayInputStream is = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        StreamDemarcator scanner = new StreamDemarcator(is, null, 1000, 2);
        byte[] next = scanner.nextToken();
        assertNotNull(next);
        assertEquals(data, new String(next, StandardCharsets.UTF_8));
        assertNull(scanner.nextToken());
    }

    @Test
    public void validateWithComplexDelimiter() throws IOException {
        String data = "THIS IS MY TEXT<MYDELIMITER>THIS IS MY NEW TEXT<MYDELIMITER>THIS IS MY NEWEST TEXT";
        ByteArrayInputStream is = new ByteArrayInputStream(data.getBytes());
        StreamDemarcator scanner = new StreamDemarcator(is, "<MYDELIMITER>".getBytes(StandardCharsets.UTF_8), 1000);
        assertEquals("THIS IS MY TEXT", new String(scanner.nextToken(), StandardCharsets.UTF_8));
        assertEquals("THIS IS MY NEW TEXT", new String(scanner.nextToken(), StandardCharsets.UTF_8));
        assertEquals("THIS IS MY NEWEST TEXT", new String(scanner.nextToken(), StandardCharsets.UTF_8));
        assertNull(scanner.nextToken());
    }

    @Test
    public void validateMaxBufferSize() throws IOException {
        String data = "THIS IS MY TEXT<MY DELIMITER>THIS IS MY NEW TEXT THEN<MY DELIMITER>THIS IS MY NEWEST TEXT";
        ByteArrayInputStream is = new ByteArrayInputStream(data.getBytes());
        StreamDemarcator scanner = new StreamDemarcator(is, "<MY DELIMITER>".getBytes(StandardCharsets.UTF_8), 20);
        scanner.nextToken();
        assertThrows(IOException.class, () -> scanner.nextToken());
    }

    @Test
    public void validateScannerHandlesNegativeOneByteInputsNoDelimiter() throws IOException {
        ByteArrayInputStream is = new ByteArrayInputStream(new byte[] {0, 0, 0, 0, -1, 0, 0, 0});
        StreamDemarcator scanner = new StreamDemarcator(is, null, 20);
        byte[] b = scanner.nextToken();
        assertArrayEquals(b, new byte[] {0, 0, 0, 0, -1, 0, 0, 0});
    }

    @Test
    public void validateScannerHandlesNegativeOneByteInputs() throws IOException {
        ByteArrayInputStream is = new ByteArrayInputStream(new byte[] {0, 0, 0, 0, -1, 0, 0, 0});
        StreamDemarcator scanner = new StreamDemarcator(is, "water".getBytes(StandardCharsets.UTF_8), 20, 1024);
        byte[] b = scanner.nextToken();
        assertArrayEquals(b, new byte[] {0, 0, 0, 0, -1, 0, 0, 0});
    }

    @Test
    public void verifyScannerHandlesNegativeOneByteDelimiter() throws IOException {
        ByteArrayInputStream is = new ByteArrayInputStream(new byte[] {0, 0, 0, 0, -1, 0, 0, 0});
        StreamDemarcator scanner = new StreamDemarcator(is, new byte[] {-1}, 20, 1024);
        assertArrayEquals(scanner.nextToken(), new byte[] {0, 0, 0, 0});
        assertArrayEquals(scanner.nextToken(), new byte[] {0, 0, 0});
    }

    @Test
    public void testWithoutTrailingDelimiter() throws IOException {
        final byte[] inputData = "Larger Message First\nSmall".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream is = new ByteArrayInputStream(inputData);
        StreamDemarcator scanner = new StreamDemarcator(is, "\n".getBytes(), 1000);

        final byte[] first = scanner.nextToken();
        final byte[] second = scanner.nextToken();
        assertNotNull(first);
        assertNotNull(second);

        assertEquals("Larger Message First", new String(first, StandardCharsets.UTF_8));
        assertEquals("Small", new String(second, StandardCharsets.UTF_8));
    }

    @Test
    public void testOnBufferSplitNoTrailingDelimiter() throws IOException {
        final byte[] inputData = "Yes\nNo".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream is = new ByteArrayInputStream(inputData);
        StreamDemarcator scanner = new StreamDemarcator(is, "\n".getBytes(), 1000, 3);

        final byte[] first = scanner.nextToken();
        final byte[] second = scanner.nextToken();
        assertNotNull(first);
        assertNotNull(second);

        assertArrayEquals(first, new byte[] {'Y', 'e', 's'});
        assertArrayEquals(second, new byte[] {'N', 'o'});
    }

    @Test
    public void testOnBufferCorners() throws IOException {
        byte[] inputData = "YesDEMARCATORNo".getBytes(StandardCharsets.UTF_8);
        final int[] initialLengths = new int[] {1, 2, 12, 13, 14, 15, 16, 25};

        for (final int initialBufferLength : initialLengths) {
            ByteArrayInputStream is = new ByteArrayInputStream(inputData);
            StreamDemarcator scanner = new StreamDemarcator(is, "DEMARCATOR".getBytes(), 1000, initialBufferLength);

            final byte[] first = scanner.nextToken();
            final byte[] second = scanner.nextToken();
            assertNotNull(first);
            assertNotNull(second);

            assertArrayEquals(new byte[] {'Y', 'e', 's'}, first);
            assertArrayEquals(new byte[] {'N', 'o'}, second);
        }

        inputData = "NoDEMARCATORYes".getBytes(StandardCharsets.UTF_8);
        for (final int initialBufferLength : initialLengths) {
            ByteArrayInputStream is = new ByteArrayInputStream(inputData);
            StreamDemarcator scanner = new StreamDemarcator(is, "DEMARCATOR".getBytes(), 1000, initialBufferLength);

            final byte[] first = scanner.nextToken();
            final byte[] second = scanner.nextToken();
            assertNotNull(first);
            assertNotNull(second);

            assertArrayEquals(new byte[] {'N', 'o'}, first);
            assertArrayEquals(new byte[] {'Y', 'e', 's'}, second);
        }
    }


    @Test
    public void testOnBufferSplit() throws IOException {
        final byte[] inputData = "123\n456\n789".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream is = new ByteArrayInputStream(inputData);
        StreamDemarcator scanner = new StreamDemarcator(is, "\n".getBytes(), 1000, 3);

        final byte[] first = scanner.nextToken();
        final byte[] second = scanner.nextToken();
        final byte[] third = scanner.nextToken();
        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(third);

        assertArrayEquals(first, new byte[] {'1', '2', '3'});
        assertArrayEquals(second, new byte[] {'4', '5', '6'});
        assertArrayEquals(third, new byte[] {'7', '8', '9'});
    }

}
