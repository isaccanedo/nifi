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
package org.apache.nifi.kafka.processors.publish.additional;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.IOUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.nifi.kafka.processors.PublishKafka;
import org.apache.nifi.kafka.processors.AbstractPublishKafkaIT;
import org.apache.nifi.kafka.shared.property.PublishStrategy;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class PublishKafkaContentX1IT extends AbstractPublishKafkaIT {
    private static final String TEST_RESOURCE = "org/apache/nifi/kafka/processors/publish/additional/contentX1.json";

    @Test
    public void test1ProduceOneFlowFile() throws InitializationException, IOException {
        final TestRunner runner = TestRunners.newTestRunner(PublishKafka.class);
        runner.setValidateExpressionUsage(false);
        runner.setProperty(PublishKafka.CONNECTION_SERVICE, addKafkaConnectionService(runner));
        addRecordReaderService(runner);
        addRecordWriterService(runner);
        addRecordKeyWriterService(runner);

        runner.setProperty(PublishKafka.TOPIC_NAME, getClass().getName());
        runner.setProperty(PublishKafka.PUBLISH_STRATEGY, PublishStrategy.USE_VALUE.name());
        runner.setProperty(PublishKafka.MESSAGE_KEY_FIELD, "account");
        runner.setProperty(PublishKafka.ATTRIBUTE_HEADER_PATTERN, "attribute.*");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("attributeA", "valueA");
        attributes.put("attributeB", "valueB");
        attributes.put("otherAttribute", "otherValue");
        final byte[] bytesFlowFile = IOUtils.toByteArray(Objects.requireNonNull(
                getClass().getClassLoader().getResource(TEST_RESOURCE)));
        runner.enqueue(bytesFlowFile, attributes);
        runner.run(1);
        runner.assertAllFlowFilesTransferred("success", 1);
    }

    @Test
    public void test2ConsumeOneRecord() throws IOException {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(getKafkaConsumerProperties())) {
            consumer.subscribe(Collections.singletonList(getClass().getName()));
            final ConsumerRecords<String, String> records = consumer.poll(DURATION_POLL);
            assertEquals(1, records.count());
            for (ConsumerRecord<String, String> record : records) {
                // kafka record headers
                final List<Header> headers = Arrays.asList(record.headers().toArray());
                assertEquals(2, headers.size());
                assertTrue(headers.contains(new RecordHeader("attributeA", "valueA".getBytes(StandardCharsets.UTF_8))));
                assertTrue(headers.contains(new RecordHeader("attributeB", "valueB".getBytes(StandardCharsets.UTF_8))));
                // kafka record key
                final ObjectNode kafkaKey = (ObjectNode) objectMapper.readTree(record.key());
                assertNotNull(kafkaKey);
                assertEquals("Acme", kafkaKey.get("name").textValue());
                assertEquals("AC1234", kafkaKey.get("number").textValue());
                // kafka record value
                final ObjectNode kafkaValue = (ObjectNode) objectMapper.readTree(record.value());
                assertNotNull(kafkaValue);
                assertEquals("1234 First Street", kafkaValue.get("address").textValue());
                assertEquals("12345", kafkaValue.get("zip").textValue());
                assertTrue(kafkaValue.get("account") instanceof ObjectNode);
            }
        }
    }
}
