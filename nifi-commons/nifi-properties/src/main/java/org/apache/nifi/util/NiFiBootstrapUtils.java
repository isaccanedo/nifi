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
package org.apache.nifi.util;

import org.apache.nifi.properties.AbstractBootstrapPropertiesLoader;
import org.apache.nifi.properties.BootstrapProperties;

import java.io.IOException;

/**
 * Encapsulates utility methods for dealing with bootstrap.conf or nifi.properties.
 */
public class NiFiBootstrapUtils {
    private static final AbstractBootstrapPropertiesLoader BOOTSTRAP_PROPERTIES_LOADER = new NiFiBootstrapPropertiesLoader();

    /**
     * Loads the default bootstrap.conf file into a BootstrapProperties object.
     * @return The default bootstrap.conf as a BootstrapProperties object
     * @throws IOException If the file is not readable
     */
    public static BootstrapProperties loadBootstrapProperties() throws IOException {
        return loadBootstrapProperties(null);
    }

    /**
     * Loads the bootstrap.conf file into a BootstrapProperties object.
     * @param bootstrapPath the path to the bootstrap file
     * @return The bootstrap.conf as a BootstrapProperties object
     * @throws IOException If the file is not readable
     */
    public static BootstrapProperties loadBootstrapProperties(final String bootstrapPath) throws IOException {
        return BOOTSTRAP_PROPERTIES_LOADER.loadBootstrapProperties(bootstrapPath);
    }

    /**
     * Returns the default file path to {@code $NIFI_HOME/conf/nifi.properties}. If the system
     * property nifi.properties.file.path is not set, it will be set to the relative conf/nifi.properties
     *
     * @return the path to the nifi.properties file
     */
    public static String getDefaultApplicationPropertiesFilePath() {
        return BOOTSTRAP_PROPERTIES_LOADER.getDefaultApplicationPropertiesFilePath();
    }
}
