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
package org.apache.nifi.connectable;

import org.apache.nifi.components.PortFunction;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.controller.AbstractPort;
import org.apache.nifi.controller.ProcessScheduler;
import org.apache.nifi.flow.ExecutionEngine;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.groups.DataValve;
import org.apache.nifi.groups.FlowFileConcurrency;
import org.apache.nifi.groups.FlowFileGate;
import org.apache.nifi.groups.FlowFileOutboundPolicy;
import org.apache.nifi.groups.ProcessGroup;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.scheduling.SchedulingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Provides a mechanism by which <code>FlowFile</code>s can be transferred into and out of a <code>ProcessGroup</code> to and/or from another <code>ProcessGroup</code> within the same instance of
 * NiFi.
 */
public class LocalPort extends AbstractPort {
    private static final Logger logger = LoggerFactory.getLogger(LocalPort.class);

    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    private final int maxIterations;

    public LocalPort(final String id, final String name, final ConnectableType type, final ProcessScheduler scheduler, final int maxConcurrentTasks, final int maxBatchSize,
                     final String boredYieldDuration) {
        super(id, name, type, scheduler);

        setMaxConcurrentTasks(maxConcurrentTasks);

        maxIterations = Math.max(1, (int) Math.ceil(maxBatchSize / 1000.0));
        setYieldPeriod(boredYieldDuration);
    }

    @Override
    public boolean isValid() {
        return hasIncomingConnection() && (!isOutboundConnectionRequired() || hasOutboundConnection());
    }

    private boolean hasOutboundConnection() {
        return !getConnections(Relationship.ANONYMOUS).isEmpty();
    }

    /**
     * <p>
     * An Outbound Connection is required for a Local Port unless all of the following conditions are met:
     * </p>
     *
     * <ul>
     *    <li>Group is using the Stateless Execution Engine</li>
     *    <li>There is no Input Port to the Group</li>
     *    <li>The Port is a failure port</li>
     * </ul>
     *
     * <p>
     *     Under these conditions, it is not necessary to have an outbound connection because anything that gets routed to the
     *     Port will end up being rolled back anyway, and since there is no Input Port, there is no input FlowFile to route to failure.
     * </p>
     *
     * @return true if an Outbound Connection is required in order for the Port to be valid, <code>false</code> otherwise
     */
    private boolean isOutboundConnectionRequired() {
        final ExecutionEngine engine = getProcessGroup().resolveExecutionEngine();
        if (engine == ExecutionEngine.STANDARD) {
            return true;
        }

        final PortFunction portFunction = getPortFunction();
        if (portFunction != PortFunction.FAILURE) {
            return true;
        }

        final boolean groupHasInputPort = !getProcessGroup().getInputPorts().isEmpty();
        if (groupHasInputPort) {
            return true;
        }

        return false;
    }

    @Override
    public Collection<ValidationResult> getValidationErrors() {
        final Collection<ValidationResult> validationErrors = new ArrayList<>();
        // Incoming connections are required but not set
        if (!hasIncomingConnection()) {
            validationErrors.add(new ValidationResult.Builder()
                .explanation("Port has no incoming connections")
                .subject(String.format("Port '%s'", getName()))
                .valid(false)
                .build());
        }

        // Outgoing connections are required but not set
        if (!hasOutboundConnection() && isOutboundConnectionRequired()) {
            validationErrors.add(new ValidationResult.Builder()
                .explanation("Port has no outgoing connections")
                .subject(String.format("Port '%s'", getName()))
                .valid(false)
                .build());
        }

        return validationErrors;
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) {
        readLock.lock();
        try {
            if (getConnectableType() == ConnectableType.OUTPUT_PORT) {
                triggerOutputPort(context, session);
            } else {
                triggerInputPort(context, session);
            }
        } finally {
            readLock.unlock();
        }
    }

