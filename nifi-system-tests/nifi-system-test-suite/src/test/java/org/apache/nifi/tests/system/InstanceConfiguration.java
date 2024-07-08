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
package org.apache.nifi.tests.system;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class InstanceConfiguration {
    private final File bootstrapConfigFile;
    private final File instanceDirectory;
    private final File flowJsonGz;
    private final File stateDirectory;
    private final boolean autoStart;
    private final Map<String, String> nifiPropertiesOverrides;
    private final boolean unpackPythonExtensions;

    private InstanceConfiguration(Builder builder) {
        this.bootstrapConfigFile = builder.bootstrapConfigFile;
        this.instanceDirectory = builder.instanceDirectory;
        this.flowJsonGz = builder.flowJsonGz;
        this.stateDirectory = builder.stateDirectory;
        this.autoStart = builder.autoStart;
        this.nifiPropertiesOverrides = builder.nifiPropertiesOverrides;
        this.unpackPythonExtensions = builder.unpackPythonExtensions;
    }

    public File getBootstrapConfigFile() {
        return bootstrapConfigFile;
    }

    public File getInstanceDirectory() {
        return instanceDirectory;
    }

    public File getFlowJsonGz() {
        return flowJsonGz;
    }

    public File getStateDirectory() {
        return stateDirectory;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public boolean isUnpackPythonExtensions() {
        return unpackPythonExtensions;
    }

    public Map<String, String> getNifiPropertiesOverrides() {
        return nifiPropertiesOverrides;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        final InstanceConfiguration that = (InstanceConfiguration) other;
        return autoStart == that.autoStart && unpackPythonExtensions == that.unpackPythonExtensions && Objects.equals(bootstrapConfigFile, that.bootstrapConfigFile)
            && Objects.equals(instanceDirectory, that.instanceDirectory) && Objects.equals(flowJsonGz, that.flowJsonGz)
            && Objects.equals(stateDirectory, that.stateDirectory) && Objects.equals(nifiPropertiesOverrides, that.nifiPropertiesOverrides);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bootstrapConfigFile, instanceDirectory, flowJsonGz, stateDirectory, autoStart, nifiPropertiesOverrides, unpackPythonExtensions);
    }

    public static class Builder {
        private File bootstrapConfigFile;
        private File instanceDirectory;
        private File flowJsonGz;
        private File stateDirectory;
        private boolean autoStart = true;
        private boolean unpackPythonExtensions = false;
        private final Map<String, String> nifiPropertiesOverrides = new HashMap<>();

        public Builder overrideNifiProperties(final Map<String, String> overrides) {
            nifiPropertiesOverrides.clear();
            if (overrides != null) {
                nifiPropertiesOverrides.putAll(overrides);
            }

            return this;
        }

        public Builder bootstrapConfig(final File configFile) {
            if (!configFile.exists()) {
                throw new RuntimeException(new FileNotFoundException(configFile.getAbsolutePath()));
            }

            this.bootstrapConfigFile = configFile;
            return this;
        }

        public Builder bootstrapConfig(final String configFilename) {
            return bootstrapConfig(new File(configFilename));
        }

        public Builder instanceDirectory(final File instanceDir) {
            this.instanceDirectory = instanceDir;
            return this;
        }

        public Builder instanceDirectory(final String instanceDirName) {
            return instanceDirectory(new File(instanceDirName));
        }

        public Builder flowJson(final File flowJsonGz) {
            this.flowJsonGz = flowJsonGz;
            return this;
        }

        public Builder flowJson(final String flowJsonFilename) {
            return flowJson(new File(flowJsonFilename));
        }

        public Builder stateDirectory(final File stateDirectory) {
            if (!stateDirectory.exists()) {
                throw new RuntimeException(new FileNotFoundException(stateDirectory.getAbsolutePath()));
            }

            if (!stateDirectory.isDirectory()) {
                throw new RuntimeException("Specified State Directory " + stateDirectory.getAbsolutePath() + " is not a directory");
            }

            this.stateDirectory = stateDirectory;
            return this;
        }

        public Builder stateDirectory(final String stateDirectoryName) {
            return stateDirectory(new File(stateDirectoryName));
        }

        public Builder autoStart(boolean autoStart) {
            this.autoStart = autoStart;
            return this;
        }

        public Builder unpackPythonExtensions(final boolean unpackPythonExtensions) {
            this.unpackPythonExtensions = unpackPythonExtensions;
            return this;
        }

        public InstanceConfiguration build() {
            if (instanceDirectory == null) {
                throw new IllegalStateException("Instance Directory has not been specified");
            }
            if (bootstrapConfigFile == null) {
                throw new IllegalStateException("Bootstrap Config File has not been specified");
            }

            return new InstanceConfiguration(this);
        }
    }
}
