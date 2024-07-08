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

package org.apache.nifi.stateless.basics;

import org.apache.nifi.flow.VersionedPort;
import org.apache.nifi.flow.VersionedProcessor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.registry.flow.VersionedFlowSnapshot;
import org.apache.nifi.stateless.StatelessSystemIT;
import org.apache.nifi.stateless.VersionedFlowBuilder;
import org.apache.nifi.stateless.config.StatelessConfigurationException;
import org.apache.nifi.stateless.flow.DataflowTrigger;
import org.apache.nifi.stateless.flow.StatelessDataflow;
import org.apache.nifi.stateless.flow.TriggerResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AsyncCommitCallbackIT extends StatelessSystemIT {
    private final File inputFile = new File("target/input.txt");
    private final File replacementFile = new File("target/replacement.txt");

    @BeforeEach
    public void setup() throws IOException {
        Files.write(inputFile.toPath(), "Hello World".getBytes(), StandardOpenOption.CREATE);
        Files.deleteIfExists(replacementFile.toPath());
    }

    @Test
    public void testCommitFailureCallbackWhenDownstreamProcessorFails() throws IOException, StatelessConfigurationException, InterruptedException {
        final File failureOutputFile = new File("target/failure-output.txt");
        Files.deleteIfExists(failureOutputFile.toPath());

        final VersionedFlowBuilder builder = new VersionedFlowBuilder();
        final VersionedProcessor generate = builder.createSimpleProcessor("GenerateFlowFile");
        final Map<String, String> generateProperties = new HashMap<>();
        generateProperties.put("File to Write on Commit Failure", failureOutputFile.getAbsolutePath());
        generate.setProperties(generateProperties);

        final VersionedProcessor throwException = builder.createSimpleProcessor("ThrowProcessException");
        builder.createConnection(generate, throwException, "success");

        final StatelessDataflow dataflow = loadDataflow(builder.getFlowSnapshot());
        final DataflowTrigger trigger = dataflow.trigger();
        final TriggerResult result = trigger.getResult();
        assertTrue(result.getFailureCause().isPresent());

        assertTrue(failureOutputFile.exists());
    }

    @Test
    public void testCommitFailureCallbackWhenDownstreamProcessorSessionCommitFails() throws IOException, StatelessConfigurationException, InterruptedException {
        final File failureOutputFile = new File("target/failure-output.txt");
        Files.deleteIfExists(failureOutputFile.toPath());

        final VersionedFlowBuilder builder = new VersionedFlowBuilder();
        final VersionedProcessor generate = builder.createSimpleProcessor("GenerateFlowFile");
        final Map<String, String> generateProperties = new HashMap<>();
        generateProperties.put("File to Write on Commit Failure", failureOutputFile.getAbsolutePath());
        generate.setProperties(generateProperties);

        final VersionedProcessor throwException = builder.createSimpleProcessor("DoNotTransferFlowFile");
        builder.createConnection(generate, throwException, "success");

        final StatelessDataflow dataflow = loadDataflow(builder.getFlowSnapshot());
        final DataflowTrigger trigger = dataflow.trigger();
        final TriggerResult result = trigger.getResult();
        assertTrue(result.getFailureCause().isPresent());

        assertTrue(failureOutputFile.exists());
    }

    @Test
    public void testCleanupAfterFlowFilesTerminated() throws IOException, StatelessConfigurationException, InterruptedException {
        testCleanupAfterFlowFilesTerminated("asynchronous");
    }

    @Test
    public void testSynchronousCommitCleanupAfterFlowFilesTerminated() throws IOException, StatelessConfigurationException, InterruptedException {
        testCleanupAfterFlowFilesTerminated("synchronous");
    }

    private void testCleanupAfterFlowFilesTerminated(final String commitMode) throws IOException, StatelessConfigurationException, InterruptedException {
        final VersionedFlowBuilder builder = new VersionedFlowBuilder();
        final VersionedProcessor ingestFile = builder.createSimpleProcessor("IngestFile");
        final Map<String, String> ingestProperties = new HashMap<>();
        ingestProperties.put("Filename", inputFile.getAbsolutePath());
        ingestProperties.put("Commit Mode", commitMode);
        ingestFile.setProperties(ingestProperties);

        final VersionedProcessor replace = builder.createSimpleProcessor("ReplaceWithFile");
        replace.setProperties(Collections.singletonMap("Filename", replacementFile.getAbsolutePath()));

        final VersionedPort outputPort = builder.createOutputPort("Out");

        builder.createConnection(ingestFile, replace, "success");
        builder.createConnection(replace, outputPort, "success");

        testAsyncCallbackCalledAtFinish(builder.getFlowSnapshot(), inputFile, replacementFile, result -> {
            final List<FlowFile> flowFilesOut = result.getOutputFlowFiles("Out");
            assertEquals(1, flowFilesOut.size());
            final FlowFile out = flowFilesOut.get(0);
            assertEquals(replacementFile.getName(), out.getAttribute("filename"));

            final byte[] outputContents;
            try {
                outputContents = result.readContentAsByteArray(out);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            assertEquals("Good-bye World", new String(outputContents));
        });
    }

    @Test
    public void testAsyncCallbackCalledAfterFlowFilesRoutedToSuccessPort() throws InterruptedException, StatelessConfigurationException, IOException {
        testCleanupAfterFlowFilesRoutedToSuccessPort("asynchronous");
    }

    @Test
    public void testCleanupAfterSynchronousCommitRoutedToSuccessPort() throws InterruptedException, StatelessConfigurationException, IOException {
        testCleanupAfterFlowFilesRoutedToSuccessPort("synchronous");
    }

    private void testCleanupAfterFlowFilesRoutedToSuccessPort(final String commitMode) throws IOException, StatelessConfigurationException, InterruptedException {
        final VersionedFlowBuilder builder = new VersionedFlowBuilder();
        final VersionedProcessor ingestFile = builder.createSimpleProcessor("IngestFile");
        final Map<String, String> ingestProperties = new HashMap<>();
        ingestProperties.put("Filename", inputFile.getAbsolutePath());
        ingestProperties.put("Commit Mode", commitMode);
        ingestFile.setProperties(ingestProperties);

        final VersionedProcessor replace = builder.createSimpleProcessor("ReplaceWithFile");
        replace.setProperties(Collections.singletonMap("Filename", replacementFile.getAbsolutePath()));

        final VersionedProcessor terminate = builder.createSimpleProcessor("TerminateFlowFile");

        builder.createConnection(ingestFile, replace, "success");
        builder.createConnection(replace, terminate, "success");

        testAsyncCallbackCalledAtFinish(builder.getFlowSnapshot(), inputFile, replacementFile, (result) -> { });
    }

    private TriggerResult testAsyncCallbackCalledAtFinish(final VersionedFlowSnapshot flowSnapshot, final File inputFile, final File replacementFile, final Consumer<TriggerResult> resultConsumer)
                throws IOException, InterruptedException, StatelessConfigurationException {
        final StatelessDataflow dataflow = loadDataflow(flowSnapshot);
        final DataflowTrigger trigger = dataflow.trigger();

        Thread.sleep(1000L);
        assertFalse(trigger.getResultNow().isPresent());

        // Write to a temp file, then rename to the replacement file. Otherwise, the file may be created, and then the flow
        // run before the data is written to the file, which can cause the FlowFile to be replaced with 0 bytes.
        final File tempFile = new File(replacementFile.getParentFile(), "." + replacementFile.getName());
        Files.write(tempFile.toPath(), "Good-bye World".getBytes(), StandardOpenOption.CREATE);
        assertTrue(tempFile.renameTo(replacementFile));

        assertTrue(inputFile.exists());

        final TriggerResult result = trigger.getResult();
        assertTrue(inputFile.exists());

        assertTrue(result.isSuccessful());

        resultConsumer.accept(result);

        result.acknowledge();

        // When acknowledge() is called, we do not block until the synchronous commits have been "unwound".
        // So the file may not be cleaned up for a bit. So we wait for that to occur. But the framework will not
        // trigger the flow again until this completes
        while (inputFile.exists()) {
            Thread.sleep(10L);
        }
        assertFalse(inputFile.exists());
        return result;
    }

    @Test
    public void testAsyncCallbackNotCalledIfExceptionThrown() throws IOException, StatelessConfigurationException, InterruptedException {
        testCleanupNotCalledIfExceptionThrown("asynchronous");
    }

    @Test
    public void testSynchronousCleanupNotCalledIfExceptionThrown() throws IOException, StatelessConfigurationException, InterruptedException {
        testCleanupNotCalledIfExceptionThrown("synchronous");
    }

    private void testCleanupNotCalledIfExceptionThrown(final String commitMode) throws IOException, StatelessConfigurationException, InterruptedException {
        final VersionedFlowBuilder builder = new VersionedFlowBuilder();
        final VersionedProcessor ingestFile = builder.createSimpleProcessor("IngestFile");
        final Map<String, String> ingestProperties = new HashMap<>();
        ingestProperties.put("Filename", inputFile.getAbsolutePath());
        ingestProperties.put("Commit Mode", commitMode);
        ingestFile.setProperties(ingestProperties);

        final VersionedProcessor replace = builder.createSimpleProcessor("ReplaceWithFile");
        replace.setProperties(Collections.singletonMap("Filename", replacementFile.getAbsolutePath()));

        final VersionedProcessor exceptionProcessor = builder.createSimpleProcessor("ThrowProcessException");

        builder.createConnection(ingestFile, replace, "success");
        builder.createConnection(replace, exceptionProcessor, "success");

        final StatelessDataflow dataflow = loadDataflow(builder.getFlowSnapshot());
        testAsyncCallbackNotCalledOnFailure(dataflow, inputFile, replacementFile);
    }


    @Test
    public void testAsyncCallbackNotCalledIfRoutedToFailurePort() throws IOException, StatelessConfigurationException, InterruptedException {
        testCleanupNotCalledIfRoutedToFailurePort("asynchronous");
    }

    @Test
    public void testSynchronousCleanupNotCalledIfRoutedToFailurePort() throws IOException, StatelessConfigurationException, InterruptedException {
        testCleanupNotCalledIfRoutedToFailurePort("synchronous");
    }

    private void testCleanupNotCalledIfRoutedToFailurePort(final String commitMode) throws IOException, StatelessConfigurationException, InterruptedException {
        final VersionedFlowBuilder builder = new VersionedFlowBuilder();
        final VersionedProcessor ingestFile = builder.createSimpleProcessor("IngestFile");
        final Map<String, String> ingestProperties = new HashMap<>();
        ingestProperties.put("Filename", inputFile.getAbsolutePath());
        ingestProperties.put("Commit Mode", commitMode);
        ingestFile.setProperties(ingestProperties);

        final VersionedProcessor replace = builder.createSimpleProcessor("ReplaceWithFile");
        replace.setProperties(Collections.singletonMap("Filename", replacementFile.getAbsolutePath()));

        final VersionedPort failurePort = builder.createOutputPort("fail");

        builder.createConnection(ingestFile, replace, "success");
        builder.createConnection(replace, failurePort, "success");

        final StatelessDataflow dataflow = loadDataflow(builder.getFlowSnapshot(), Collections.emptyList(), Collections.singleton("fail"));
        testAsyncCallbackNotCalledOnFailure(dataflow, inputFile, replacementFile);
    }

    private void testAsyncCallbackNotCalledOnFailure(final StatelessDataflow dataflow, final File inputFile, final File replacementFile)
            throws IOException, InterruptedException {

        final DataflowTrigger trigger = dataflow.trigger();

        Thread.sleep(1000L);
        assertFalse(trigger.getResultNow().isPresent());

        Files.write(replacementFile.toPath(), "Good-bye World".getBytes(), StandardOpenOption.CREATE);

        assertTrue(inputFile.exists());

        final TriggerResult result = trigger.getResult();
        assertTrue(inputFile.exists());

        assertFalse(result.isSuccessful());
        result.acknowledge();

        assertTrue(inputFile.exists());
    }

}
