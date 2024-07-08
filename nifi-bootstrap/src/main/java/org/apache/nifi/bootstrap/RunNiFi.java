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
package org.apache.nifi.bootstrap;

import org.apache.nifi.bootstrap.process.RuntimeValidatorExecutor;
import org.apache.nifi.bootstrap.property.ApplicationPropertyHandler;
import org.apache.nifi.bootstrap.property.SecurityApplicationPropertyHandler;
import org.apache.nifi.bootstrap.util.DumpFileValidator;
import org.apache.nifi.util.file.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * <p>
 * The class which bootstraps Apache NiFi. This class looks for the
 * bootstrap.conf file by looking in the following places (in order):</p>
 * <ol>
 * <li>Java System Property named
 * {@code org.apache.nifi.bootstrap.config.file}</li>
 * <li>${NIFI_HOME}/./conf/bootstrap.conf, where ${NIFI_HOME} references an
 * environment variable {@code NIFI_HOME}</li>
 * <li>./conf/bootstrap.conf, where {@code ./} represents the working
 * directory.</li>
 * </ol>
 * <p>
 * If the {@code bootstrap.conf} file cannot be found, throws a {@code FileNotFoundException}.
 */
public class RunNiFi {

    public static final String DEFAULT_CONFIG_FILE = "./conf/bootstrap.conf";
    public static final String DEFAULT_JAVA_CMD = "java";
    public static final String DEFAULT_PID_DIR = "bin";
    public static final String DEFAULT_LOG_DIR = "./logs";
    public static final String DEFAULT_STATUS_HISTORY_DAYS = "1";

    public static final String GRACEFUL_SHUTDOWN_PROP = "graceful.shutdown.seconds";
    public static final String DEFAULT_GRACEFUL_SHUTDOWN_VALUE = "20";

    public static final String NIFI_PID_DIR_PROP = "org.apache.nifi.bootstrap.config.pid.dir";

    public static final String NIFI_PID_FILE_NAME = "nifi.pid";
    public static final String NIFI_STATUS_FILE_NAME = "nifi.status";
    public static final String NIFI_LOCK_FILE_NAME = "nifi.lock";

    public static final String NIFI_BOOTSTRAP_LISTEN_PORT_PROP = "nifi.bootstrap.listen.port";

    public static final String PID_KEY = "pid";

    public static final int STARTUP_WAIT_SECONDS = 60;
    public static final long GRACEFUL_SHUTDOWN_RETRY_MILLIS = 2000L;

    public static final String SHUTDOWN_CMD = "SHUTDOWN";
    public static final String DECOMMISSION_CMD = "DECOMMISSION";
    public static final String PING_CMD = "PING";
    public static final String DUMP_CMD = "DUMP";
    public static final String DIAGNOSTICS_CMD = "DIAGNOSTICS";
    public static final String CLUSTER_STATUS_CMD = "CLUSTER_STATUS";
    public static final String IS_LOADED_CMD = "IS_LOADED";
    public static final String STATUS_HISTORY_CMD = "STATUS_HISTORY";

    private static final int UNINITIALIZED_CC_PORT = -1;

    private static final int INVALID_CMD_ARGUMENT = -1;

    private volatile boolean autoRestartNiFi = true;
    private volatile int ccPort = UNINITIALIZED_CC_PORT;
    private volatile long nifiPid = -1L;
    private volatile String secretKey;
    private volatile ShutdownHook shutdownHook;
    private volatile boolean nifiStarted;

    private final Lock startedLock = new ReentrantLock();
    private final Lock lock = new ReentrantLock();
    private final Condition startupCondition = lock.newCondition();

    private final File bootstrapConfigFile;

    // used for logging initial info; these will be logged to console by default when the app is started
    private final Logger cmdLogger = LoggerFactory.getLogger("org.apache.nifi.bootstrap.Command");
    // used for logging all info. These by default will be written to the log file
    private final Logger defaultLogger = LoggerFactory.getLogger(RunNiFi.class);

    private final ExecutorService loggingExecutor;
    private final RuntimeValidatorExecutor runtimeValidatorExecutor;
    private volatile Set<Future<?>> loggingFutures = new HashSet<>(2);

    public RunNiFi(final File bootstrapConfigFile) throws IOException {
        this.bootstrapConfigFile = bootstrapConfigFile;

        loggingExecutor = Executors.newFixedThreadPool(2, runnable -> {
            final Thread t = Executors.defaultThreadFactory().newThread(runnable);
            t.setDaemon(true);
            t.setName("NiFi logging handler");
            return t;
        });

        runtimeValidatorExecutor = new RuntimeValidatorExecutor();
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println();
        System.out.println("java org.apache.nifi.bootstrap.RunNiFi <command> [options]");
        System.out.println();
        System.out.println("Valid commands include:");
        System.out.println();
        System.out.println("Start : Start a new instance of Apache NiFi");
        System.out.println("Stop : Stop a running instance of Apache NiFi");
        System.out.println("Restart : Stop Apache NiFi, if it is running, and then start a new instance");
        System.out.println("Decommission : Disconnects Apache NiFi from its cluster, offloads its data to other nodes in the cluster, removes itself from the cluster, and shuts down the instance");
        System.out.println("Status : Determine if there is a running instance of Apache NiFi");
        System.out.println("Dump : Write a Thread Dump to the file specified by [options], or to the log if no file is given");
        System.out.println("Diagnostics : Write diagnostic information to the file specified by [options], or to the log if no file is given. The --verbose flag may be provided as an option before " +
                "the filename, which may result in additional diagnostic information being written.");
        System.out.println("Status-history : Save the status history to the file specified by [options]. The expected command parameters are: " +
                "status-history <number of days> <dumpFile>. The <number of days> parameter is optional and defaults to 1 day.");
        System.out.println("Run : Start a new instance of Apache NiFi and monitor the Process, restarting if the instance dies");
        System.out.println();
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 3) {
            printUsage();
            return;
        }

        File dumpFile = null;
        boolean verbose = false;
        String statusHistoryDays = null;

