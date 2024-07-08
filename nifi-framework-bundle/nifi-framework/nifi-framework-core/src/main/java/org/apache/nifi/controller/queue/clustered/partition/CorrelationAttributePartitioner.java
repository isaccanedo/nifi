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

package org.apache.nifi.controller.queue.clustered.partition;

import org.apache.nifi.controller.repository.FlowFileRecord;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CorrelationAttributePartitioner implements FlowFilePartitioner {
    private static final Logger logger = LoggerFactory.getLogger(CorrelationAttributePartitioner.class);

    private final String partitioningAttribute;

    public CorrelationAttributePartitioner(final String partitioningAttribute) {
        this.partitioningAttribute = partitioningAttribute;
    }

    @Override
    public QueuePartition getPartition(final FlowFileRecord flowFile, final QueuePartition[] partitions,  final QueuePartition localPartition) {
        final int hash = hash(flowFile);

        final int index = findIndex(hash, partitions.length);

        if (logger.isDebugEnabled()) {
            final List<String> partitionDescriptions = new ArrayList<>(partitions.length);
            for (final QueuePartition partition : partitions) {
                partitionDescriptions.add(partition.getSwapPartitionName());
            }

            logger.debug("Assigning Partition {} to {} based on {}", index, flowFile.getAttribute(CoreAttributes.UUID.key()), partitionDescriptions);
        }

        return partitions[index];
    }

    protected int hash(final FlowFileRecord flowFile) {
        final String partitionAttributeValue = flowFile.getAttribute(partitioningAttribute);
        return (partitionAttributeValue == null) ? 0 : partitionAttributeValue.hashCode();
    }

    @Override
    public boolean isRebalanceOnClusterResize() {
        return true;
    }

    @Override
    public boolean isRebalanceOnFailure() {
        return false;
    }

    private int findIndex(final long hash, final int partitions) {
        final Random random = new Random(hash);
        return random.nextInt(partitions);
    }
}
