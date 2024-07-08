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

package org.apache.nifi.controller.repository.metrics;

public class NopPerformanceTracker implements PerformanceTracker {
    @Override
    public void beginContentRead() {

    }

    @Override
    public void endContentRead() {

    }

    @Override
    public long getContentReadNanos() {
        return 0;
    }

    @Override
    public void beginContentWrite() {

    }

    @Override
    public void endContentWrite() {

    }

    @Override
    public long getContentWriteNanos() {
        return 0;
    }

    @Override
    public void beginSessionCommit() {

    }

    @Override
    public void endSessionCommit() {

    }

    @Override
    public long getSessionCommitNanos() {
        return 0;
    }
}