        final String cmd = args[0];
        if (cmd.equalsIgnoreCase("dump")) {
            if (args.length > 1) {
                dumpFile = new File(args[1]);
            }
        } else if (cmd.equalsIgnoreCase("diagnostics")) {
            if (args.length > 2) {
                verbose = args[1].equalsIgnoreCase("--verbose");
                dumpFile = new File(args[2]);
            } else if (args.length > 1) {
                if (args[1].equalsIgnoreCase("--verbose")) {
                    verbose = true;
                } else {
                    dumpFile = new File(args[1]);
                }
            }
        } else if (cmd.equalsIgnoreCase("cluster-status")) {
            if (args.length > 1) {
                dumpFile = new File(args[1]);
            }
        } else if (cmd.equalsIgnoreCase("status-history")) {
            if (args.length < 2) {
                System.err.printf("Wrong number of arguments: %d instead of 1 or 2, the command parameters are: " +
                        "status-history <number of days> <dumpFile>%n", 0);
                System.exit(INVALID_CMD_ARGUMENT);
            }
            if (args.length == 3) {
                statusHistoryDays = args[1];
                try {
                    final int numberOfDays = Integer.parseInt(statusHistoryDays);
                    if (numberOfDays < 1) {
                        System.err.println("The <number of days> parameter must be positive and greater than zero. The command parameters are:" +
                                " status-history <number of days> <dumpFile>");
                        System.exit(INVALID_CMD_ARGUMENT);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("The <number of days> parameter value is not a number. The command parameters are: status-history <number of days> <dumpFile>");
                    System.exit(INVALID_CMD_ARGUMENT);
                }
                try {
                    Paths.get(args[2]);
                } catch (InvalidPathException e) {
                    System.err.println("Invalid filename. The command parameters are: status-history <number of days> <dumpFile>");
                    System.exit(INVALID_CMD_ARGUMENT);
                }
                dumpFile = new File(args[2]);
            } else {
                final boolean isValid = DumpFileValidator.validate(args[1]);
                if (isValid) {
                    statusHistoryDays = DEFAULT_STATUS_HISTORY_DAYS;
                    dumpFile = new File(args[1]);
                } else {
                    System.exit(INVALID_CMD_ARGUMENT);
                }
            }
        }

        switch (cmd.toLowerCase()) {
            case "start":
            case "run":
            case "stop":
            case "decommission":
            case "status":
            case "is_loaded":
            case "dump":
            case "diagnostics":
            case "status-history":
            case "restart":
            case "env":
            case "cluster-status":
                break;
            default:
                printUsage();
                return;
        }

        final File configFile = getDefaultBootstrapConfFile();
        final RunNiFi runNiFi = new RunNiFi(configFile);

        Integer exitStatus = null;
        switch (cmd.toLowerCase()) {
            case "start":
                runNiFi.start(true);
                break;
            case "run":
                runNiFi.start(true);
                break;
            case "stop":
                runNiFi.stop();
                break;
            case "decommission":
                final boolean shutdown = args.length < 2 || !"--shutdown=false".equals(args[1]);
                exitStatus = runNiFi.decommission(shutdown);
                break;
            case "status":
                exitStatus = runNiFi.status();
                break;
            case "is_loaded":
                try {
                    System.out.println(runNiFi.isNiFiFullyLoaded());
                } catch (NiFiNotRunningException e) {
                    System.out.println("not_running");
                }
                break;
            case "restart":
                runNiFi.stop();
                runNiFi.start(true);
                break;
            case "dump":
                runNiFi.dump(dumpFile);
                break;
            case "diagnostics":
                runNiFi.diagnostics(dumpFile, verbose);
                break;
            case "cluster-status":
                runNiFi.clusterStatus(dumpFile);
                break;
            case "status-history":
                runNiFi.statusHistory(dumpFile, statusHistoryDays);
                break;
            case "env":
                runNiFi.env();
                break;
        }
        if (exitStatus != null) {
            System.exit(exitStatus);
        }
    }

    private static File getDefaultBootstrapConfFile() {
        String configFilename = System.getProperty("org.apache.nifi.bootstrap.config.file");

        if (configFilename == null) {
            final String nifiHome = System.getenv("NIFI_HOME");
            if (nifiHome != null) {
                final File nifiHomeFile = new File(nifiHome.trim());
                final File configFile = new File(nifiHomeFile, DEFAULT_CONFIG_FILE);
                configFilename = configFile.getAbsolutePath();
            }
        }

        if (configFilename == null) {
            configFilename = DEFAULT_CONFIG_FILE;
        }

        return new File(configFilename);
    }

    protected File getBootstrapFile(final Logger logger, String directory, String defaultDirectory, String fileName) throws IOException {

        final File confDir = bootstrapConfigFile.getParentFile();
        final File nifiHome = confDir.getParentFile();

        String confFileDir = System.getProperty(directory);

        final File fileDir;

        if (confFileDir != null) {
            fileDir = new File(confFileDir.trim());
        } else {
            fileDir = new File(nifiHome, defaultDirectory);
        }

        FileUtils.ensureDirectoryExistAndCanAccess(fileDir);
        final File statusFile = new File(fileDir, fileName);
        logger.debug("Status File: {}", statusFile);
        return statusFile;
    }

    protected File getPidFile(final Logger logger) throws IOException {
        return getBootstrapFile(logger, NIFI_PID_DIR_PROP, DEFAULT_PID_DIR, NIFI_PID_FILE_NAME);
    }

    protected File getStatusFile(final Logger logger) throws IOException {
        return getBootstrapFile(logger, NIFI_PID_DIR_PROP, DEFAULT_PID_DIR, NIFI_STATUS_FILE_NAME);
    }

    protected File getLockFile(final Logger logger) throws IOException {
        return getBootstrapFile(logger, NIFI_PID_DIR_PROP, DEFAULT_PID_DIR, NIFI_LOCK_FILE_NAME);
    }

    protected File getStatusFile() throws IOException {
        return getStatusFile(defaultLogger);
    }

    private Properties loadProperties(final Logger logger) throws IOException {
        final Properties props = new Properties();
        final File statusFile = getStatusFile(logger);
        if (statusFile == null || !statusFile.exists()) {
            logger.debug("No status file to load properties from");
            return props;
        }

        try (final FileInputStream fis = new FileInputStream(getStatusFile(logger))) {
            props.load(fis);
        }

        final Map<Object, Object> modified = new HashMap<>(props);
        modified.remove("secret.key");
        logger.debug("Properties: {}", modified);

        return props;
    }

