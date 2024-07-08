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
package org.apache.nifi.controller.leader.election;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Leader Election Manager abstracts cluster election registration and status
 */
public interface LeaderElectionManager {
    /**
     * Starts managing leader elections for all registered roles
     */
    void start();

    /**
     * Adds a new role for which a leader is required, without participating in the leader election. I.e., this node
     * will not be elected leader but will passively observe changes to the leadership. This allows calls to {@link #isLeader(String)}
     * and {@link #getLeader(String)} to know which node is currently elected the leader.
     *
     * @param roleName the name of the role
     * @param listener a listener that will be called when the node gains or relinquishes
     *            the role of leader
     */
    void register(String roleName, LeaderElectionStateChangeListener listener);

    /**
     * Adds a new role for which a leader is required, providing the given value for this node as the Participant ID. If the Participant ID
     * is <code>null</code>, this node will never be elected leader but will passively observe changes to the leadership.
     *
     * @param roleName the name of the role
     * @param listener a listener that will be called when the node gains or relinquishes
     *            the role of leader
     * @param participantId the ID to register as this node's Participant ID. All nodes will see this as the identifier when
     *            asking to see who the leader is via the {@link #getLeader(String)} method
     */
    void register(String roleName, LeaderElectionStateChangeListener listener, String participantId);

    /**
     * Indicates whether or not this node is an active participant in the election for the given role
     * @param roleName the name of the role
     * @return <code>true</code> if this node is an active participant in the election (is allowed to be elected) or <code>false</code> otherwise
     */
    boolean isActiveParticipant(String roleName);

    /**
     * Returns the Participant ID of the node that is elected the leader, if one was provided when the node registered
     * for the role via {@link #register(String, LeaderElectionStateChangeListener, String)}. If there is currently no leader
     * known or if the role was registered without providing a Participant ID, this will return <code>null</code>.
     *
     * @param roleName the name of the role
     * @return the Participant ID of the node that is elected leader, or <code>empty</code> if either no leader is known or the leader
     *         did not register with a Participant ID.
     */
    Optional<String> getLeader(String roleName);

    /**
     * Removes the role with the given name from this manager. If this
     * node is the elected leader for the given role, this node will relinquish
     * the leadership role
     *
     * @param roleName the name of the role to unregister
     */
    void unregister(String roleName);

    /**
     * Returns a boolean value indicating whether or not this node
     * is the elected leader for the given role
     *
     * @param roleName the name of the role
     * @return <code>true</code> if the node is the elected leader, <code>false</code> otherwise.
     */
    boolean isLeader(String roleName);

    /**
     * Stops managing leader elections and relinquishes the role as leader
     * for all registered roles. If the LeaderElectionManager is later started
     * again, all previously registered roles will still be registered.
     */
    void stop();

    /**
     * Returns a Map of Role Name to the number of times that the leader has been detected as changing in the given time period. Note that
     * the amount of time that these counts is stored and the precision is implementation specific.
     *
     * @param  duration the duration
     * @param  timeUnit the time unit
     * @return a Mapping of role to the number of times that the leader for that role has changed
     */
    Map<String, Integer> getLeadershipChangeCount(long duration, TimeUnit timeUnit);

    /**
     * Returns the average amount of time it has taken to poll the leader election service in the past 5 minutes.
     * @param timeUnit the desired time unit
     * @return the average amount of time it has taken to poll the leader election service, or <code>-1</code> if this is not supported
     */
    long getAveragePollTime(TimeUnit timeUnit);

    /**
     * Returns the minimum amount of time any poll of the leader election service has taken in the past 5 minutes.
     * @param timeUnit the desired time unit
     * @return the minimum amount of time any poll of the leader election service has taken, or <code>-1</code> if this is not supported
     */
    long getMinPollTime(TimeUnit timeUnit);

    /**
     * Returns the maximum amount of time any poll of the leader election service has taken in the past 5 minutes.
     * @param timeUnit the desired time unit
     * @return the maximum amount of time any poll of the leader election service has taken, or <code>-1</code> if this is not supported
     */
    long getMaxPollTime(TimeUnit timeUnit);

    /**
     * Returns the number of times that the leader election service has been polled in the past 5 minutes
     * @return the number of times that the leader election service has been polled in the past 5 minutes, or <code>-1</code> if this is not supported
     */
    long getPollCount();
}
