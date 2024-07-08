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
package org.apache.nifi.remote;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.nifi.authorization.AuthorizationResult;
import org.apache.nifi.authorization.AuthorizationResult.Result;
import org.apache.nifi.authorization.Authorizer;
import org.apache.nifi.authorization.RequestAction;
import org.apache.nifi.authorization.resource.Authorizable;
import org.apache.nifi.authorization.resource.DataTransferAuthorizable;
import org.apache.nifi.authorization.user.NiFiUser;
import org.apache.nifi.authorization.user.StandardNiFiUser.Builder;
import org.apache.nifi.authorization.util.IdentityMapping;
import org.apache.nifi.authorization.util.IdentityMappingUtil;
import org.apache.nifi.authorization.util.UserGroupUtil;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.connectable.ConnectableType;
import org.apache.nifi.connectable.Connection;
import org.apache.nifi.controller.AbstractPort;
import org.apache.nifi.controller.ProcessScheduler;
import org.apache.nifi.controller.ScheduledState;
import org.apache.nifi.events.BulletinFactory;
import org.apache.nifi.events.EventReporter;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessSessionFactory;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.FlowFileAccessException;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.remote.codec.FlowFileCodec;
import org.apache.nifi.remote.exception.BadRequestException;
import org.apache.nifi.remote.exception.NotAuthorizedException;
import org.apache.nifi.remote.exception.ProtocolException;
import org.apache.nifi.remote.exception.RequestExpiredException;
import org.apache.nifi.remote.exception.TransmissionDisabledException;
import org.apache.nifi.remote.protocol.CommunicationsSession;
import org.apache.nifi.remote.protocol.ServerProtocol;
import org.apache.nifi.reporting.BulletinRepository;
import org.apache.nifi.reporting.ComponentType;
import org.apache.nifi.reporting.Severity;
import org.apache.nifi.scheduling.SchedulingStrategy;
import org.apache.nifi.util.NiFiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class StandardPublicPort extends AbstractPort implements PublicPort {

    private static final String CATEGORY = "Site to Site";

    private static final Logger logger = LoggerFactory.getLogger(StandardPublicPort.class);

    private final boolean secure;
    private final Authorizer authorizer;
    private final List<IdentityMapping> identityMappings;
    private TransferDirection direction;

    @SuppressWarnings("unused")
    private final BulletinRepository bulletinRepository;
    private final EventReporter eventReporter;
    private final ProcessScheduler scheduler;
    private final Set<Relationship> relationships;

    private final BlockingQueue<FlowFileRequest> requestQueue = new ArrayBlockingQueue<>(1000);

    private final Set<FlowFileRequest> activeRequests = new HashSet<>();
    private final Lock requestLock = new ReentrantLock();
    private boolean shutdown = false;   // guarded by requestLock

    public StandardPublicPort(final String id, final String name,
                              final TransferDirection direction, final ConnectableType type, final Authorizer authorizer,
                              final BulletinRepository bulletinRepository, final ProcessScheduler scheduler, final boolean secure,
                              final String yieldPeriod, final List<IdentityMapping> identityMappings) {

        super(id, name, type, scheduler);

        setSchedulingPeriod(MINIMUM_SCHEDULING_NANOS + " nanos");
        this.authorizer = authorizer;
        this.secure = secure;
        this.identityMappings = identityMappings;
        this.bulletinRepository = bulletinRepository;
        this.scheduler = scheduler;
        this.direction = direction;
        setYieldPeriod(yieldPeriod);
        eventReporter = new EventReporter() {
            private static final long serialVersionUID = 1L;

            @Override
            public void reportEvent(final Severity severity, final String category, final String message) {
                final String groupId = StandardPublicPort.this.getProcessGroup().getIdentifier();
                final String groupName = StandardPublicPort.this.getProcessGroup().getName();
                final String sourceId = StandardPublicPort.this.getIdentifier();
                final String sourceName = StandardPublicPort.this.getName();
                final ComponentType componentType = direction == TransferDirection.RECEIVE ? ComponentType.INPUT_PORT : ComponentType.OUTPUT_PORT;
                bulletinRepository.addBulletin(BulletinFactory.createBulletin(groupId, groupName, sourceId, componentType, sourceName, category, severity.name(), message));
            }
        };

        relationships = direction == TransferDirection.RECEIVE ? Collections.singleton(AbstractPort.PORT_RELATIONSHIP) : Collections.emptySet();
    }

    @Override
    public Collection<Relationship> getRelationships() {
        return relationships;
    }

    @Override
    public boolean isTriggerWhenEmpty() {
        return true;
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSessionFactory sessionFactory) {
        final FlowFileRequest flowFileRequest;
        try {
            flowFileRequest = requestQueue.poll(1, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException ie) {
            return;
        }

        if (flowFileRequest == null) {
            context.yield();
            return;
        }

        flowFileRequest.setServiceBegin();

        requestLock.lock();
        try {
            if (shutdown) {
                final CommunicationsSession commsSession = flowFileRequest.getPeer().getCommunicationsSession();
                if (commsSession != null) {
                    commsSession.interrupt();
                }
            }

            activeRequests.add(flowFileRequest);
        } finally {
            requestLock.unlock();
        }

        final ProcessSession session = sessionFactory.createSession();

        try {
            onTrigger(context, session, flowFileRequest);
            // we leave the session open, because we send it back to the caller of #receiveFlowFile or #transmitFlowFile,
            // so that they can perform appropriate actions to commit or rollback the transaction.
        } catch (final TransmissionDisabledException e) {
            session.rollback();
        } catch (final Exception e) {
            logger.error("{} Failed to process data", this, e);
            if (logger.isDebugEnabled()) {
                logger.error("", e);
            }

            session.rollback();
        } finally {
            requestLock.lock();
            try {
                activeRequests.remove(flowFileRequest);
            } finally {
                requestLock.unlock();
            }
        }
    }

    @Override
    public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
        // nothing to do here -- we will never get called because we override onTrigger(ProcessContext, ProcessSessionFactory)
    }

    private void onTrigger(final ProcessContext context, final ProcessSession session, final FlowFileRequest flowFileRequest) {
        final ServerProtocol protocol = flowFileRequest.getProtocol();
        final BlockingQueue<ProcessingResult> responseQueue = flowFileRequest.getResponseQueue();
        if (flowFileRequest.isExpired()) {
            final String message = String.format("%s Cannot service request from %s because the request has timed out", this, flowFileRequest.getPeer());
            logger.warn(message);
            eventReporter.reportEvent(Severity.WARNING, CATEGORY, message);

            responseQueue.add(new ProcessingResult(new RequestExpiredException()));
            return;
        }

        final Peer peer = flowFileRequest.getPeer();
        final CommunicationsSession commsSession = peer.getCommunicationsSession();
        final String sourceDn = commsSession.getUserDn();
        logger.debug("{} Servicing request for {} (DN={})", this, peer, sourceDn);

        final PortAuthorizationResult authorizationResult = checkUserAuthorization(sourceDn);
        if (!authorizationResult.isAuthorized()) {
            final String message = String.format("%s Cannot service request from %s (DN=%s) because peer is not authorized to communicate with this port: %s",
                    this, flowFileRequest.getPeer(), flowFileRequest.getPeer().getCommunicationsSession().getUserDn(), authorizationResult.getExplanation());
            logger.error(message);
            eventReporter.reportEvent(Severity.ERROR, CATEGORY, message);

            responseQueue.add(new ProcessingResult(new NotAuthorizedException(authorizationResult.getExplanation())));
            return;
        }

        final FlowFileCodec codec = protocol.getPreNegotiatedCodec();
        if (codec == null) {
            responseQueue.add(new ProcessingResult(new BadRequestException("None of the supported FlowFile Codecs supplied is compatible with this instance")));
            return;
        }

        final int transferCount;

        try {
            if (getConnectableType() == ConnectableType.INPUT_PORT) {
                transferCount = receiveFlowFiles(context, session, codec, flowFileRequest);
            } else {
                transferCount = transferFlowFiles(context, session, codec, flowFileRequest);
            }
        } catch (final Exception e) {
            session.rollback();
            responseQueue.add(new ProcessingResult(e));

            return;
        }

        responseQueue.add(new ProcessingResult(transferCount));
    }

    private int transferFlowFiles(final ProcessContext context, final ProcessSession session, final FlowFileCodec codec, final FlowFileRequest request) throws IOException {
        return request.getProtocol().transferFlowFiles(request.getPeer(), context, session, codec);
    }

    private int receiveFlowFiles(final ProcessContext context, final ProcessSession session, final FlowFileCodec codec, final FlowFileRequest receiveRequest) throws IOException {
        return receiveRequest.getProtocol().receiveFlowFiles(receiveRequest.getPeer(), context, session, codec);
    }

    /**
     * Returns {@code true} if the port is not a <em>local</em> input port (remote input ports are
     * handled by {@link StandardRemoteGroupPort}), or if the local input port has at least one
     * available connection.
     *
     * @return true if this port is valid
     */
    @Override
    public boolean isValid() {
        if (getConnectableType() == ConnectableType.INPUT_PORT) {
            Set<Connection> availableConnections = getConnections(Relationship.ANONYMOUS);
            return !availableConnections.isEmpty();
        }
        return true;
    }

    @Override
    public Collection<ValidationResult> getValidationErrors() {
        final Collection<ValidationResult> validationErrors = new ArrayList<>();
        if (getScheduledState() == ScheduledState.STOPPED) {
            if (!isValid()) {
                final ValidationResult error = new ValidationResult.Builder()
                        .explanation(String.format("Output connection for port '%s' is not defined.", getName()))
                        .subject(String.format("Port '%s'", getName()))
                        .valid(false)
                        .build();
                validationErrors.add(error);
            }
        }
        return validationErrors;
    }

    @Override
    public boolean isTransmitting() {
        if (!isRunning()) {
            return false;
        }

        requestLock.lock();
        try {
            return !activeRequests.isEmpty();
        } finally {
            requestLock.unlock();
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();

        requestLock.lock();
        try {
            this.shutdown = true;

            for (final FlowFileRequest request : activeRequests) {
                final CommunicationsSession commsSession = request.getPeer().getCommunicationsSession();
                if (commsSession != null) {
                    commsSession.interrupt();
                }
            }
        } finally {
            requestLock.unlock();
        }
    }

    @Override
    public void onSchedulingStart() {
        super.onSchedulingStart();

        requestLock.lock();
        try {
            shutdown = false;
        } finally {
            requestLock.unlock();
        }
    }

    @Override
    public PortAuthorizationResult checkUserAuthorization(final String dn) {
        if (!secure) {
            return new StandardPortAuthorizationResult(true, "Site-to-Site is not Secure");
        }

        if (dn == null) {
            final String message = String.format("%s authorization failed for user %s because the DN is unknown", this, dn);
            logger.warn(message);
            eventReporter.reportEvent(Severity.WARNING, CATEGORY, message);
            return new StandardPortAuthorizationResult(false, "User DN is not known");
        }

        final String identity = IdentityMappingUtil.mapIdentity(dn, identityMappings);
        final Set<String> groups = UserGroupUtil.getUserGroups(authorizer, identity);
        return checkUserAuthorization(new Builder().identity(identity).groups(groups).build());
    }

    @Override
    public PortAuthorizationResult checkUserAuthorization(NiFiUser user) {
        if (!secure) {
            return new StandardPortAuthorizationResult(true, "Site-to-Site is not Secure");
        }

        if (user == null) {
            final String message = String.format("%s authorization failed because the user is unknown", this);
            logger.warn(message);
            eventReporter.reportEvent(Severity.WARNING, CATEGORY, message);
            return new StandardPortAuthorizationResult(false, "User is not known");
        }

        // perform the authorization
        final Authorizable dataTransferAuthorizable = new DataTransferAuthorizable(this);
        final AuthorizationResult result = dataTransferAuthorizable.checkAuthorization(authorizer, RequestAction.WRITE, user);

        if (!Result.Approved.equals(result.getResult())) {
            final String message = String.format("%s authorization failed for user %s because %s", this, user.getIdentity(), result.getExplanation());
            logger.warn(message);
            eventReporter.reportEvent(Severity.WARNING, CATEGORY, message);
            return new StandardPortAuthorizationResult(false, message);
        }

        return new StandardPortAuthorizationResult(true, "User is Authorized");
    }

    public static class StandardPortAuthorizationResult implements PortAuthorizationResult {

        private final boolean isAuthorized;
        private final String explanation;

        public StandardPortAuthorizationResult(final boolean isAuthorized, final String explanation) {
            this.isAuthorized = isAuthorized;
            this.explanation = explanation;
        }

        @Override
        public boolean isAuthorized() {
            return isAuthorized;
        }

        @Override
        public String getExplanation() {
            return explanation;
        }
    }

    private static class ProcessingResult {

        private final int fileCount;
        private final Exception problem;

        public ProcessingResult(final int fileCount) {
            this.fileCount = fileCount;
            this.problem = null;
        }

        public ProcessingResult(final Exception problem) {
            this.fileCount = 0;
            this.problem = problem;
        }

        public Exception getProblem() {
            return problem;
        }

        public int getFileCount() {
            return fileCount;
        }
    }

    private static class FlowFileRequest {

        private final Peer peer;
        private final ServerProtocol protocol;
        private final BlockingQueue<ProcessingResult> queue;
        private final long creationTime;
        private final AtomicBoolean beingServiced = new AtomicBoolean(false);

        public FlowFileRequest(final Peer peer, final ServerProtocol protocol) {
            this.creationTime = System.currentTimeMillis();
            this.peer = peer;
            this.protocol = protocol;
            this.queue = new ArrayBlockingQueue<>(1);
        }

        public void setServiceBegin() {
            beingServiced.set(true);
            synchronized (this) {
                notifyAll();
            }
        }

        public boolean waitForService(final long duration, final TimeUnit timeUnit) throws InterruptedException {
            final long latestWaitTime = System.nanoTime() + timeUnit.toNanos(duration);

            while (!beingServiced.get()) {
                final long nanosToWait = latestWaitTime - System.nanoTime();
                if (nanosToWait <= 0) {
                    return false;
                }

                if (isExpired()) {
                    return false;
                }

                synchronized (this) {
                    TimeUnit.NANOSECONDS.timedWait(this, nanosToWait);
                }
            }

            return true;
        }

        public BlockingQueue<ProcessingResult> getResponseQueue() {
            return queue;
        }

        public Peer getPeer() {
            return peer;
        }

        public ServerProtocol getProtocol() {
            return protocol;
        }

        public boolean isExpired() {
            // use double the protocol's expiration because the sender may send data for a bit before
            // the timeout starts being counted, and we don't want to timeout before the sender does.
            // is this a good idea...???
            long expiration = protocol.getRequestExpiration() * 2;
            if (expiration <= 0L) {
                return false;
            }

            if (expiration < 500L) {
                expiration = 500L;
            }

            return System.currentTimeMillis() > creationTime + expiration;
        }
    }

    @Override
    public int receiveFlowFiles(final Peer peer, final ServerProtocol serverProtocol)
            throws NotAuthorizedException, BadRequestException, RequestExpiredException {
        if (getConnectableType() != ConnectableType.INPUT_PORT) {
            throw new IllegalStateException("Cannot receive FlowFiles because this port is not an Input Port");
        }

        if (!isRunning()) {
            throw new IllegalStateException("Port not running");
        }

        try {
            final FlowFileRequest request = new FlowFileRequest(peer, serverProtocol);
            if (!this.requestQueue.offer(request)) {
                throw new RequestExpiredException();
            }

            // Get a response from the response queue but don't wait forever if the port is stopped
            ProcessingResult result = null;

            // wait for the request to start getting serviced... and time out if it doesn't happen
            // before the request expires
            try {
                while (!request.waitForService(100, TimeUnit.MILLISECONDS)) {
                    if (request.isExpired()) {
                        // Remove expired request, so that it won't block new request to be offered.
                        this.requestQueue.remove(request);
                        throw new SocketTimeoutException("Read timed out");
                    }
                }
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new ProcessException("Interrupted while waiting for site-to-site request to be serviced", ie);
            }

            // we've started to service the request. Now just wait until it's finished
            result = request.getResponseQueue().take();

            final Exception problem = result.getProblem();
            if (problem == null) {
                return result.getFileCount();
            } else {
                throw problem;
            }
        } catch (final NotAuthorizedException | BadRequestException | RequestExpiredException e) {
            throw e;
        } catch (final ProtocolException e) {
            throw new BadRequestException(e);
        } catch (final IOException | FlowFileAccessException e) {
            // The content length filter might be blocking the transmission
            final String REQUEST_TOO_LONG_MSG = "Request input stream longer than";
            if (e.getMessage() != null && e.getMessage().contains(REQUEST_TOO_LONG_MSG)) {
                logger.error("The content length filter (configured with {}) is blocking the site-to-site connection: {}", NiFiProperties.WEB_MAX_CONTENT_SIZE, e.getMessage());
                // Perhaps BRE causes the sender to back off?
                throw new BadRequestException(e);
            } else {
                throw new ProcessException(e);
            }
        } catch (final InterruptedException e) {
            logger.error("The NiFi DoS filter has interrupted a long running session. If this is undesired, configure a longer web request timeout value in nifi.properties using '{}'",
                    NiFiProperties.WEB_REQUEST_TIMEOUT);
            throw new ProcessException(e);
        } catch (final Exception e) {
            throw new ProcessException(e);
        }
    }

    @Override
    public int transferFlowFiles(final Peer peer, final ServerProtocol serverProtocol)
            throws NotAuthorizedException, BadRequestException, RequestExpiredException {
        if (getConnectableType() != ConnectableType.OUTPUT_PORT) {
            throw new IllegalStateException("Cannot send FlowFiles because this port is not an Output Port");
        }

        if (!isRunning()) {
            throw new IllegalStateException("Port not running");
        }

        try {
            final FlowFileRequest request = new FlowFileRequest(peer, serverProtocol);
            if (!this.requestQueue.offer(request)) {
                throw new RequestExpiredException();
            }

            // Get a response from the response queue but don't wait forever if the port is stopped
            ProcessingResult result;

            // wait for the request to start getting serviced... and time out if it doesn't happen
            // before the request expires
            try {
                while (!request.waitForService(100, TimeUnit.MILLISECONDS)) {
                    if (request.isExpired()) {
                        // Remove expired request, so that it won't block new request to be offered.
                        this.requestQueue.remove(request);
                        throw new SocketTimeoutException("Read timed out");
                    }
                }
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new ProcessException("Interrupted while waiting for Site-to-Site request to be serviced", ie);
            }

            // we've started to service the request. Now just wait until it's finished
            result = request.getResponseQueue().take();

            final Exception problem = result.getProblem();
            if (problem == null) {
                return result.getFileCount();
            } else {
                throw problem;
            }
        } catch (final NotAuthorizedException | BadRequestException | RequestExpiredException e) {
            throw e;
        } catch (final ProtocolException e) {
            throw new BadRequestException(e);
        } catch (final Exception e) {
            throw new ProcessException(e);
        }
    }

    @Override
    public SchedulingStrategy getSchedulingStrategy() {
        return SchedulingStrategy.TIMER_DRIVEN;
    }

    @Override
    public boolean isSideEffectFree() {
        return false;
    }

    @Override
    public String getComponentType() {
        return "PublicPort";
    }

    @Override
    public TransferDirection getDirection() {
        return direction;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
            .append("id", getIdentifier()).append("name", getName()).toString();
    }
}
