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
package org.apache.nifi.processors.standard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processors.standard.util.ArgumentUtils;
import org.apache.nifi.util.LogMessage;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "Test only runs on *nix")
public class TestExecuteStreamCommand {

    @Test
    public void testDynamicPropertyArgumentsStrategyValid() {
        final TestRunner runner = TestRunners.newTestRunner(ExecuteStreamCommand.class);

        runner.setProperty(ExecuteStreamCommand.ARGUMENTS_STRATEGY, ExecuteStreamCommand.DYNAMIC_PROPERTY_ARGUMENTS_STRATEGY.getValue());
        runner.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        runner.setProperty("command.argument.1", "-version");

        runner.assertValid();

        final List<LogMessage> warnMessages = runner.getLogger().getWarnMessages();
        assertTrue(warnMessages.isEmpty(), "Warning Log Messages found");
    }

    @Test
    public void testCommandArgumentsPropertyStrategyValid() {
        final TestRunner runner = TestRunners.newTestRunner(ExecuteStreamCommand.class);

        runner.setProperty(ExecuteStreamCommand.ARGUMENTS_STRATEGY, ExecuteStreamCommand.COMMAND_ARGUMENTS_PROPERTY_STRATEGY.getValue());
        runner.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        runner.setProperty("RUNTIME_VERSION", "version-1");

        runner.assertValid();

        final List<LogMessage> warnMessages = runner.getLogger().getWarnMessages();
        assertTrue(warnMessages.isEmpty(), "Warning Log Messages found");
    }

