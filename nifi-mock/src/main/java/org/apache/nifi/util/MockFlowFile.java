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
package org.apache.nifi.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.junit.jupiter.api.Assertions;

public class MockFlowFile implements FlowFile {

    private final Map<String, String> attributes = new LinkedHashMap<>();

    private final long id;
    private final long entryDate;
    private final long creationTime;
    private boolean penalized = false;

    private byte[] data = new byte[0];

    private long lastEnqueuedDate = 0;
    private long enqueuedIndex = 0;

    public MockFlowFile(final long id) {
        this.creationTime = System.nanoTime();
        this.id = id;
        entryDate = System.currentTimeMillis();
        lastEnqueuedDate = entryDate;
        attributes.put(CoreAttributes.FILENAME.key(), System.nanoTime() + ".mockFlowFile");
        attributes.put(CoreAttributes.PATH.key(), "target");

        final String uuid = UUID.randomUUID().toString();
        attributes.put(CoreAttributes.UUID.key(), uuid);
    }

    public MockFlowFile(final long id, final FlowFile toCopy) {
        this.creationTime = System.nanoTime();
        this.id = id;
        entryDate = toCopy.getEntryDate();

        final Map<String, String> attributesToCopy = toCopy.getAttributes();
        String filename = attributesToCopy.get(CoreAttributes.FILENAME.key());
        if (filename == null) {
            filename = System.nanoTime() + ".mockFlowFile";
        }
        attributes.put(CoreAttributes.FILENAME.key(), filename);

        String path = attributesToCopy.get(CoreAttributes.PATH.key());
        if (path == null) {
            path = "target";
        }
        attributes.put(CoreAttributes.PATH.key(), path);

        String uuid = attributesToCopy.get(CoreAttributes.UUID.key());
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
        attributes.put(CoreAttributes.UUID.key(), uuid);

        attributes.putAll(toCopy.getAttributes());
        final byte[] dataToCopy = ((MockFlowFile) toCopy).data;
        this.data = new byte[dataToCopy.length];
        System.arraycopy(dataToCopy, 0, this.data, 0, dataToCopy.length);

        this.penalized = toCopy.isPenalized();
    }

    void setPenalized(boolean penalized) {
        this.penalized = penalized;
    }

    @Override
    public long getLineageStartDate() {
        return entryDate;
    }

    @Override
    public int compareTo(final FlowFile o) {
        return getAttribute(CoreAttributes.UUID.key()).compareTo(o.getAttribute(CoreAttributes.UUID.key()));
    }

    @Override
    public String getAttribute(final String attrName) {
        return attributes.get(attrName);
    }

    @Override
    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    @Override
    public long getEntryDate() {
        return entryDate;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public long getSize() {
        return data.length;
    }

    /**
     * Sets the value of the internal content for this mock flowfile. Would not exist in standard {@link FlowFile} implementations, but useful for complex test assertions.
     *
     * @param data the flowfile content as a byte[]
     */
    public void setData(final byte[] data) {
        this.data = data;
    }

    /**
     * Returns the value of the internal content for this mock flowfile. Would not exist in standard {@link FlowFile} implementations, but useful for complex test assertions.
     *
     * @return the internal flowfile content as a byte[]
     */
    public byte[] getData() {
        return this.data;
    }

    /**
     * Returns the value of the internal content for this mock flowfile as a UTF-8 encoded String. Would not exist in standard {@link FlowFile} implementations, but useful for complex test assertions.
     *
     * @return the internal flowfile content as a String
     */
    public String getContent() {
        return new String(getData(), StandardCharsets.UTF_8);
    }

    /**
     * Returns the value of the internal content for this mock flowfile as an {@link InputStream}. Would not exist in standard {@link FlowFile} implementations, but useful for complex test assertions.
     *
     * @return the internal flowfile content as a stream
     */
    public InputStream getContentStream() {
        return new ByteArrayInputStream(getData());
    }

    @Override
    public boolean isPenalized() {
        return penalized;
    }

    public void putAttributes(final Map<String, String> attrs) {
        attributes.putAll(attrs);
    }

    public void removeAttributes(final Set<String> attrNames) {
        for (final String attrName : attrNames) {
            if (CoreAttributes.UUID.key().equals(attrName)) {
                // the core attribute "uuid" of a FlowFile cannot be altered / removed
                continue;
            }

            attributes.remove(attrName);
        }
    }

    @Override
    public String toString() {
        return "FlowFile[" + id + "," + getAttribute(CoreAttributes.FILENAME.key()) + "," + getSize() + "B]";
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(7, 13).append(id).toHashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj instanceof FlowFile) {
            return ((FlowFile) obj).getId() == this.id;
        }
        return false;
    }

    public void assertAttributeExists(final String attributeName) {
        Assertions.assertTrue(attributes.containsKey(attributeName), "Attribute " + attributeName + " does not exist");
    }