    private synchronized void savePidProperties(final Properties pidProperties, final Logger logger) throws IOException {
        final String pid = pidProperties.getProperty(PID_KEY);
        if (pid != null && !pid.isBlank()) {
            writePidFile(pid, logger);
        }

        final File statusFile = getStatusFile(logger);
        if (statusFile.exists() && !statusFile.delete()) {
            logger.warn("Failed to delete {}", statusFile);
        }

        if (!statusFile.createNewFile()) {
            throw new IOException("Failed to create file " + statusFile);
        }

        try {
            final Set<PosixFilePermission> perms = new HashSet<>();
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(statusFile.toPath(), perms);
        } catch (final Exception e) {
            logger.warn("Failed to set permissions so that only the owner can read status file {}; "
                    + "this may allows others to have access to the key needed to communicate with NiFi. "
                    + "Permissions should be changed so that only the owner can read this file", statusFile);
        }

        try (final FileOutputStream fos = new FileOutputStream(statusFile)) {
            pidProperties.store(fos, null);
            fos.getFD().sync();
        }

        logger.debug("Saved Properties {} to {}", pidProperties, statusFile);
    }

    private synchronized void writePidFile(final String pid, final Logger logger) throws IOException {
        final File pidFile = getPidFile(logger);
        if (pidFile.exists() && !pidFile.delete()) {
            logger.warn("Failed to delete {}", pidFile);
        }

        if (!pidFile.createNewFile()) {
            throw new IOException("Failed to create file " + pidFile);
        }

        try {
            final Set<PosixFilePermission> perms = new HashSet<>();
            perms.add(PosixFilePermission.OWNER_WRITE);
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.GROUP_READ);
            perms.add(PosixFilePermission.OTHERS_READ);
            Files.setPosixFilePermissions(pidFile.toPath(), perms);
        } catch (final Exception e) {
            logger.warn("Failed to set permissions so that only the owner can read pid file {}; "
                    + "this may allows others to have access to the key needed to communicate with NiFi. "
                    + "Permissions should be changed so that only the owner can read this file", pidFile);
        }

        try (final FileOutputStream fos = new FileOutputStream(pidFile)) {
            fos.write(pid.getBytes(StandardCharsets.UTF_8));
            fos.getFD().sync();
        }

