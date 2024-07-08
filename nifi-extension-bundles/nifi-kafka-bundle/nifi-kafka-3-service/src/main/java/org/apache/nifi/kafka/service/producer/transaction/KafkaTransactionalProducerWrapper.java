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
package org.apache.nifi.kafka.service.producer.transaction;

import org.apache.kafka.clients.producer.Producer;

public class KafkaTransactionalProducerWrapper extends KafkaProducerWrapper {

    public KafkaTransactionalProducerWrapper(final Producer<byte[], byte[]> producer) {
        super(producer);
    }

    @Override
    public void init() {
        producer.initTransactions();
        producer.beginTransaction();
    }

    @Override
    public void commit() {
        try {
            producer.commitTransaction();
        } catch (final Exception e) {
            logger.debug("Failure during producer transaction commit", e);
            abort();
        }
    }

    @Override
    public void abort() {
        producer.abortTransaction();
    }
}