    private void triggerOutputPort(final ProcessContext context, final ProcessSession session) {
        final DataValve dataValve = getProcessGroup().getDataValve(this);

        final boolean shouldTransfer = isTransferDataOut();
        if (shouldTransfer) {
            if (!dataValve.tryOpenFlowOutOfGroup(getProcessGroup())) {
                logger.trace("{} will not transfer data out of Process Group because Data Valve prevents data from flowing out of the Process Group", this);
                context.yield();
                return;
            }

            try {
                transferUnboundedConcurrency(context, session);
            } finally {
                dataValve.closeFlowOutOfGroup(getProcessGroup());
            }
        } else {
            context.yield();
        }
    }

    private void triggerInputPort(final ProcessContext context, final ProcessSession session) {
        final FlowFileGate flowFileGate = getProcessGroup().getFlowFileGate();
        final boolean obtainedClaim = flowFileGate.tryClaim(this);
        if (!obtainedClaim) {
            logger.trace("{} failed to obtain claim for FlowFileGate. Will yield and will not transfer any FlowFiles", this);
            context.yield();
            return;
        }

        try {
            logger.trace("{} obtained claim for FlowFileGate", this);

            final FlowFileConcurrency flowFileConcurrency = getProcessGroup().getFlowFileConcurrency();
            switch (flowFileConcurrency) {
                case UNBOUNDED -> transferUnboundedConcurrency(context, session);
                case SINGLE_FLOWFILE_PER_NODE -> transferSingleFlowFile(session);
                case SINGLE_BATCH_PER_NODE -> transferInputBatch(session);
            }
        } finally {
            flowFileGate.releaseClaim(this);
            logger.trace("{} released claim for FlowFileGate", this);
        }
    }

    private boolean isTransferDataOut() {
        final FlowFileConcurrency flowFileConcurrency = getProcessGroup().getFlowFileConcurrency();
        if (flowFileConcurrency == FlowFileConcurrency.UNBOUNDED) {
            logger.trace("{} will transfer data out of Process Group because FlowFile Concurrency is Unbounded", this);
            return true;
        }

        final FlowFileOutboundPolicy outboundPolicy = getProcessGroup().getFlowFileOutboundPolicy();
        if (outboundPolicy == FlowFileOutboundPolicy.STREAM_WHEN_AVAILABLE) {
            logger.trace("{} will transfer data out of Process Group because FlowFile Outbound Policy is Stream When Available", this);
            return true;
        }

        final boolean queuedForProcessing = getProcessGroup().isDataQueuedForProcessing();
        if (queuedForProcessing) {
            logger.trace("{} will not transfer data out of Process Group because FlowFile Outbound Policy is Batch Output and there is data queued for Processing", this);
            return false;
        }

        logger.trace("{} will transfer data out of Process Group because there is no data queued for processing", this);
        return true;
    }

    private void transferInputBatch(final ProcessSession session) {
        final ProcessGroup processGroup = getProcessGroup();

        // Transfer all FlowFiles from input queues to output queues, ignoring backpressure on the output queue.
        // We do this because the Process Group is configured for batch processing, so the downstream processors
        // will not be able to process the data until the entire batch is ingested. As a result, if backpressure is
        // applied, it will never cease to be applied until the entire batch is brought in. Therefore, we must ignore
        // it so that data can be processed.
        while (session.getQueueSize().getObjectCount() > 0) {
            final List<FlowFile> flowFiles = session.get(10000);
            session.transfer(flowFiles, Relationship.ANONYMOUS);
            session.commitAsync();
            logger.debug("{} Successfully transferred {} FlowFiles into {}", this, flowFiles.size(), processGroup);
        }
    }

    private void transferSingleFlowFile(final ProcessSession session) {
        FlowFile flowFile = session.get();
        if (flowFile == null) {
            return;
        }

        session.transfer(flowFile, Relationship.ANONYMOUS);
        getProcessGroup().getBatchCounts().reset();
        logger.debug("{} Transferred Single FlowFile", this);
    }


