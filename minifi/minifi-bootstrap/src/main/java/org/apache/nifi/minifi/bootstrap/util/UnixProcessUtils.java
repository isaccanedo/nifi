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

package org.apache.nifi.minifi.bootstrap.util;

import static org.apache.nifi.minifi.bootstrap.RunMiNiFi.DEFAULT_LOGGER;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Utility class for providing information about the running MiNiFi process.
 * The methods which are using the PID are working only on unix systems, and should be used only as a fallback in case the PING command fails.
 * */
public class UnixProcessUtils implements ProcessUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnixProcessUtils.class);

    private final int processKillCheckRetries;

    public UnixProcessUtils(int processKillCheckRetries) {
        this.processKillCheckRetries = processKillCheckRetries;
    }

    @Override
    public boolean isProcessRunning(Long pid) {
        if (pid == null) {
            LOGGER.error("Unable to get process status due to missing process id");
            return false;
        }
        try {
            // We use the "ps" command to check if the process is still running.
            ProcessBuilder builder = new ProcessBuilder();
            String pidString = String.valueOf(pid);

            builder.command("ps", "-p", pidString);
            Process proc = builder.start();

            // Look for the pid in the output of the 'ps' command.
            boolean running = false;
            String line;
            try (InputStream in = proc.getInputStream();
                 Reader streamReader = new InputStreamReader(in);
                 BufferedReader reader = new BufferedReader(streamReader)) {

                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith(pidString)) {
                        running = true;
                    }
                }
            }

            // If output of the ps command had our PID, the process is running.
            LOGGER.debug("Process with PID {} is {}running", pid, running ? "" : "not ");

            return running;
        } catch (IOException ioe) {
            LOGGER.error("Failed to determine if Process {} is running; assuming that it is not", pid);
            return false;
        }
    }

    @Override
    public void shutdownProcess(Long pid, String s, int gracefulShutdownSeconds) {
        long startWait = System.nanoTime();
        while (isProcessRunning(pid)) {
            LOGGER.info("Waiting for Apache MiNiFi to finish shutting down...");
            long waitNanos = System.nanoTime() - startWait;
            long waitSeconds = TimeUnit.NANOSECONDS.toSeconds(waitNanos);
            if (waitSeconds >= gracefulShutdownSeconds || gracefulShutdownSeconds == 0) {
                if (isProcessRunning(pid)) {
                    LOGGER.warn(s, gracefulShutdownSeconds);
                    try {
                        killProcessTree(pid);
                    } catch (IOException ioe) {
                        LOGGER.error("Failed to kill Process with PID {}", pid);
                    }
                }
                break;
            } else {
                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException ie) {
                    DEFAULT_LOGGER.warn("Thread interrupted while shutting down MiNiFi");
                }
            }
        }
    }

    @Override
    public void killProcessTree(Long pid) throws IOException {
        LOGGER.debug("Killing Process Tree for PID {}", pid);

        List<Long> children = getChildProcesses(pid);
        LOGGER.debug("Children of PID {}: {}", pid, children);

        for (Long childPid : children) {
            killProcessTree(childPid);
        }

        Runtime.getRuntime().exec(new String[] {"kill", "-9", String.valueOf(pid)});

        int retries = processKillCheckRetries;
        while (isProcessRunning(pid)) {
            if (retries == 0) {
                throw new IOException("Failed to stop process. Process is still running after killing attempt with pid=" + pid);
            }
            LOGGER.warn("Process is still running after killing attempt with pid={}", pid);
            retries--;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                DEFAULT_LOGGER.warn("Thread interrupted while waiting for killing process with pid={}", pid);
            }
        }
    }

    private List<Long> getChildProcesses(Long ppid) throws IOException {
        Process proc = Runtime.getRuntime().exec(new String[] {"ps", "-o", "pid", "--no-headers", "--ppid", String.valueOf(ppid)});
        List<Long> childPids = new ArrayList<>();
        try (InputStream in = proc.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {

            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    Long childPid = Long.valueOf(line.trim());
                    childPids.add(childPid);
                } catch (NumberFormatException e) {
                    LOGGER.trace("Failed to parse PID", e);
                }
            }
        }

        return childPids;
    }
}