    @Test
    public void testExecuteJar() throws Exception {
        File exJar = new File("src/test/resources/ExecuteCommand/TestSuccess.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.setProperty(ExecuteStreamCommand.MIME_TYPE.getName(), "text/plain");
        controller.enqueue(dummy.toPath());
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_ARGUMENTS, "-jar;" + jarPath);
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 1);

        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP);
        MockFlowFile outputFlowFile = flowFiles.get(0);
        byte[] byteArray = outputFlowFile.toByteArray();
        String result = new String(byteArray);
        assertTrue(Pattern.compile("Test was a success\r?\n").matcher(result).find());
        assertEquals("0", outputFlowFile.getAttribute("execution.status"));
        assertEquals("java", outputFlowFile.getAttribute("execution.command"));
        assertEquals("-jar;", outputFlowFile.getAttribute("execution.command.args").substring(0, 5));
        String attribute = outputFlowFile.getAttribute("execution.command.args");
        String expected = "src" + File.separator + "test" + File.separator + "resources" + File.separator + "ExecuteCommand" + File.separator + "TestSuccess.jar";
        assertEquals(expected, attribute.substring(attribute.length() - expected.length()));
        outputFlowFile.assertAttributeEquals(CoreAttributes.MIME_TYPE.key(), "text/plain");

        MockFlowFile originalFlowFile = controller.getFlowFilesForRelationship(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP).get(0);
        assertEquals(outputFlowFile.getAttribute("execution.status"), originalFlowFile.getAttribute("execution.status"));
        assertEquals(outputFlowFile.getAttribute("execution.command"), originalFlowFile.getAttribute("execution.command"));
        assertEquals(outputFlowFile.getAttribute("execution.command.args"), originalFlowFile.getAttribute("execution.command.args"));
    }

    @Test
    public void testExecuteJarDynamicPropArgs() throws Exception {
        File exJar = new File("src/test/resources/ExecuteCommand/TestSuccess.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue(dummy.toPath());
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.ARGUMENTS_STRATEGY, ExecuteStreamCommand.DYNAMIC_PROPERTY_ARGUMENTS_STRATEGY.getValue());
        PropertyDescriptor dynamicProp1 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.1")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp1, "-jar");
        PropertyDescriptor dynamicProp2 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.2")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp2, jarPath);
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 1);

        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP);
        MockFlowFile outputFlowFile = flowFiles.get(0);
        byte[] byteArray = outputFlowFile.toByteArray();
        String result = new String(byteArray);
        assertTrue(Pattern.compile("Test was a success\r?\n").matcher(result).find());
        assertEquals("0", outputFlowFile.getAttribute("execution.status"));
        assertEquals("java", outputFlowFile.getAttribute("execution.command"));
        assertEquals("-jar", outputFlowFile.getAttribute("execution.command.args").substring(0, 4).trim());
        String attribute = outputFlowFile.getAttribute("execution.command.args");
        String expected = "src" + File.separator + "test" + File.separator + "resources" + File.separator + "ExecuteCommand" + File.separator + "TestSuccess.jar";
        assertEquals(expected, attribute.substring(attribute.length() - expected.length()));

        MockFlowFile originalFlowFile = controller.getFlowFilesForRelationship(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP).get(0);
        assertEquals(outputFlowFile.getAttribute("execution.status"), originalFlowFile.getAttribute("execution.status"));
        assertEquals(outputFlowFile.getAttribute("execution.command"), originalFlowFile.getAttribute("execution.command"));
        assertEquals(outputFlowFile.getAttribute("execution.command.args"), originalFlowFile.getAttribute("execution.command.args"));
    }


    @Test
    public void testExecuteJarWithBadPath() throws Exception {
        File exJar = new File("src/test/resources/ExecuteCommand/noSuchFile.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue(dummy.toPath());
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_ARGUMENTS, "-jar;" + jarPath);
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 0);
        controller.assertTransferCount(ExecuteStreamCommand.NONZERO_STATUS_RELATIONSHIP, 1);
        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.NONZERO_STATUS_RELATIONSHIP);
        MockFlowFile flowFile = flowFiles.get(0);
        assertEquals(0, flowFile.getSize());
        assertTrue(flowFile.getAttribute("execution.error").contains("Error: Unable to access jarfile"));
        assertTrue(flowFile.isPenalized());
    }

    @Test
    public void testExecuteJarWithBadPathDynamicProperties() throws Exception {
        File exJar = new File("src/test/resources/ExecuteCommand/noSuchFile.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue(dummy.toPath());
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.ARGUMENTS_STRATEGY, ExecuteStreamCommand.DYNAMIC_PROPERTY_ARGUMENTS_STRATEGY.getValue());
        PropertyDescriptor dynamicProp1 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.1")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp1, "-jar");
        PropertyDescriptor dynamicProp2 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.2")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp2, jarPath);
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 0);
        controller.assertTransferCount(ExecuteStreamCommand.NONZERO_STATUS_RELATIONSHIP, 1);
        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.NONZERO_STATUS_RELATIONSHIP);
        MockFlowFile flowFile = flowFiles.get(0);
        assertEquals(0, flowFile.getSize());
        assertTrue(flowFile.getAttribute("execution.error").contains("Error: Unable to access jarfile"));
        assertTrue(flowFile.isPenalized());
    }

    @Test
    public void testExecuteIngestAndUpdate() throws IOException {
        File exJar = new File("src/test/resources/ExecuteCommand/TestIngestAndUpdate.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        File dummy10MBytes = new File("target/10MB.txt");
        try (FileOutputStream fos = new FileOutputStream(dummy10MBytes)) {
            byte[] bytes = Files.readAllBytes(dummy.toPath());
            assertEquals(1000, bytes.length);
            for (int i = 0; i < 10000; i++) {
                fos.write(bytes, 0, 1000);
            }
        }
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue(dummy10MBytes.toPath());
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_ARGUMENTS, "-jar;" + jarPath);
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 1);
        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP);
        byte[] byteArray = flowFiles.get(0).toByteArray();
        String result = new String(byteArray);

        assertTrue(Pattern.compile("nifi-standard-processors:ModifiedResult\r?\n").matcher(result).find());
    }

    @Test
    public void testExecuteIngestAndUpdateDynamicProperties() throws IOException {
        File exJar = new File("src/test/resources/ExecuteCommand/TestIngestAndUpdate.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        File dummy10MBytes = new File("target/10MB.txt");
        try (FileOutputStream fos = new FileOutputStream(dummy10MBytes)) {
            byte[] bytes = Files.readAllBytes(dummy.toPath());
            assertEquals(1000, bytes.length);
            for (int i = 0; i < 10000; i++) {
                fos.write(bytes, 0, 1000);
            }
        }
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue(dummy10MBytes.toPath());
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.ARGUMENTS_STRATEGY, ExecuteStreamCommand.DYNAMIC_PROPERTY_ARGUMENTS_STRATEGY.getValue());
        PropertyDescriptor dynamicProp1 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.1")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp1, "-jar");
        PropertyDescriptor dynamicProp2 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.2")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp2, jarPath);
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 1);
        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP);
        byte[] byteArray = flowFiles.get(0).toByteArray();
        String result = new String(byteArray);

        assertTrue(Pattern.compile("nifi-standard-processors:ModifiedResult\r?\n").matcher(result).find());
    }

    @Test
    public void testLoggingToStdErr() throws IOException {
        File exJar = new File("src/test/resources/ExecuteCommand/TestLogStdErr.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1mb.txt");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.setValidateExpressionUsage(false);
        controller.enqueue(dummy.toPath());
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_ARGUMENTS, "-jar;" + jarPath);
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 1);
        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP);
        MockFlowFile flowFile = flowFiles.get(0);
        assertEquals(0, flowFile.getSize());
        assertTrue(flowFile.getAttribute("execution.error").contains("ÄÖÜäöüß"));
        assertTrue(flowFile.getAttribute("execution.error").contains("fffffffffffffffffffffffffffffff"));
    }

    @Test
    public void testLoggingToStdErrDynamicProperties() throws IOException {
        File exJar = new File("src/test/resources/ExecuteCommand/TestLogStdErr.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1mb.txt");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.setValidateExpressionUsage(false);
        controller.enqueue(dummy.toPath());
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.ARGUMENTS_STRATEGY, ExecuteStreamCommand.DYNAMIC_PROPERTY_ARGUMENTS_STRATEGY.getValue());
        PropertyDescriptor dynamicProp1 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.1")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp1, "-jar");
        PropertyDescriptor dynamicProp2 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.2")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp2, jarPath);
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 1);
        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP);
        MockFlowFile flowFile = flowFiles.get(0);
        assertEquals(0, flowFile.getSize());
        assertTrue(flowFile.getAttribute("execution.error").contains("ÄÖÜäöüß"));
        assertTrue(flowFile.getAttribute("execution.error").contains("fffffffffffffffffffffffffffffff"));
    }

    @Test
    public void testExecuteIngestAndUpdateWithWorkingDir() throws IOException {
        File exJar = new File("src/test/resources/ExecuteCommand/TestIngestAndUpdate.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue(dummy.toPath());
        controller.setProperty(ExecuteStreamCommand.WORKING_DIR, "target");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_ARGUMENTS, "-jar;" + jarPath);
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 1);
        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP);
        byte[] byteArray = flowFiles.get(0).toByteArray();
        String result = new String(byteArray);

        final String quotedSeparator = Pattern.quote(File.separator);
        assertTrue(Pattern.compile(quotedSeparator + "nifi-standard-processors" + quotedSeparator + "target:ModifiedResult\r?\n").matcher(result).find());
    }

    @Test
    public void testExecuteIngestAndUpdateWithWorkingDirDynamicProperties() throws IOException {
        File exJar = new File("src/test/resources/ExecuteCommand/TestIngestAndUpdate.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue(dummy.toPath());
        controller.setProperty(ExecuteStreamCommand.WORKING_DIR, "target");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.ARGUMENTS_STRATEGY, ExecuteStreamCommand.DYNAMIC_PROPERTY_ARGUMENTS_STRATEGY.getValue());
        PropertyDescriptor dynamicProp1 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.1")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp1, "-jar");
        PropertyDescriptor dynamicProp2 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.2")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp2, jarPath);
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 1);
        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP);
        byte[] byteArray = flowFiles.get(0).toByteArray();
        String result = new String(byteArray);

        final String quotedSeparator = Pattern.quote(File.separator);
        assertTrue(Pattern.compile(quotedSeparator + "nifi-standard-processors" + quotedSeparator + "target:ModifiedResult\r?\n").matcher(result).find());
    }

    @Test
    public void testIgnoredStdin() throws IOException {
        File exJar = new File("src/test/resources/ExecuteCommand/TestIngestAndUpdate.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue(dummy.toPath());
        controller.setProperty(ExecuteStreamCommand.WORKING_DIR, "target");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_ARGUMENTS, "-jar;" + jarPath);
        controller.setProperty(ExecuteStreamCommand.IGNORE_STDIN, "true");
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 1);
        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP);
        byte[] byteArray = flowFiles.get(0).toByteArray();
        String result = new String(byteArray);
        assertTrue(Pattern.compile("target:ModifiedResult\r?\n$").matcher(result).find(), "TestIngestAndUpdate.jar should not have received anything to modify");
    }

    @Test
    public void testIgnoredStdinDynamicProperties() throws IOException {
        File exJar = new File("src/test/resources/ExecuteCommand/TestIngestAndUpdate.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue(dummy.toPath());
        controller.setProperty(ExecuteStreamCommand.WORKING_DIR, "target");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.ARGUMENTS_STRATEGY, ExecuteStreamCommand.DYNAMIC_PROPERTY_ARGUMENTS_STRATEGY.getValue());
        PropertyDescriptor dynamicProp1 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.1")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp1, "-jar");
        PropertyDescriptor dynamicProp2 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.2")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp2, jarPath);
        controller.setProperty(ExecuteStreamCommand.IGNORE_STDIN, "true");
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 1);
        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP);
        byte[] byteArray = flowFiles.get(0).toByteArray();
        String result = new String(byteArray);
        assertTrue(Pattern.compile("target:ModifiedResult\r?\n$").matcher(result).find(), "TestIngestAndUpdate.jar should not have received anything to modify");
    }

    @Test
    public void testDynamicEnvironment() throws Exception {
        File exJar = new File("src/test/resources/ExecuteCommand/TestDynamicEnvironment.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.setProperty("NIFI_TEST_1", "testvalue1");
        controller.setProperty("NIFI_TEST_2", "testvalue2");
        controller.enqueue(dummy.toPath());
        controller.setProperty(ExecuteStreamCommand.WORKING_DIR, "target");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_ARGUMENTS, "-jar;" + jarPath);
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 1);
        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP);
        byte[] byteArray = flowFiles.get(0).toByteArray();
        String result = new String(byteArray);
        Set<String> dynamicEnvironmentVariables = new HashSet<>(Arrays.asList(result.split("\r?\n")));
        assertFalse(dynamicEnvironmentVariables.size() < 2, "Should contain at least two environment variables starting with NIFI");
        assertTrue(dynamicEnvironmentVariables.contains("NIFI_TEST_1=testvalue1"), "NIFI_TEST_1 environment variable is missing");
        assertTrue(dynamicEnvironmentVariables.contains("NIFI_TEST_2=testvalue2"), "NIFI_TEST_2 environment variable is missing");
    }

    @Test
    public void testDynamicEnvironmentDynamicProperties() throws Exception {
        File exJar = new File("src/test/resources/ExecuteCommand/TestDynamicEnvironment.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.setProperty("NIFI_TEST_1", "testvalue1");
        controller.setProperty("NIFI_TEST_2", "testvalue2");
        controller.enqueue(dummy.toPath());
        controller.setProperty(ExecuteStreamCommand.WORKING_DIR, "target");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.ARGUMENTS_STRATEGY, ExecuteStreamCommand.DYNAMIC_PROPERTY_ARGUMENTS_STRATEGY.getValue());
        PropertyDescriptor dynamicProp1 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.1")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp1, "-jar");
        PropertyDescriptor dynamicProp2 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.2")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp2, jarPath);
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 1);
        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP);
        byte[] byteArray = flowFiles.get(0).toByteArray();
        String result = new String(byteArray);
        Set<String> dynamicEnvironmentVariables = new HashSet<>(Arrays.asList(result.split("\r?\n")));
        assertFalse(dynamicEnvironmentVariables.size() < 2, "Should contain at least two environment variables starting with NIFI");
        assertTrue(dynamicEnvironmentVariables.contains("NIFI_TEST_1=testvalue1"), "NIFI_TEST_1 environment variable is missing");
        assertTrue(dynamicEnvironmentVariables.contains("NIFI_TEST_2=testvalue2"), "NIFI_TEST_2 environment variable is missing");
    }

    @Test
    public void testSmallEchoPutToAttribute() {
        File dummy = new File("src/test/resources/hello.txt");
        assertTrue(dummy.exists());
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.setProperty(ExecuteStreamCommand.MIME_TYPE, "application/json");
        controller.enqueue("".getBytes());

        if (isWindows()) {
            controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "cmd.exe");
            controller.setProperty(ExecuteStreamCommand.EXECUTION_ARGUMENTS, "/c;echo Hello");
            controller.setProperty(ExecuteStreamCommand.ARG_DELIMITER, ";");
        } else {
            controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "echo");
            controller.setProperty(ExecuteStreamCommand.EXECUTION_ARGUMENTS, "Hello");
        }
        controller.setProperty(ExecuteStreamCommand.IGNORE_STDIN, "true");
        controller.setProperty(ExecuteStreamCommand.PUT_OUTPUT_IN_ATTRIBUTE, "executeStreamCommand.output");

        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 0);

        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP);
        MockFlowFile outputFlowFile = flowFiles.get(0);
        outputFlowFile.assertContentEquals("");
        outputFlowFile.assertAttributeNotExists(CoreAttributes.MIME_TYPE.key());
        String ouput = outputFlowFile.getAttribute("executeStreamCommand.output");
        assertTrue(ouput.startsWith("Hello"));
        assertEquals("0", outputFlowFile.getAttribute("execution.status"));
        assertEquals(isWindows() ? "cmd.exe" : "echo", outputFlowFile.getAttribute("execution.command"));
    }

    @Test
    public void testSmallEchoPutToAttributeDynamicProperties() {
        File dummy = new File("src/test/resources/hello.txt");
        assertTrue(dummy.exists());
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue("".getBytes());
        controller.setProperty(ExecuteStreamCommand.ARGUMENTS_STRATEGY, ExecuteStreamCommand.DYNAMIC_PROPERTY_ARGUMENTS_STRATEGY.getValue());

        if (isWindows()) {
            controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "cmd.exe");
            PropertyDescriptor dynamicProp1 = new PropertyDescriptor.Builder()
                .dynamic(true)
                .name("command.argument.1")
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .build();
            controller.setProperty(dynamicProp1, "/c");
            PropertyDescriptor dynamicProp2 = new PropertyDescriptor.Builder()
                .dynamic(true)
                .name("command.argument.2")
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .build();
            controller.setProperty(dynamicProp2, "echo Hello");
            controller.setProperty(ExecuteStreamCommand.EXECUTION_ARGUMENTS, "/c;echo Hello");
        } else {
            controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "echo");
            PropertyDescriptor dynamicProp1 = new PropertyDescriptor.Builder()
                .dynamic(true)
                .name("command.argument.1")
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .build();
            controller.setProperty(dynamicProp1, "Hello");
        }
        controller.setProperty(ExecuteStreamCommand.IGNORE_STDIN, "true");
        controller.setProperty(ExecuteStreamCommand.PUT_OUTPUT_IN_ATTRIBUTE, "executeStreamCommand.output");

        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 0);

        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP);
        MockFlowFile outputFlowFile = flowFiles.get(0);
        outputFlowFile.assertContentEquals("");
        String ouput = outputFlowFile.getAttribute("executeStreamCommand.output");
        assertTrue(ouput.startsWith("Hello"));
        assertEquals("0", outputFlowFile.getAttribute("execution.status"));
        assertEquals(isWindows() ? "cmd.exe" : "echo", outputFlowFile.getAttribute("execution.command"));
    }

    @Test
    public void testArgumentsWithQuotesFromAttributeDynamicProperties() throws Exception {
        File dummy = new File("src/test/resources/TestJson/json-sample.json");
        assertTrue(dummy.exists());
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);

        Map<String, String> attrs = new HashMap<>();

        String json = FileUtils.readFileToString(dummy, StandardCharsets.UTF_8);
        attrs.put("json.attribute", json);
        controller.enqueue("".getBytes(), attrs);

        controller.setProperty(ExecuteStreamCommand.ARGUMENTS_STRATEGY, ExecuteStreamCommand.DYNAMIC_PROPERTY_ARGUMENTS_STRATEGY.getValue());

        if (isWindows()) {
            controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "cmd.exe");
            PropertyDescriptor dynamicProp1 = new PropertyDescriptor.Builder()
                .dynamic(true)
                .name("command.argument.1")
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .build();
            controller.setProperty(dynamicProp1, "/c");
            PropertyDescriptor dynamicProp2 = new PropertyDescriptor.Builder()
                .dynamic(true)
                .name("command.argument.2")
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .build();
            controller.setProperty(dynamicProp2, "echo");
        } else {
            controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "echo");
        }
        PropertyDescriptor dynamicProp3 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.3")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp3, "${json.attribute}");
        controller.setProperty(ExecuteStreamCommand.IGNORE_STDIN, "true");

        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 1);

        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP);
        MockFlowFile outputFlowFile = flowFiles.get(0);
        String output = new String(outputFlowFile.toByteArray());
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree1 = mapper.readTree(json);
        JsonNode tree2 = mapper.readTree(output);
        assertEquals(tree1, tree2);
        assertEquals("0", outputFlowFile.getAttribute("execution.status"));
        assertEquals(isWindows() ? "cmd.exe" : "echo", outputFlowFile.getAttribute("execution.command"));
    }

    @Test
    public void testExecuteJarPutToAttribute() throws Exception {
        File exJar = new File("src/test/resources/ExecuteCommand/TestSuccess.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue(dummy.toPath());
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_ARGUMENTS, "-jar;" + jarPath);
        controller.setProperty(ExecuteStreamCommand.PUT_OUTPUT_IN_ATTRIBUTE, "executeStreamCommand.output");
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 0);

        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP);
        MockFlowFile outputFlowFile = flowFiles.get(0);
        String result = outputFlowFile.getAttribute("executeStreamCommand.output");
        outputFlowFile.assertContentEquals(dummy);
        assertTrue(Pattern.compile("Test was a success\r?\n").matcher(result).find());
        assertEquals("0", outputFlowFile.getAttribute("execution.status"));
        assertEquals("java", outputFlowFile.getAttribute("execution.command"));
        assertEquals("-jar;", outputFlowFile.getAttribute("execution.command.args").substring(0, 5));
        String attribute = outputFlowFile.getAttribute("execution.command.args");
        String expected = "src" + File.separator + "test" + File.separator + "resources" + File.separator + "ExecuteCommand" + File.separator + "TestSuccess.jar";
        assertEquals(expected, attribute.substring(attribute.length() - expected.length()));
    }

    @Test
    public void testExecuteJarPutToAttributeDynamicProperties() throws Exception {
        File exJar = new File("src/test/resources/ExecuteCommand/TestSuccess.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue(dummy.toPath());
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.ARGUMENTS_STRATEGY, ExecuteStreamCommand.DYNAMIC_PROPERTY_ARGUMENTS_STRATEGY.getValue());
        PropertyDescriptor dynamicProp1 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.1")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp1, "-jar");
        PropertyDescriptor dynamicProp2 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.2")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp2, jarPath);
        controller.setProperty(ExecuteStreamCommand.PUT_OUTPUT_IN_ATTRIBUTE, "executeStreamCommand.output");
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 0);

        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP);
        MockFlowFile outputFlowFile = flowFiles.get(0);
        String result = outputFlowFile.getAttribute("executeStreamCommand.output");
        outputFlowFile.assertContentEquals(dummy);
        assertTrue(Pattern.compile("Test was a success\r?\n").matcher(result).find());
        assertEquals("0", outputFlowFile.getAttribute("execution.status"));
        assertEquals("java", outputFlowFile.getAttribute("execution.command"));
        assertEquals("-jar", outputFlowFile.getAttribute("execution.command.args").substring(0, 4));
        String attribute = outputFlowFile.getAttribute("execution.command.args");
        String expected = "src" + File.separator + "test" + File.separator + "resources" + File.separator + "ExecuteCommand" + File.separator + "TestSuccess.jar";
        assertEquals(expected, attribute.substring(attribute.length() - expected.length()));
    }

    @Test
    public void testExecuteJarToAttributeConfiguration() throws Exception {
        File exJar = new File("src/test/resources/ExecuteCommand/TestSuccess.jar");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue("small test".getBytes());
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_ARGUMENTS, "-jar;" + jarPath);
        controller.setProperty(ExecuteStreamCommand.PUT_ATTRIBUTE_MAX_LENGTH, "10");
        controller.setProperty(ExecuteStreamCommand.PUT_OUTPUT_IN_ATTRIBUTE, "outputDest");
        assertEquals(1, controller.getProcessContext().getAvailableRelationships().size());
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 0);
        controller.assertTransferCount(ExecuteStreamCommand.NONZERO_STATUS_RELATIONSHIP, 0);

        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP);
        MockFlowFile outputFlowFile = flowFiles.get(0);
        outputFlowFile.assertContentEquals("small test".getBytes());
        String result = outputFlowFile.getAttribute("outputDest");
        assertTrue(Pattern.compile("Test was a").matcher(result).find());
        assertEquals("0", outputFlowFile.getAttribute("execution.status"));
        assertEquals("java", outputFlowFile.getAttribute("execution.command"));
        assertEquals("-jar;", outputFlowFile.getAttribute("execution.command.args").substring(0, 5));
        String attribute = outputFlowFile.getAttribute("execution.command.args");
        String expected = "src" + File.separator + "test" + File.separator + "resources" + File.separator + "ExecuteCommand" + File.separator + "TestSuccess.jar";
        assertEquals(expected, attribute.substring(attribute.length() - expected.length()));
    }

    @Test
    public void testExecuteJarToAttributeConfigurationDyanmicProperties() throws Exception {
        File exJar = new File("src/test/resources/ExecuteCommand/TestSuccess.jar");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue("small test".getBytes());
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.ARGUMENTS_STRATEGY, ExecuteStreamCommand.DYNAMIC_PROPERTY_ARGUMENTS_STRATEGY.getValue());
        PropertyDescriptor dynamicProp1 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.1")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp1, "-jar");
        PropertyDescriptor dynamicProp2 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.2")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp2, jarPath);
        controller.setProperty(ExecuteStreamCommand.PUT_ATTRIBUTE_MAX_LENGTH, "10");
        controller.setProperty(ExecuteStreamCommand.PUT_OUTPUT_IN_ATTRIBUTE, "outputDest");
        assertEquals(1, controller.getProcessContext().getAvailableRelationships().size());
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 0);
        controller.assertTransferCount(ExecuteStreamCommand.NONZERO_STATUS_RELATIONSHIP, 0);

        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP);
        MockFlowFile outputFlowFile = flowFiles.get(0);
        outputFlowFile.assertContentEquals("small test".getBytes());
        String result = outputFlowFile.getAttribute("outputDest");
        assertTrue(Pattern.compile("Test was a").matcher(result).find());
        assertEquals("0", outputFlowFile.getAttribute("execution.status"));
        assertEquals("java", outputFlowFile.getAttribute("execution.command"));
        assertEquals("-jar", outputFlowFile.getAttribute("execution.command.args").substring(0, 4));
        String attribute = outputFlowFile.getAttribute("execution.command.args");
        String expected = "src" + File.separator + "test" + File.separator + "resources" + File.separator + "ExecuteCommand" + File.separator + "TestSuccess.jar";
        assertEquals(expected, attribute.substring(attribute.length() - expected.length()));
    }

    @Test
    public void testExecuteIngestAndUpdatePutToAttribute() throws IOException {
        File exJar = new File("src/test/resources/ExecuteCommand/TestIngestAndUpdate.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        File dummy10MBytes = new File("target/10MB.txt");
        byte[] bytes = Files.readAllBytes(dummy.toPath());
        try (FileOutputStream fos = new FileOutputStream(dummy10MBytes)) {
            for (int i = 0; i < 10000; i++) {
                fos.write(bytes, 0, 1000);
            }
        }
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue(dummy10MBytes.toPath());
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_ARGUMENTS, "-jar;" + jarPath);
        controller.setProperty(ExecuteStreamCommand.PUT_OUTPUT_IN_ATTRIBUTE, "outputDest");
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 0);
        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP);
        String result = flowFiles.get(0).getAttribute("outputDest");

        assertTrue(Pattern.compile("nifi-standard-processors:ModifiedResult\r?\n").matcher(result).find());
    }

    @Test
    public void testExecuteIngestAndUpdatePutToAttributeDynamicProperties() throws IOException {
        File exJar = new File("src/test/resources/ExecuteCommand/TestIngestAndUpdate.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        File dummy10MBytes = new File("target/10MB.txt");
        byte[] bytes = Files.readAllBytes(dummy.toPath());
        try (FileOutputStream fos = new FileOutputStream(dummy10MBytes)) {
            for (int i = 0; i < 10000; i++) {
                fos.write(bytes, 0, 1000);
            }
        }
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue(dummy10MBytes.toPath());
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.ARGUMENTS_STRATEGY, ExecuteStreamCommand.DYNAMIC_PROPERTY_ARGUMENTS_STRATEGY.getValue());
        PropertyDescriptor dynamicProp1 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.1")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp1, "-jar");
        PropertyDescriptor dynamicProp2 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.2")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp2, jarPath);
        controller.setProperty(ExecuteStreamCommand.PUT_OUTPUT_IN_ATTRIBUTE, "outputDest");
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 0);
        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP);
        String result = flowFiles.get(0).getAttribute("outputDest");

        assertTrue(Pattern.compile("nifi-standard-processors:ModifiedResult\r?\n").matcher(result).find());
    }

    @Test
    public void testLargePutToAttribute() throws IOException {
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        File dummy10MBytes = new File("target/10MB.txt");
        byte[] bytes = Files.readAllBytes(dummy.toPath());
        try (FileOutputStream fos = new FileOutputStream(dummy10MBytes)) {
            for (int i = 0; i < 10000; i++) {
                fos.write(bytes, 0, 1000);
            }
        }

        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue("".getBytes());
        if (isWindows()) {
            controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "cmd.exe");
            controller.setProperty(ExecuteStreamCommand.EXECUTION_ARGUMENTS, "/c;type " + dummy10MBytes.getAbsolutePath());
            controller.setProperty(ExecuteStreamCommand.ARG_DELIMITER, ";");
        } else {
            controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "cat");
            controller.setProperty(ExecuteStreamCommand.EXECUTION_ARGUMENTS, dummy10MBytes.getAbsolutePath());
        }
        controller.setProperty(ExecuteStreamCommand.IGNORE_STDIN, "true");
        controller.setProperty(ExecuteStreamCommand.PUT_OUTPUT_IN_ATTRIBUTE, "executeStreamCommand.output");
        controller.setProperty(ExecuteStreamCommand.PUT_ATTRIBUTE_MAX_LENGTH, "256");

        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 0);
        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP);

        flowFiles.get(0).assertAttributeEquals("execution.status", "0");
        String result = flowFiles.get(0).getAttribute("executeStreamCommand.output");
        assertTrue(Pattern.compile("a{256}").matcher(result).matches());
    }

    @Test
    public void testLargePutToAttributeDynamicProperties() throws IOException {
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        File dummy10MBytes = new File("target/10MB.txt");
        byte[] bytes = Files.readAllBytes(dummy.toPath());
        try (FileOutputStream fos = new FileOutputStream(dummy10MBytes)) {
            for (int i = 0; i < 10000; i++) {
                fos.write(bytes, 0, 1000);
            }
        }

        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue("".getBytes());
        controller.setProperty(ExecuteStreamCommand.ARGUMENTS_STRATEGY, ExecuteStreamCommand.DYNAMIC_PROPERTY_ARGUMENTS_STRATEGY.getValue());
        if (isWindows()) {
            controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "cmd.exe");
            PropertyDescriptor dynamicProp1 = new PropertyDescriptor.Builder()
                .dynamic(true)
                .name("command.argument.1")
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .build();
            controller.setProperty(dynamicProp1, "/c");
            PropertyDescriptor dynamicProp2 = new PropertyDescriptor.Builder()
                .dynamic(true)
                .name("command.argument.2")
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .build();
            controller.setProperty(dynamicProp2, "type " + dummy10MBytes.getAbsolutePath());
        } else {
            controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "cat");
            PropertyDescriptor dynamicProp1 = new PropertyDescriptor.Builder()
                .dynamic(true)
                .name("command.argument.1")
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .build();
            controller.setProperty(dynamicProp1, dummy10MBytes.getAbsolutePath());
        }
        controller.setProperty(ExecuteStreamCommand.IGNORE_STDIN, "true");
        controller.setProperty(ExecuteStreamCommand.PUT_OUTPUT_IN_ATTRIBUTE, "executeStreamCommand.output");
        controller.setProperty(ExecuteStreamCommand.PUT_ATTRIBUTE_MAX_LENGTH, "256");

        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 0);
        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP);

        flowFiles.get(0).assertAttributeEquals("execution.status", "0");
        String result = flowFiles.get(0).getAttribute("executeStreamCommand.output");
        assertTrue(Pattern.compile("a{256}").matcher(result).matches());
    }

    @Test
    public void testExecuteIngestAndUpdateWithWorkingDirPutToAttribute() throws IOException {
        File exJar = new File("src/test/resources/ExecuteCommand/TestIngestAndUpdate.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue(dummy.toPath());
        controller.setProperty(ExecuteStreamCommand.WORKING_DIR, "target");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.PUT_OUTPUT_IN_ATTRIBUTE, "streamOutput");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_ARGUMENTS, "-jar;" + jarPath);
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP);
        String result = flowFiles.get(0).getAttribute("streamOutput");

        final String quotedSeparator = Pattern.quote(File.separator);
        assertTrue(Pattern.compile(quotedSeparator + "nifi-standard-processors" + quotedSeparator + "target:ModifiedResult\r?\n").matcher(result).find());
    }

    @Test
    public void testExecuteIngestAndUpdateWithWorkingDirPutToAttributeDynamicProperties() throws IOException {
        File exJar = new File("src/test/resources/ExecuteCommand/TestIngestAndUpdate.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue(dummy.toPath());
        controller.setProperty(ExecuteStreamCommand.WORKING_DIR, "target");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.PUT_OUTPUT_IN_ATTRIBUTE, "streamOutput");
        controller.setProperty(ExecuteStreamCommand.ARGUMENTS_STRATEGY, ExecuteStreamCommand.DYNAMIC_PROPERTY_ARGUMENTS_STRATEGY.getValue());
        PropertyDescriptor dynamicProp1 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.1")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp1, "-jar");
        PropertyDescriptor dynamicProp2 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.2")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp2, jarPath);
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP);
        String result = flowFiles.get(0).getAttribute("streamOutput");

        final String quotedSeparator = Pattern.quote(File.separator);
        assertTrue(Pattern.compile(quotedSeparator + "nifi-standard-processors" + quotedSeparator + "target:ModifiedResult\r?\n").matcher(result).find());
    }

    @Test
    public void testIgnoredStdinPutToAttribute() throws IOException {
        File exJar = new File("src/test/resources/ExecuteCommand/TestIngestAndUpdate.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue(dummy.toPath());
        controller.setProperty(ExecuteStreamCommand.WORKING_DIR, "target");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_ARGUMENTS, "-jar;" + jarPath);
        controller.setProperty(ExecuteStreamCommand.IGNORE_STDIN, "true");
        controller.setProperty(ExecuteStreamCommand.PUT_OUTPUT_IN_ATTRIBUTE, "executeStreamCommand.output");
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP);
        String result = flowFiles.get(0).getAttribute("executeStreamCommand.output");
        assertTrue(Pattern.compile("target:ModifiedResult\r?\n?").matcher(result).find(),
                "TestIngestAndUpdate.jar should not have received anything to modify");
    }

    @Test
    public void testIgnoredStdinPutToAttributeDynamicProperties() throws IOException {
        File exJar = new File("src/test/resources/ExecuteCommand/TestIngestAndUpdate.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue(dummy.toPath());
        controller.setProperty(ExecuteStreamCommand.WORKING_DIR, "target");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.ARGUMENTS_STRATEGY, ExecuteStreamCommand.DYNAMIC_PROPERTY_ARGUMENTS_STRATEGY.getValue());
        PropertyDescriptor dynamicProp1 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.1")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp1, "-jar");
        PropertyDescriptor dynamicProp2 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.2")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp2, jarPath);
        controller.setProperty(ExecuteStreamCommand.IGNORE_STDIN, "true");
        controller.setProperty(ExecuteStreamCommand.PUT_OUTPUT_IN_ATTRIBUTE, "executeStreamCommand.output");
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP);
        String result = flowFiles.get(0).getAttribute("executeStreamCommand.output");
        assertTrue(Pattern.compile("target:ModifiedResult\r?\n?").matcher(result).find(),
                "TestIngestAndUpdate.jar should not have received anything to modify");
    }

    @Test
    public void testDynamicEnvironmentPutToAttribute() throws Exception {
        File exJar = new File("src/test/resources/ExecuteCommand/TestDynamicEnvironment.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.setProperty("NIFI_TEST_1", "testvalue1");
        controller.setProperty("NIFI_TEST_2", "testvalue2");
        controller.enqueue(dummy.toPath());
        controller.setProperty(ExecuteStreamCommand.WORKING_DIR, "target");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_ARGUMENTS, "-jar;" + jarPath);
        controller.setProperty(ExecuteStreamCommand.PUT_OUTPUT_IN_ATTRIBUTE, "executeStreamCommand.output");
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP);
        String result = flowFiles.get(0).getAttribute("executeStreamCommand.output");
        Set<String> dynamicEnvironmentVariables = new HashSet<>(Arrays.asList(result.split("\r?\n")));
        assertFalse(dynamicEnvironmentVariables.size() < 2, "Should contain at least two environment variables starting with NIFI");
        assertTrue(dynamicEnvironmentVariables.contains("NIFI_TEST_1=testvalue1"), "NIFI_TEST_1 environment variable is missing");
        assertTrue(dynamicEnvironmentVariables.contains("NIFI_TEST_2=testvalue2"), "NIFI_TEST_2 environment variable is missing");
    }

    @Test
    public void testDynamicEnvironmentPutToAttributeDynamicProperties() throws Exception {
        File exJar = new File("src/test/resources/ExecuteCommand/TestDynamicEnvironment.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.setProperty("NIFI_TEST_1", "testvalue1");
        controller.setProperty("NIFI_TEST_2", "testvalue2");
        controller.enqueue(dummy.toPath());
        controller.setProperty(ExecuteStreamCommand.WORKING_DIR, "target");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.ARGUMENTS_STRATEGY, ExecuteStreamCommand.DYNAMIC_PROPERTY_ARGUMENTS_STRATEGY.getValue());
        PropertyDescriptor dynamicProp1 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.1")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp1, "-jar");
        PropertyDescriptor dynamicProp2 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.2")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp2, jarPath);
        controller.setProperty(ExecuteStreamCommand.PUT_OUTPUT_IN_ATTRIBUTE, "executeStreamCommand.output");
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);
        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP);
        String result = flowFiles.get(0).getAttribute("executeStreamCommand.output");
        Set<String> dynamicEnvironmentVariables = new HashSet<>(Arrays.asList(result.split("\r?\n")));
        assertFalse(dynamicEnvironmentVariables.size() < 2, "Should contain at least two environment variables starting with NIFI");
        assertTrue(dynamicEnvironmentVariables.contains("NIFI_TEST_1=testvalue1"), "NIFI_TEST_1 environment variable is missing");
        assertTrue(dynamicEnvironmentVariables.contains("NIFI_TEST_2=testvalue2"), "NIFI_TEST_2 environment variable is missing");
    }

    @Test
    public void testQuotedArguments() throws Exception {
        List<String> args = ArgumentUtils.splitArgs("echo -n \"arg1 arg2 arg3\"", ' ');
        assertEquals(3, args.size());
        args = ArgumentUtils.splitArgs("echo;-n;\"arg1 arg2 arg3\"", ';');
        assertEquals(3, args.size());
    }

    @Test
    public void testInvalidDelimiter() throws Exception {
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "echo");
        controller.assertValid();
        controller.setProperty(ExecuteStreamCommand.ARG_DELIMITER, "foo");
        controller.assertNotValid();
        controller.setProperty(ExecuteStreamCommand.ARG_DELIMITER, "f");
        controller.assertValid();
    }

    @Test
    public void testExecuteJarPutToAttributeBadPath() throws Exception {
        File exJar = new File("src/test/resources/ExecuteCommand/noSuchFile.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue(dummy.toPath());
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.EXECUTION_ARGUMENTS, "-jar;" + jarPath);
        controller.setProperty(ExecuteStreamCommand.PUT_OUTPUT_IN_ATTRIBUTE, "executeStreamCommand.output");
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 0);
        controller.assertTransferCount(ExecuteStreamCommand.NONZERO_STATUS_RELATIONSHIP, 0);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);

        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP);
        MockFlowFile outputFlowFile = flowFiles.get(0);
        String result = outputFlowFile.getAttribute("executeStreamCommand.output");
        outputFlowFile.assertContentEquals(dummy);
        assertTrue(result.isEmpty()); // java -jar with bad path only prints to standard error not standard out
        assertEquals("1", outputFlowFile.getAttribute("execution.status")); // java -jar with bad path exits with code 1
        assertEquals("java", outputFlowFile.getAttribute("execution.command"));
        assertEquals("-jar;", outputFlowFile.getAttribute("execution.command.args").substring(0, 5));
        String attribute = outputFlowFile.getAttribute("execution.command.args");
        String expected = "src" + File.separator + "test" + File.separator + "resources" + File.separator + "ExecuteCommand" + File.separator + "noSuchFile.jar";
        assertEquals(expected, attribute.substring(attribute.length() - expected.length()));
    }

    @Test
    public void testExecuteJarPutToAttributeBadPathDynamicProperties() throws Exception {
        File exJar = new File("src/test/resources/ExecuteCommand/noSuchFile.jar");
        File dummy = new File("src/test/resources/ExecuteCommand/1000bytes.txt");
        String jarPath = exJar.getAbsolutePath();
        exJar.setExecutable(true);
        final TestRunner controller = TestRunners.newTestRunner(ExecuteStreamCommand.class);
        controller.enqueue(dummy.toPath());
        controller.setProperty(ExecuteStreamCommand.EXECUTION_COMMAND, "java");
        controller.setProperty(ExecuteStreamCommand.ARGUMENTS_STRATEGY, ExecuteStreamCommand.DYNAMIC_PROPERTY_ARGUMENTS_STRATEGY.getValue());
        PropertyDescriptor dynamicProp1 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.1")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp1, "-jar");
        PropertyDescriptor dynamicProp2 = new PropertyDescriptor.Builder()
            .dynamic(true)
            .name("command.argument.2")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
        controller.setProperty(dynamicProp2, jarPath);
        controller.setProperty(ExecuteStreamCommand.PUT_OUTPUT_IN_ATTRIBUTE, "executeStreamCommand.output");
        controller.run(1);
        controller.assertTransferCount(ExecuteStreamCommand.OUTPUT_STREAM_RELATIONSHIP, 0);
        controller.assertTransferCount(ExecuteStreamCommand.NONZERO_STATUS_RELATIONSHIP, 0);
        controller.assertTransferCount(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP, 1);

        List<MockFlowFile> flowFiles = controller.getFlowFilesForRelationship(ExecuteStreamCommand.ORIGINAL_RELATIONSHIP);
        MockFlowFile outputFlowFile = flowFiles.get(0);
        String result = outputFlowFile.getAttribute("executeStreamCommand.output");
        outputFlowFile.assertContentEquals(dummy);
        assertTrue(result.isEmpty()); // java -jar with bad path only prints to standard error not standard out
        assertEquals("1", outputFlowFile.getAttribute("execution.status")); // java -jar with bad path exits with code 1
        assertEquals("java", outputFlowFile.getAttribute("execution.command"));
        assertEquals("-jar", outputFlowFile.getAttribute("execution.command.args").substring(0, 4));
        String attribute = outputFlowFile.getAttribute("execution.command.args");
        String expected = "src" + File.separator + "test" + File.separator + "resources" + File.separator + "ExecuteCommand" + File.separator + "noSuchFile.jar";
        assertEquals(expected, attribute.substring(attribute.length() - expected.length()));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().startsWith("windows");
    }
}