    protected void transferUnboundedConcurrency(final ProcessContext context, final ProcessSession session) {
        final Map<String, String> attributes = new HashMap<>();
        final Map<String, Integer> counts = getProcessGroup().getBatchCounts().captureCounts();
        counts.forEach((k, v) -> attributes.put("batch.output." + k, String.valueOf(v)));

        final List<Connection> outgoingConnections = new ArrayList<>(getConnections());

        final boolean batchOutput = getProcessGroup().getFlowFileOutboundPolicy() == FlowFileOutboundPolicy.BATCH_OUTPUT && getConnectableType() == ConnectableType.OUTPUT_PORT;
        if (batchOutput) {
            // Lock the outgoing connections so that the destination of the connection is unable to pull any data until all
            // data has finished transferring. Before locking, we must sort by identifier so that if there are two arbitrary connections, we always lock them in the same order.
            // (I.e., anywhere that we lock connections, we must always first sort by ID).
            // Otherwise, we could encounter a deadlock, if another thread were to lock the same two connections in a different order.
            outgoingConnections.sort(Comparator.comparing(Connection::getIdentifier));
            outgoingConnections.forEach(Connection::lock);
        }

        try {
            Set<Relationship> available = context.getAvailableRelationships();
            int iterations = 0;
            while (!available.isEmpty()) {
                final List<FlowFile> flowFiles = session.get(1000);
                if (flowFiles.isEmpty()) {
                    break;
                }

                if (!attributes.isEmpty()) {
                    flowFiles.forEach(ff -> session.putAllAttributes(ff, attributes));
                }

                session.transfer(flowFiles, Relationship.ANONYMOUS);
                session.commitAsync();

                logger.debug("{} Transferred {} FlowFiles", this, flowFiles.size());

                // If there are fewer than 1,000 FlowFiles available to transfer, or if we
                // have hit the configured FlowFile cap, we want to stop. This prevents us from
                // holding the Timer-Driven Thread for an excessive amount of time.
                if (flowFiles.size() < 1000 || ++iterations >= maxIterations) {
                    break;
                }

                available = context.getAvailableRelationships();
            }
        } finally {
            if (batchOutput) {
                // Reverse ordering in order to unlock.
                outgoingConnections.sort(Comparator.comparing(Connection::getIdentifier).reversed());
                outgoingConnections.forEach(Connection::unlock);
            }
        }
    }

    @Override
    public void updateConnection(final Connection connection) throws IllegalStateException {
        writeLock.lock();
        try {
            super.updateConnection(connection);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void addConnection(final Connection connection) throws IllegalArgumentException {
        writeLock.lock();
        try {
            super.addConnection(connection);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removeConnection(final Connection connection) throws IllegalArgumentException, IllegalStateException {
        writeLock.lock();
        try {
            super.removeConnection(connection);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Set<Connection> getConnections() {
        readLock.lock();
        try {
            return super.getConnections();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Set<Connection> getConnections(Relationship relationship) {
        readLock.lock();
        try {
            return super.getConnections(relationship);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public List<Connection> getIncomingConnections() {
        readLock.lock();
        try {
            return super.getIncomingConnections();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean hasIncomingConnection() {
        readLock.lock();
        try {
            return super.hasIncomingConnection();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean isTriggerWhenEmpty() {
        return false;
    }

    @Override
    public SchedulingStrategy getSchedulingStrategy() {
        return SchedulingStrategy.TIMER_DRIVEN;
    }

    @Override
    public boolean isSideEffectFree() {
        return true;
    }

    @Override
    public String getComponentType() {
        return "Local Port";
    }

    @Override
    public String toString() {
        return "LocalPort[id=" + getIdentifier() + ", type=" + getConnectableType() + ", name=" + getName() + ", group=" + getProcessGroup().getName() + "]";
    }
}