    public void assertAttributeNotExists(final String attributeName) {
        Assertions.assertFalse(attributes.containsKey(attributeName), "Attribute " + attributeName + " should not exist on FlowFile, but exists with value "
                + attributes.get(attributeName));
    }

    public void assertAttributeEquals(final String attributeName, final String expectedValue) {
        Assertions.assertEquals(expectedValue, attributes.get(attributeName), "Expected attribute " + attributeName + " to be " +
                expectedValue + " but instead it was " + attributes.get(attributeName));
    }

    public void assertAttributeNotEquals(final String attributeName, final String expectedValue) {
        Assertions.assertNotSame(expectedValue, attributes.get(attributeName));
    }

    /**
     * Asserts that the content of this FlowFile is the same as the content of
     * the given file
     *
     * @param file to compare content against
     * @throws IOException if fails doing IO during comparison
     */
    public void assertContentEquals(final File file) throws IOException {
        assertContentEquals(file.toPath());
    }

    /**
     * Asserts that the content of this FlowFile is the same as the content of
     * the given path
     *
     * @param path where to find content to compare to
     * @throws IOException if io error occurs while comparing content
     */
    public void assertContentEquals(final Path path) throws IOException {
        try (final InputStream in = Files.newInputStream(path, StandardOpenOption.READ)) {
            assertContentEquals(in);
        }
    }

    /**
     * Asserts that the content of this FlowFile is the same as the content of
     * the given byte array
     *
     * @param data the data to compare
     * @throws IOException if any ioe occurs while reading flowfile
     */
    public void assertContentEquals(final byte[] data) throws IOException {
        try (final InputStream in = new ByteArrayInputStream(data)) {
            assertContentEquals(in);
        }
    }

    public void assertContentEquals(final String data) {
        assertContentEquals(data, false);
    }

    public void assertContentEquals(final String data, final boolean ignoreLineEndings) {
        assertContentEquals(data, StandardCharsets.UTF_8, ignoreLineEndings);
    }

    public void assertContentEquals(final String data, final String charset) {
        assertContentEquals(data, Charset.forName(charset), false);
    }


    public void assertContentEquals(final String data, final Charset charset) {
        assertContentEquals(data, charset, false);
    }

    public void assertContentEquals(final String data, final Charset charset, final boolean ignoreLineEndings) {
        final String value = new String(this.data, charset);
        if (ignoreLineEndings) {
            Assertions.assertEquals(data.replace("\r\n", "\n"), value.replace("\r\n", "\n"));
        } else {
            Assertions.assertEquals(data, value);
        }
    }

    /**
     * Asserts that the content of this FlowFile is the same as the content of
     * the given InputStream. This method closes the InputStream when it is
     * finished.
     *
     * @param in the stream to source comparison data from
     * @throws IOException if any issues reading from given source
     */
    public void assertContentEquals(final InputStream in) throws IOException {
        int bytesRead = 0;
        try (final BufferedInputStream buffered = new BufferedInputStream(in)) {
            for (final byte datum : data) {
                final int fromStream = buffered.read();
                if (fromStream < 0) {
                    Assertions.fail("FlowFile content is " + data.length + " bytes but provided input is only " + bytesRead + " bytes");
                }

                if ((fromStream & 0xFF) != (datum & 0xFF)) {
                    Assertions.fail("FlowFile content differs from input at byte " + bytesRead + " with input having value "
                                    + (fromStream & 0xFF) + " and FlowFile having value " + (datum & 0xFF));
                }

                bytesRead++;
            }

            final int nextByte = buffered.read();
            if (nextByte >= 0) {
                Assertions.fail("Contents of input and FlowFile were the same through byte " + data.length + "; however, FlowFile's content ended at this point, and input has more data");
            }
        }
    }

    /**
     * @return a copy of the the contents of the FlowFile as a byte array
     */
    public byte[] toByteArray() {
        return Arrays.copyOf(this.data, this.data.length);
    }

    @Override
    public Long getLastQueueDate() {
        return lastEnqueuedDate;
    }

    public void setLastEnqueuedDate(long lastEnqueuedDate) {
        this.lastEnqueuedDate = lastEnqueuedDate;
    }

    @Override
    public long getLineageStartIndex() {
        return 0;
    }

    @Override
    public long getQueueDateIndex() {
        return enqueuedIndex;
    }

    public void setEnqueuedIndex(long enqueuedIndex) {
        this.enqueuedIndex = enqueuedIndex;
    }

    public boolean isAttributeEqual(final String attributeName, final String expectedValue) {
        // unknown attribute name, so cannot be equal.
        if (!attributes.containsKey(attributeName)) {
            return false;
        }

        String value = attributes.get(attributeName);
        return Objects.equals(expectedValue, value);
    }

    public boolean isContentEqual(String expected) {
        return isContentEqual(expected, StandardCharsets.UTF_8);
    }

    public boolean isContentEqual(String expected, final Charset charset) {
        final String value = new String(this.data, charset);
        return Objects.equals(expected, value);
    }
}
