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
package org.apache.nifi.remote.cluster;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stream.StreamSource;

import org.apache.nifi.xml.processing.ProcessingException;
import org.apache.nifi.xml.processing.stream.StandardXMLStreamReaderProvider;
import org.apache.nifi.xml.processing.stream.XMLStreamReaderProvider;

@XmlRootElement
public class ClusterNodeInformation {

    private Collection<NodeInformation> nodeInfo;

    private static final JAXBContext JAXB_CONTEXT;

    static {
        try {
            JAXB_CONTEXT = JAXBContext.newInstance(ClusterNodeInformation.class);
        } catch (JAXBException e) {
            throw new RuntimeException("Unable to create JAXBContext.", e);
        }
    }

    public ClusterNodeInformation() {
        this.nodeInfo = null;
    }

    public void setNodeInformation(final Collection<NodeInformation> nodeInfo) {
        this.nodeInfo = nodeInfo;
    }

    @XmlJavaTypeAdapter(NodeInformationAdapter.class)
    public Collection<NodeInformation> getNodeInformation() {
        return nodeInfo;
    }

    public void marshal(final OutputStream os) throws JAXBException {
        final Marshaller marshaller = JAXB_CONTEXT.createMarshaller();
        marshaller.marshal(this, os);
    }

    public static ClusterNodeInformation unmarshal(final InputStream is) throws JAXBException {
        try {
            final Unmarshaller unmarshaller = JAXB_CONTEXT.createUnmarshaller();
            final XMLStreamReaderProvider provider = new StandardXMLStreamReaderProvider();
            final XMLStreamReader xsr = provider.getStreamReader(new StreamSource(is));
            return (ClusterNodeInformation) unmarshaller.unmarshal(xsr);
        } catch (final ProcessingException e) {
            throw new JAXBException("Error unmarshalling the cluster node information", e);
        }
    }
}
