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
package org.apache.nifi.xml.inference;

import org.apache.nifi.schema.inference.HierarchicalSchemaInference;
import org.apache.nifi.schema.inference.TimeValueInference;
import org.apache.nifi.serialization.record.DataType;
import org.apache.nifi.util.SchemaInferenceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class XmlSchemaInference extends HierarchicalSchemaInference<XmlNode> {
    private static final Logger logger = LoggerFactory.getLogger(XmlSchemaInference.class);
    private final TimeValueInference timeValueInference;

    public XmlSchemaInference(final TimeValueInference timeValueInference) {
        this.timeValueInference = timeValueInference;
    }

    @Override
    protected DataType getDataType(final XmlNode xmlNode) {
        final XmlNodeType nodeType = xmlNode.getNodeType();
        if (nodeType != XmlNodeType.TEXT) {
            logger.debug("When inferring XML Schema, expected to get an XmlTextNode but received a {} node instead; will ignore this node.", nodeType);
            return null;
        }

        final String text = ((XmlTextNode) xmlNode).getText();
        return inferTextualDataType(text);
    }

    public DataType inferTextualDataType(final String text) {
        return SchemaInferenceUtil.getDataType(text, timeValueInference);
    }

    @Override
    protected boolean isObject(final XmlNode value) {
        return value.getNodeType() == XmlNodeType.CONTAINER;
    }

    @Override
    protected boolean isArray(final XmlNode value) {
        return value.getNodeType() == XmlNodeType.ARRAY;
    }

    @Override
    protected boolean isEmptyArray(final XmlNode value) {
        return value.getNodeType() == XmlNodeType.ARRAY && ((XmlArrayNode) value).getElements().isEmpty();
    }

    @Override
    protected void forEachFieldInRecord(final XmlNode rawRecord, final BiConsumer<String, XmlNode> fieldConsumer) {
        final XmlContainerNode container = (XmlContainerNode) rawRecord;
        container.forEach(fieldConsumer);
    }

    @Override
    protected void forEachRawRecordInArray(final XmlNode arrayRecord, final Consumer<XmlNode> rawRecordConsumer) {
        final XmlArrayNode arrayNode = (XmlArrayNode) arrayRecord;
        arrayNode.forEach(rawRecordConsumer);
    }

    @Override
    protected String getRootName(final XmlNode rawRecord) {
        return rawRecord.getName();
    }
}
