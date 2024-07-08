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

package org.apache.nifi.stateless.flow;

import org.apache.nifi.stateless.config.ParameterOverride;
import org.apache.nifi.stateless.config.StatelessConfigurationException;
import org.apache.nifi.stateless.engine.StatelessEngineConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface DataflowDefinitionParser {
    DataflowDefinition parseFlowDefinition(File configurationFile, StatelessEngineConfiguration engineConfiguration, List<ParameterOverride> parameterOverrides)
        throws StatelessConfigurationException, IOException;

    DataflowDefinition parseFlowDefinition(Map<String, String> configurationProperties, StatelessEngineConfiguration engineConfiguration, List<ParameterOverride> parameterOverrides)
        throws StatelessConfigurationException, IOException;
}
