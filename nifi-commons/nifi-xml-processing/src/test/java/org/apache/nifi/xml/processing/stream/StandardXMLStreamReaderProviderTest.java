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
package org.apache.nifi.xml.processing.stream;

import org.apache.nifi.xml.processing.ResourceProvider;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class StandardXMLStreamReaderProviderTest {
    @Test
    void testGetStreamReaderStandard() throws IOException, XMLStreamException {
        final StandardXMLStreamReaderProvider provider = new StandardXMLStreamReaderProvider();

        try (final InputStream inputStream = ResourceProvider.getStandardDocument()) {
            final XMLStreamReader reader = provider.getStreamReader(new StreamSource(inputStream));
            processReader(reader);
        }
    }

    @Test
    void testGetStreamReaderStandardDocumentTypeDeclaration() throws IOException {
        final StandardXMLStreamReaderProvider provider = new StandardXMLStreamReaderProvider();

        try (final InputStream inputStream = ResourceProvider.getStandardDocumentDocType()) {
            final XMLStreamReader reader = provider.getStreamReader(new StreamSource(inputStream));
            assertDoesNotThrow(() -> processReader(reader));
        }
    }

    @Test
    void testGetStreamReaderStandardExternalEntityException() throws IOException {
        final StandardXMLStreamReaderProvider provider = new StandardXMLStreamReaderProvider();

        try (final InputStream inputStream = ResourceProvider.getStandardDocumentDocTypeEntity()) {
            final XMLStreamReader reader = provider.getStreamReader(new StreamSource(inputStream));
            assertThrows(XMLStreamException.class, () -> processReader(reader));
        }
    }

    private void processReader(final XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            reader.next();
        }
    }
}
