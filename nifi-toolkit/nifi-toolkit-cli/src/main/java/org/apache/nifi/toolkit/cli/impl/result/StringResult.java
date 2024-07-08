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
package org.apache.nifi.toolkit.cli.impl.result;

import org.apache.nifi.toolkit.cli.api.WritableResult;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Result for a single string value.
 */
public class StringResult implements WritableResult<String> {

    private final String value;
    private final boolean isInteractive;

    public StringResult(final String value, final boolean isInteractive) {
        this.value = Objects.requireNonNull(value);
        this.isInteractive = isInteractive;
    }

    @Override
    public String getResult() {
        return value;
    }

    @Override
    public void write(final PrintStream output) {
        if (isInteractive) {
            output.println();
        }
        output.println(value);
        if (isInteractive) {
            output.println();
        }
    }
}
