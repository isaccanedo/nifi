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
package org.apache.nifi.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.xml.bind.annotation.XmlType;

@XmlType(name = "jmxMetricsResults")
public class JmxMetricsResultDTO {
    final private String beanName;
    final private String attributeName;
    final private Object attributeValue;

    public JmxMetricsResultDTO(final String beanName, final String attributeName, final Object attributeValue) {
        this.beanName = beanName;
        this.attributeName = attributeName;
        this.attributeValue = attributeValue;
    }

    @Schema(description = "The bean name of the metrics bean.")
    public String getBeanName() {
        return beanName;
    }

    @Schema(description = "The attribute name of the metrics bean's attribute.")
    public String getAttributeName() {
        return attributeName;
    }

    @Schema(description = "The attribute value of the the metrics bean's attribute")
    public Object getAttributeValue() {
        return attributeValue;
    }
}