        logger.debug("Saved PID [{}] to [{}]", pid, pidFile);
    }

    private boolean isPingSuccessful(final int port, final String secretKey, final Logger logger) {
        logger.debug("Pinging {}", port);

        try (final Socket socket = new Socket("localhost", port)) {
            final OutputStream out = socket.getOutputStream();
            out.write((PING_CMD + " " + secretKey + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();

            logger.debug("Sent PING command");
            socket.setSoTimeout(5000);
            final InputStream in = socket.getInputStream();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            final String response = reader.readLine();
            logger.debug("PING response: {}", response);
            out.close();
            reader.close();

            return PING_CMD.equals(response);
        } catch (final IOException ioe) {
            return false;
        }
    }

    private Integer getCurrentPort(final Logger logger) throws IOException {
        final Properties props = loadProperties(logger);
        final String portVal = props.getProperty("port");
        if (portVal == null) {
            logger.debug("No Port found in status file");
            return null;
        } else {
            logger.debug("Port defined in status file: {}", portVal);
        }

        final int port = Integer.parseInt(portVal);
        final boolean success = isPingSuccessful(port, props.getProperty("secret.key"), logger);
        if (success) {
            logger.debug("Successful PING on port {}", port);
            return port;
        }

        final String pid = props.getProperty(PID_KEY);
        logger.debug("PID in status file is {}", pid);
        if (pid != null) {
            final boolean procRunning = isProcessRunning(pid, logger);
            if (procRunning) {
                return port;
            } else {
                return null;
            }
        }

        return null;
    }

    private boolean isProcessRunning(final String pid, final Logger logger) {
        try {
            // We use the "ps" command to check if the process is still running.
            final ProcessBuilder builder = new ProcessBuilder();

            builder.command("ps", "-p", pid);
            final Process proc = builder.start();

            // Look for the pid in the output of the 'ps' command.
            boolean running = false;
            String line;
            try (final InputStream in = proc.getInputStream();
                 final Reader streamReader = new InputStreamReader(in);
                 final BufferedReader reader = new BufferedReader(streamReader)) {

                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith(pid)) {
                        running = true;
                    }
                }
            }

            // If output of the ps command had our PID, the process is running.
            if (running) {
                logger.debug("Process with PID {} is running", pid);
            } else {
                logger.debug("Process with PID {} is not running", pid);
            }

            return running;
        } catch (final IOException ioe) {
            System.err.println("Failed to determine if Process " + pid + " is running; assuming that it is not");
            return false;
        }
    }

    private Status getStatus(final Logger logger) {
        final Properties props;
        try {
            props = loadProperties(logger);
        } catch (final IOException ioe) {
            return new Status(null, null, false, false);
        }

        if (props == null) {
            return new Status(null, null, false, false);
        }

        final String portValue = props.getProperty("port");
        final String pid = props.getProperty(PID_KEY);
        final String secretKey = props.getProperty("secret.key");

        if (portValue == null && pid == null) {
            return new Status(null, null, false, false);
        }

        Integer port = null;
        boolean pingSuccess = false;
        if (portValue != null) {
            try {
                port = Integer.parseInt(portValue);
                pingSuccess = isPingSuccessful(port, secretKey, logger);
            } catch (final NumberFormatException nfe) {
                return new Status(null, null, false, false);
            }
        }

        if (pingSuccess) {
            return new Status(port, pid, true, true);
        }

        final boolean alive = pid != null && isProcessRunning(pid, logger);
        return new Status(port, pid, pingSuccess, alive);
    }

    public int status() throws IOException {
        final Logger logger = cmdLogger;
        final Status status = getStatus(logger);
        if (status.isRespondingToPing()) {
            logger.info("Apache NiFi PID [{}] running with Bootstrap Port [{}]", status.getPid(), status.getPort());
            return 0;
        }

        if (status.isProcessRunning()) {
            logger.info("Apache NiFi PID [{}] running but not responding with Bootstrap Port [{}]", status.getPid(), status.getPort());
            return 4;
        }

        if (status.getPort() == null) {
            logger.info("Apache NiFi is not running");
            return 3;
        }

        if (status.getPid() == null) {
            logger.info("Apache NiFi is not responding to Ping requests. The process may have died or may be hung");
        } else {
            logger.info("Apache NiFi is not running");
        }
        return 3;
    }

    public void env() {
        final Logger logger = cmdLogger;
        final Status status = getStatus(logger);
        if (status.getPid() == null) {
            logger.info("Apache NiFi is not running");
            return;
        }
        final Class<?> virtualMachineClass;
        try {
            virtualMachineClass = Class.forName("com.sun.tools.attach.VirtualMachine");
        } catch (final ClassNotFoundException cnfe) {
            logger.error("Seems tools.jar (Linux / Windows JDK) or classes.jar (Mac OS) is not available in classpath");
            return;
        }
        final Method attachMethod;
        final Method detachMethod;

        try {
            attachMethod = virtualMachineClass.getMethod("attach", String.class);
            detachMethod = virtualMachineClass.getDeclaredMethod("detach");
        } catch (final Exception e) {
            logger.error("Methods required for getting environment not available", e);
            return;
        }

        final Object virtualMachine;
        try {
            virtualMachine = attachMethod.invoke(null, status.getPid());
        } catch (final Throwable t) {
            logger.error("Problem attaching to NiFi", t);
            return;
        }

        try {
            final Method getSystemPropertiesMethod = virtualMachine.getClass().getMethod("getSystemProperties");

            final Properties sysProps = (Properties) getSystemPropertiesMethod.invoke(virtualMachine);
            for (Entry<Object, Object> syspropEntry : sysProps.entrySet()) {
                logger.info("{} = {}", syspropEntry.getKey(), syspropEntry.getValue());
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        } finally {
            try {
                detachMethod.invoke(virtualMachine);
            } catch (final Exception e) {
                logger.warn("Caught exception detaching from process", e);
            }
        }
    }

    /**
     * Writes NiFi diagnostic information to the given file; if the file is null, logs at INFO level instead.
     */
    public void diagnostics(final File dumpFile, final boolean verbose) throws IOException {
        final String args = verbose ? "--verbose=true" : null;
        makeRequest(DIAGNOSTICS_CMD, args, dumpFile, null, "diagnostics information");
    }

    public void clusterStatus(final File dumpFile) throws IOException {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            makeRequest(CLUSTER_STATUS_CMD, null, dumpFile, baos, "cluster status");

            final String response = baos.toString(StandardCharsets.UTF_8);
            System.out.println("Cluster Status: " + response);
        }
    }

    /**
     * Writes a NiFi thread dump to the given file; if file is null, logs at
     * INFO level instead.
     *
     * @param dumpFile the file to write the dump content to
     * @throws IOException if any issues occur while writing the dump file
     */
    public void dump(final File dumpFile) throws IOException {
        makeRequest(DUMP_CMD, null, dumpFile, null, "thread dump");
    }

    /**
     * Writes NiFi status history information to the given file.
     *
     * @param dumpFile the file to write the dump content to
     * @throws IOException if any issues occur while writing the dump file
     */
    public void statusHistory(final File dumpFile, final String days) throws IOException {
        // Due to input validation, the dumpFile cannot currently be null in this scenario.
        makeRequest(STATUS_HISTORY_CMD, days, dumpFile, null, "status history information");
    }

    private boolean isNiFiFullyLoaded() throws IOException, NiFiNotRunningException {
        final Logger logger = defaultLogger;
        final Integer port = getCurrentPort(logger);
        if (port == null) {
            logger.info("Apache NiFi is not currently running");
            throw new NiFiNotRunningException();
        }

        try (final Socket socket = new Socket()) {
            sendRequest(socket, port, IS_LOADED_CMD, null, logger);

            final InputStream in = socket.getInputStream();
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line = reader.readLine();
                return Boolean.parseBoolean(line);
            }
        }
    }

    /**
     * Makes a request to the Bootstrap Listener
     * @param request the request to send
     * @param arguments any arguments for the command, or <code>null</code> if the command takes no arguments
     * @param dumpFile a file to write the results to, or <code>null</code> to skip writing the results to any file
     * @param outputStream an OutputStream to write the results to, or <code>null</code> to skip writing the results to any OutputStream
     * @param contentsDescription a description of the contents being written; used for logging purposes
     * @throws IOException if unable to communicate with the NiFi instance or write out the results
     */
    private void makeRequest(final String request, final String arguments, final File dumpFile, final OutputStream outputStream, final String contentsDescription) throws IOException {
        final Logger logger = defaultLogger;    // dump to bootstrap log file by default
        final Integer port = getCurrentPort(logger);
        if (port == null) {
            cmdLogger.info("Apache NiFi is not currently running");
            logger.info("Apache NiFi is not currently running");
            return;
        }

        final OutputStream fileOut = dumpFile == null ? null : new FileOutputStream(dumpFile);
        try {
            try (final Socket socket = new Socket()) {
                sendRequest(socket, port, request, arguments, logger);

                final InputStream in = socket.getInputStream();
                try (final BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        boolean written = false;
                        if (fileOut != null) {
                            fileOut.write(line.getBytes(StandardCharsets.UTF_8));
                            fileOut.write('\n');
                            written = true;
                        }

                        if (outputStream != null) {
                            outputStream.write(line.getBytes(StandardCharsets.UTF_8));
                            outputStream.write('\n');
                            written = true;
                        }

                        if (!written) {
                            logger.info(line);
                        }
                    }
                }
            }
        } finally {
            if (fileOut != null) {
                fileOut.close();
                cmdLogger.info("Successfully wrote {} to {}", contentsDescription, dumpFile.getAbsolutePath());
            }
        }
    }

    private void sendRequest(Socket socket, Integer port, String request, String arguments, Logger logger) throws IOException {
        logger.debug("Connecting to NiFi instance");
        socket.setSoTimeout(60000);
        socket.connect(new InetSocketAddress("localhost", port));
        logger.debug("Established connection to NiFi instance.");
        socket.setSoTimeout(60000);

        logger.debug("Sending {} Command to port {}", request, port);
        final OutputStream socketOut = socket.getOutputStream();

        final Properties nifiProps = loadProperties(logger);
        final String secretKey = nifiProps.getProperty("secret.key");

        if (arguments == null) {
            socketOut.write((request + " " + secretKey + "\n").getBytes(StandardCharsets.UTF_8));
        } else {
            socketOut.write((request + " " + secretKey + " " + arguments + "\n").getBytes(StandardCharsets.UTF_8));
        }

        socketOut.flush();
    }


    public Integer decommission(final boolean shutdown) throws IOException {
        final Logger logger = cmdLogger;
        final Integer port = getCurrentPort(logger);
        if (port == null) {
            logger.info("Apache NiFi is not currently running");
            return 15;
        }

        // indicate that a stop command is in progress
        final File lockFile = getLockFile(logger);
        if (!lockFile.exists()) {
            lockFile.createNewFile();
        }

        final Properties nifiProps = loadProperties(logger);
        final String secretKey = nifiProps.getProperty("secret.key");
        final String pid = nifiProps.getProperty(PID_KEY);
        final File statusFile = getStatusFile(logger);
        final File pidFile = getPidFile(logger);

        try (final Socket socket = new Socket()) {
            logger.debug("Connecting to NiFi instance");
            socket.setSoTimeout(10000);
            socket.connect(new InetSocketAddress("localhost", port));
            logger.debug("Established connection to NiFi instance.");

            // We don't know how long it will take for the offloading to complete. It could be a while. So don't timeout.
            // User can press Ctrl+C to terminate if they don't want to wait
            socket.setSoTimeout(0);

            logger.debug("Sending DECOMMISSION Command to port {}", port);
            final OutputStream out = socket.getOutputStream();
            final String command = DECOMMISSION_CMD + " " + secretKey + " --shutdown=" + shutdown + "\n";
            out.write(command.getBytes(StandardCharsets.UTF_8));
            out.flush();
            socket.shutdownOutput();

            final String response = readResponse(socket.getInputStream());

            if (DECOMMISSION_CMD.equals(response)) {
                logger.debug("Received response to DECOMMISSION command: {}", response);

                if (pid != null) {
                    waitForShutdown(pid, logger, statusFile, pidFile);
                }

                return null;
            } else {
                logger.error("When sending DECOMMISSION command to NiFi, got unexpected response {}", response);
                return 18;
            }
        } finally {
            if (lockFile.exists() && !lockFile.delete()) {
                logger.error("Failed to delete lock file {}; this file should be cleaned up manually", lockFile);
            }
        }
    }

    public void stop() throws IOException {
        final Logger logger = cmdLogger;
        final Integer port = getCurrentPort(logger);
        if (port == null) {
            logger.info("Apache NiFi is not currently running");
            return;
        }

        // indicate that a stop command is in progress
        final File lockFile = getLockFile(logger);
        if (!lockFile.exists()) {
            lockFile.createNewFile();
        }

        final Properties nifiProps = loadProperties(logger);
        final String secretKey = nifiProps.getProperty("secret.key");
        final String pid = nifiProps.getProperty(PID_KEY);
        final File statusFile = getStatusFile(logger);
        final File pidFile = getPidFile(logger);

        try (final Socket socket = new Socket()) {
            logger.debug("Connecting to NiFi instance");
            socket.setSoTimeout(10000);
            socket.connect(new InetSocketAddress("localhost", port));
            logger.debug("Established connection to NiFi instance.");
            socket.setSoTimeout(10000);

            logger.debug("Sending SHUTDOWN Command to port {}", port);
            final OutputStream out = socket.getOutputStream();
            out.write((SHUTDOWN_CMD + " " + secretKey + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            socket.shutdownOutput();

            final String response = readResponse(socket.getInputStream());
            logger.debug("Received response to SHUTDOWN command: {}", response);

            if (SHUTDOWN_CMD.equals(response)) {
                logger.info("Apache NiFi has accepted the Shutdown Command and is shutting down now");

                if (pid != null) {
                    waitForShutdown(pid, logger, statusFile, pidFile);
                }
            } else {
                logger.error("When sending SHUTDOWN command to NiFi, got unexpected response: {}", response);
            }
        } catch (final IOException ioe) {
            if (pid == null) {
                logger.error("Failed to send shutdown command to port {} due to {}. No PID found for the NiFi process, so unable to kill process; "
                        + "the process should be killed manually.", port, ioe.toString());
            } else {
                logger.error("Failed to send shutdown command to port {} due to {}. Will kill the NiFi Process with PID {}.", port, ioe, pid);
                killProcessTree(pid, logger);
                if (statusFile.exists() && !statusFile.delete()) {
                    logger.error("Failed to delete status file {}; this file should be cleaned up manually", statusFile);
                }
            }
        } finally {
            if (lockFile.exists() && !lockFile.delete()) {
                logger.error("Failed to delete lock file {}; this file should be cleaned up manually", lockFile);
            }
        }
    }

    private String readResponse(final InputStream in) throws IOException {
        int lastChar;
        final StringBuilder sb = new StringBuilder();
        while ((lastChar = in.read()) > -1) {
            sb.append((char) lastChar);
        }

        return sb.toString().trim();
    }

    private void waitForShutdown(final String pid, final Logger logger, final File statusFile, final File pidFile) throws IOException {
        final Properties bootstrapProperties = new Properties();
        try (final FileInputStream fis = new FileInputStream(bootstrapConfigFile)) {
            bootstrapProperties.load(fis);
        }

        String gracefulShutdown = bootstrapProperties.getProperty(GRACEFUL_SHUTDOWN_PROP, DEFAULT_GRACEFUL_SHUTDOWN_VALUE);
        int gracefulShutdownSeconds;
        try {
            gracefulShutdownSeconds = Integer.parseInt(gracefulShutdown);
        } catch (final NumberFormatException nfe) {
            gracefulShutdownSeconds = Integer.parseInt(DEFAULT_GRACEFUL_SHUTDOWN_VALUE);
        }

        final long startWait = System.nanoTime();
        while (isProcessRunning(pid, logger)) {
            logger.info("NiFi PID [{}] shutdown in progress...", pid);
            final long waitNanos = System.nanoTime() - startWait;
            final long waitSeconds = TimeUnit.NANOSECONDS.toSeconds(waitNanos);
            if (waitSeconds >= gracefulShutdownSeconds && gracefulShutdownSeconds > 0) {
                if (isProcessRunning(pid, logger)) {
                    logger.warn("NiFi PID [{}] shutdown not completed after {} seconds: Killing process", pid, gracefulShutdownSeconds);
                    try {
                        killProcessTree(pid, logger);
                    } catch (final IOException ioe) {
                        logger.error("Failed to kill Process with PID {}", pid);
                    }
                }
                break;
            } else {
                try {
                    Thread.sleep(GRACEFUL_SHUTDOWN_RETRY_MILLIS);
                } catch (final InterruptedException ie) {
                }
            }
        }

        if (statusFile.exists() && !statusFile.delete()) {
            logger.error("Failed to delete status file {}; this file should be cleaned up manually", statusFile);
        }

        if (pidFile.exists() && !pidFile.delete()) {
            logger.error("Failed to delete pid file {}; this file should be cleaned up manually", pidFile);
        }

        logger.info("NiFi PID [{}] shutdown completed", pid);
    }

    private static List<String> getChildProcesses(final String ppid) throws IOException {
        final Process proc = Runtime.getRuntime().exec(new String[]{"ps", "-o", "pid", "--no-headers", "--ppid", ppid});
        final List<String> childPids = new ArrayList<>();
        try (final InputStream in = proc.getInputStream();
             final BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {

            String line;
            while ((line = reader.readLine()) != null) {
                childPids.add(line.trim());
            }
        }

        return childPids;
    }

    private void killProcessTree(final String pid, final Logger logger) throws IOException {
        logger.debug("Killing Process Tree for PID {}", pid);

        final List<String> children = getChildProcesses(pid);
        logger.debug("Children of PID {}: {}", pid, children);

        for (final String childPid : children) {
            killProcessTree(childPid, logger);
        }

        Runtime.getRuntime().exec(new String[]{"kill", "-9", pid});
    }

    public static boolean isAlive(final Process process) {
        try {
            process.exitValue();
            return false;
        } catch (final IllegalStateException | IllegalThreadStateException itse) {
            return true;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void start(final boolean monitor) throws IOException {
        final Integer port = getCurrentPort(cmdLogger);
        if (port != null) {
            cmdLogger.info("Apache NiFi is already running, listening to Bootstrap on port {}", port);
            return;
        }

        final File prevLockFile = getLockFile(cmdLogger);
        if (prevLockFile.exists() && !prevLockFile.delete()) {
            cmdLogger.warn("Failed to delete previous lock file {}; this file should be cleaned up manually", prevLockFile);
        }

        runtimeValidatorExecutor.execute();

        final ProcessBuilder builder = new ProcessBuilder();

        if (!bootstrapConfigFile.exists()) {
            throw new FileNotFoundException(bootstrapConfigFile.getAbsolutePath());
        }

        final Properties properties = new Properties();
        try (final FileInputStream fis = new FileInputStream(bootstrapConfigFile)) {
            properties.load(fis);
        }

        final Map<String, String> props = new HashMap<>();
        props.putAll((Map) properties);

        final String specifiedWorkingDir = props.get("working.dir");
        if (specifiedWorkingDir != null) {
            builder.directory(new File(specifiedWorkingDir));
        }

        final File bootstrapConfigAbsoluteFile = bootstrapConfigFile.getAbsoluteFile();
        final File binDir = bootstrapConfigAbsoluteFile.getParentFile();
        final File workingDir = binDir.getParentFile();

        if (specifiedWorkingDir == null) {
            builder.directory(workingDir);
        }

        final String nifiLogDir = replaceNull(System.getProperty("org.apache.nifi.bootstrap.config.log.dir"), DEFAULT_LOG_DIR).trim();

        final String libFilename = replaceNull(props.get("lib.dir"), "./lib").trim();
        File libDir = getFile(libFilename, workingDir);

        final String confFilename = replaceNull(props.get("conf.dir"), "./conf").trim();
        File confDir = getFile(confFilename, workingDir);

        String nifiPropsFilename = props.get("props.file");
        if (nifiPropsFilename == null) {
            if (confDir.exists()) {
                nifiPropsFilename = new File(confDir, "nifi.properties").getAbsolutePath();
            } else {
                nifiPropsFilename = DEFAULT_CONFIG_FILE;
            }
        }

        nifiPropsFilename = nifiPropsFilename.trim();

        String maximumHeapSize = null;
        String minimumHeapSize = null;

        final List<String> javaAdditionalArgs = new ArrayList<>();
        for (final Map.Entry<String, String> entry : props.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();

            if (key.startsWith("java.arg")) {
                javaAdditionalArgs.add(value);
                if (value.startsWith("-Xms")) {
                    minimumHeapSize = value.replace("-Xms", "");
                }
                if (value.startsWith("-Xmx")) {
                    maximumHeapSize = value.replace("-Xmx", "");
                }
            }
        }

        final File[] libFiles = libDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(final File dir, final String filename) {
                return filename.toLowerCase().endsWith(".jar");
            }
        });

        if (libFiles == null || libFiles.length == 0) {
            throw new RuntimeException("Could not find lib directory at " + libDir.getAbsolutePath());
        }

        final File[] confFiles = confDir.listFiles();
        if (confFiles == null || confFiles.length == 0) {
            throw new RuntimeException("Could not find conf directory at " + confDir.getAbsolutePath());
        }

        final List<String> cpFiles = new ArrayList<>(confFiles.length + libFiles.length);
        cpFiles.add(confDir.getAbsolutePath());
        for (final File file : libFiles) {
            cpFiles.add(file.getAbsolutePath());
        }

        defaultLogger.info(getPlatformDetails(minimumHeapSize, maximumHeapSize));

        final StringBuilder classPathBuilder = new StringBuilder();
        for (int i = 0; i < cpFiles.size(); i++) {
            final String filename = cpFiles.get(i);
            classPathBuilder.append(filename);
            if (i < cpFiles.size() - 1) {
                classPathBuilder.append(File.pathSeparatorChar);
            }
        }

        final String classPath = classPathBuilder.toString();
        String javaCmd = props.get("java");
        if (javaCmd == null) {
            javaCmd = DEFAULT_JAVA_CMD;
        }
        if (javaCmd.equals(DEFAULT_JAVA_CMD)) {
            String javaHome = System.getenv("JAVA_HOME");
            if (javaHome != null) {
                String fileExtension = isWindows() ? ".exe" : "";
                File javaFile = new File(javaHome + File.separatorChar + "bin"
                        + File.separatorChar + "java" + fileExtension);
                if (javaFile.exists() && javaFile.canExecute()) {
                    javaCmd = javaFile.getAbsolutePath();
                }
            }
        }

        try {
            final ApplicationPropertyHandler propertyHandler = new SecurityApplicationPropertyHandler(cmdLogger);
            final Path applicationPropertiesLocation = Paths.get(nifiPropsFilename);
            propertyHandler.handleProperties(applicationPropertiesLocation);
        } catch (final RuntimeException e) {
            cmdLogger.error("Self-Signed Certificate Generation Failed", e);
        }

        final String listenPortPropString = props.get(NIFI_BOOTSTRAP_LISTEN_PORT_PROP);
        int listenPortPropInt = 0; // default to zero (random ephemeral port)
        if (listenPortPropString != null) {
            try {
                listenPortPropInt = Integer.parseInt(listenPortPropString.trim());
            } catch (final Exception e) {
                // no-op, use the default
            }
        }

        final NiFiListener listener = new NiFiListener();
        final int listenPort = listener.start(this, listenPortPropInt);

        final List<String> cmd = new ArrayList<>();

        cmd.add(javaCmd);
        cmd.add("-classpath");
        cmd.add(classPath);
        cmd.addAll(javaAdditionalArgs);

        cmd.add("-Dnifi.properties.file.path=" + nifiPropsFilename);
        cmd.add("-Dnifi.bootstrap.listen.port=" + listenPort);
        cmd.add("-Dapp=NiFi");
        cmd.add("-Dorg.apache.nifi.bootstrap.config.log.dir=" + nifiLogDir);
        cmd.add("org.apache.nifi.NiFi");

        builder.command(cmd);

        final StringBuilder cmdBuilder = new StringBuilder();
        for (final String s : cmd) {
          cmdBuilder.append(s).append(" ");
        }

        cmdLogger.info("Starting Apache NiFi...");
        cmdLogger.info("Working Directory: {}", workingDir.getAbsolutePath());
        cmdLogger.info("Command: {}", cmdBuilder);

        String gracefulShutdown = props.get(GRACEFUL_SHUTDOWN_PROP);
        if (gracefulShutdown == null) {
            gracefulShutdown = DEFAULT_GRACEFUL_SHUTDOWN_VALUE;
        }

        final int gracefulShutdownSeconds;
        try {
            gracefulShutdownSeconds = Integer.parseInt(gracefulShutdown);
        } catch (final NumberFormatException nfe) {
            throw new NumberFormatException("The '" + GRACEFUL_SHUTDOWN_PROP + "' property in Bootstrap Config File "
                    + bootstrapConfigAbsoluteFile.getAbsolutePath() + " has an invalid value. Must be a non-negative integer");
        }

        if (gracefulShutdownSeconds < 0) {
            throw new NumberFormatException("The '" + GRACEFUL_SHUTDOWN_PROP + "' property in Bootstrap Config File "
                    + bootstrapConfigAbsoluteFile.getAbsolutePath() + " has an invalid value. Must be a non-negative integer");
        }

        Process process = builder.start();
        handleLogging(process);
        nifiPid = process.pid();
        final Properties pidProperties = new Properties();
        pidProperties.setProperty(PID_KEY, String.valueOf(nifiPid));
        savePidProperties(pidProperties, cmdLogger);
        cmdLogger.info("Application Process [{}] launched", nifiPid);

        shutdownHook = new ShutdownHook(process, nifiPid, this, secretKey, gracefulShutdownSeconds, loggingExecutor);

        if (monitor) {
            final Runtime runtime = Runtime.getRuntime();
            runtime.addShutdownHook(shutdownHook);

            while (true) {
                final boolean alive = isAlive(process);

                if (alive) {
                    try {
                        Thread.sleep(1000L);
                    } catch (final InterruptedException ie) {
                    }
                } else {
                    try {
                        runtime.removeShutdownHook(shutdownHook);
                    } catch (final IllegalStateException ise) {
                        // happens when already shutting down
                    }

                    if (autoRestartNiFi) {
                        final File statusFile = getStatusFile(defaultLogger);
                        if (!statusFile.exists()) {
                            defaultLogger.info("Status File no longer exists. Will not restart NiFi");
                            return;
                        }

                        final File lockFile = getLockFile(defaultLogger);
                        if (lockFile.exists()) {
                            defaultLogger.info("A shutdown was initiated. Will not restart NiFi");
                            return;
                        }

                        final boolean previouslyStarted = getNifiStarted();
                        if (!previouslyStarted) {
                            defaultLogger.info("NiFi never started. Will not restart NiFi");
                            return;
                        } else {
                            setNiFiStarted(false);
                        }

                        defaultLogger.warn("Apache NiFi appears to have died. Restarting...");
                        secretKey = null;
                        process = builder.start();
                        handleLogging(process);

                        nifiPid = process.pid();
                        pidProperties.setProperty(PID_KEY, String.valueOf(nifiPid));
                        savePidProperties(pidProperties, defaultLogger);
                        cmdLogger.info("Application Process [{}] launched", nifiPid);

                        shutdownHook = new ShutdownHook(process, nifiPid, this, secretKey, gracefulShutdownSeconds, loggingExecutor);
                        runtime.addShutdownHook(shutdownHook);

                        final boolean started = waitForStart();

                        if (started) {
                            cmdLogger.info("Application Process [{}] started", nifiPid);
                        } else {
                            defaultLogger.error("Application Process [{}] not started", nifiPid);
                        }
                    } else {
                        return;
                    }
                }
            }
        }
    }

    private String getPlatformDetails(final String minimumHeapSize, final String maximumHeapSize) {
        final Map<String, String> details = new LinkedHashMap<String, String>(6);

        details.put("javaVersion", System.getProperty("java.version"));
        details.put("availableProcessors", Integer.toString(Runtime.getRuntime().availableProcessors()));

        try {
            final ObjectName osObjectName = ManagementFactory.getOperatingSystemMXBean().getObjectName();
            final MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            final Object maxOpenFileCount = mBeanServer.getAttribute(osObjectName, "MaxFileDescriptorCount");
            if (maxOpenFileCount != null) {
                details.put("maxOpenFileDescriptors", String.valueOf(maxOpenFileCount));
            }
            final Object totalPhysicalMemory = mBeanServer.getAttribute(osObjectName, "TotalPhysicalMemorySize");
            if (totalPhysicalMemory != null) {
                details.put("totalPhysicalMemoryMB", String.valueOf(((Long) totalPhysicalMemory) / (1024 * 1024)));
            }
        } catch (final Throwable t) {
            // Ignore. This will throw either ClassNotFound or NoClassDefFoundError if unavailable in this JVM.
        }

        if (minimumHeapSize != null) {
            details.put("minimumHeapSize", minimumHeapSize);
        }
        if (maximumHeapSize != null) {
            details.put("maximumHeapSize", maximumHeapSize);
        }

        return details.toString();
    }

    private void handleLogging(final Process process) {
        final Set<Future<?>> existingFutures = loggingFutures;
        if (existingFutures != null) {
            for (final Future<?> future : existingFutures) {
                future.cancel(false);
            }
        }

        final Future<?> stdOutFuture = loggingExecutor.submit(new Runnable() {
            @Override
            public void run() {
                final Logger stdOutLogger = LoggerFactory.getLogger("org.apache.nifi.StdOut");
                final InputStream in = process.getInputStream();
                try (final BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdOutLogger.info(line);
                    }
                } catch (IOException e) {
                    defaultLogger.error("Failed to read from NiFi's Standard Out stream", e);
                }
            }
        });

        final Future<?> stdErrFuture = loggingExecutor.submit(new Runnable() {
            @Override
            public void run() {
                final Logger stdErrLogger = LoggerFactory.getLogger("org.apache.nifi.StdErr");
                final InputStream in = process.getErrorStream();
                try (final BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdErrLogger.error(line);
                    }
                } catch (IOException e) {
                    defaultLogger.error("Failed to read from NiFi's Standard Error stream", e);
                }
            }
        });

        final Set<Future<?>> futures = new HashSet<>();
        futures.add(stdOutFuture);
        futures.add(stdErrFuture);
        this.loggingFutures = futures;
    }


    private boolean isWindows() {
        final String osName = System.getProperty("os.name");
        return osName != null && osName.toLowerCase().contains("win");
    }

    private boolean waitForStart() {
        lock.lock();
        try {
            final long startTime = System.nanoTime();

            while (ccPort < 1) {
                try {
                    startupCondition.await(1, TimeUnit.SECONDS);
                } catch (final InterruptedException ie) {
                    return false;
                }

                final long waitNanos = System.nanoTime() - startTime;
                final long waitSeconds = TimeUnit.NANOSECONDS.toSeconds(waitNanos);
                if (waitSeconds > STARTUP_WAIT_SECONDS) {
                    return false;
                }
            }
        } finally {
            lock.unlock();
        }
        return true;
    }

    private File getFile(final String filename, final File workingDir) {
        File file = new File(filename);
        if (!file.isAbsolute()) {
            file = new File(workingDir, filename);
        }

        return file;
    }

    private String replaceNull(final String value, final String replacement) {
        return (value == null) ? replacement : value;
    }

    void setAutoRestartNiFi(final boolean restart) {
        this.autoRestartNiFi = restart;
    }

    void setNiFiCommandControlPort(final int port, final String secretKey) throws IOException {

        if (this.secretKey != null && this.ccPort != UNINITIALIZED_CC_PORT) {
            defaultLogger.warn("Blocking attempt to change NiFi command port and secret after they have already been initialized. requestedPort={}", port);
            return;
        }

        this.ccPort = port;
        this.secretKey = secretKey;

        if (shutdownHook != null) {
            shutdownHook.setSecretKey(secretKey);
        }

        final File statusFile = getStatusFile(defaultLogger);

        final Properties nifiProps = new Properties();
        if (nifiPid != -1) {
            nifiProps.setProperty(PID_KEY, String.valueOf(nifiPid));
        }
        nifiProps.setProperty("port", String.valueOf(ccPort));
        nifiProps.setProperty("secret.key", secretKey);

        try {
            savePidProperties(nifiProps, defaultLogger);
        } catch (final IOException ioe) {
            defaultLogger.warn("Apache NiFi has started but failed to persist NiFi Port information to {} due to {}", statusFile.getAbsolutePath(), ioe);
        }

        defaultLogger.info("Apache NiFi now running and listening for Bootstrap requests on port {}", port);
    }

    int getNiFiCommandControlPort() {
        return this.ccPort;
    }

    void setNiFiStarted(final boolean nifiStarted) {
        startedLock.lock();
        try {
            this.nifiStarted = nifiStarted;
        } finally {
            startedLock.unlock();
        }
    }

    boolean getNifiStarted() {
        startedLock.lock();
        try {
            return nifiStarted;
        } finally {
            startedLock.unlock();
        }
    }

    private static class Status {

        private final Integer port;
        private final String pid;

        private final Boolean respondingToPing;
        private final Boolean processRunning;

        public Status(final Integer port, final String pid, final Boolean respondingToPing, final Boolean processRunning) {
            this.port = port;
            this.pid = pid;
            this.respondingToPing = respondingToPing;
            this.processRunning = processRunning;
        }

        public String getPid() {
            return pid;
        }

        public Integer getPort() {
            return port;
        }

        public boolean isRespondingToPing() {
            return Boolean.TRUE.equals(respondingToPing);
        }

        public boolean isProcessRunning() {
            return Boolean.TRUE.equals(processRunning);
        }
    }

    private static class NiFiNotRunningException extends Exception {
        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
