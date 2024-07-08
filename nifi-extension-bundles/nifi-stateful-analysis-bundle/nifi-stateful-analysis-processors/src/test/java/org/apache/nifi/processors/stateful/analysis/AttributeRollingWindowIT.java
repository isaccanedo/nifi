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

package org.apache.nifi.processors.stateful.analysis;

import org.apache.commons.math3.stat.descriptive.moment.Variance;
import org.apache.commons.math3.util.Precision;
import org.apache.nifi.components.state.Scope;
import org.apache.nifi.processor.ProcessSessionFactory;
import org.apache.nifi.state.MockStateManager;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.OS;
import org.opentest4j.AssertionFailedError;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.apache.nifi.processors.stateful.analysis.AttributeRollingWindow.REL_FAILED_SET_STATE;
import static org.apache.nifi.processors.stateful.analysis.AttributeRollingWindow.ROLLING_WINDOW_COUNT_KEY;
import static org.apache.nifi.processors.stateful.analysis.AttributeRollingWindow.ROLLING_WINDOW_MEAN_KEY;
import static org.apache.nifi.processors.stateful.analysis.AttributeRollingWindow.ROLLING_WINDOW_STDDEV_KEY;
import static org.apache.nifi.processors.stateful.analysis.AttributeRollingWindow.ROLLING_WINDOW_VALUE_KEY;
import static org.apache.nifi.processors.stateful.analysis.AttributeRollingWindow.ROLLING_WINDOW_VARIANCE_KEY;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AttributeRollingWindowIT {

    private static final double EPSILON = 0.000001d; // "Error" threshold for floating-point arithmetic
    @Test
    public void testFailureDueToBadAttribute() {
        final TestRunner runner = TestRunners.newTestRunner(AttributeRollingWindow.class);

        runner.setProperty(AttributeRollingWindow.VALUE_TO_TRACK, "${value}");
        runner.setProperty(AttributeRollingWindow.TIME_WINDOW, "3 sec");


        final Map<String, String> attributes = new HashMap<>();
        attributes.put("value", "bad");


        runner.enqueue("1".getBytes(), attributes);
        runner.run(1);

        runner.assertAllFlowFilesTransferred(AttributeRollingWindow.REL_FAILURE);
    }

    @Test
    public void testStateFailures() throws IOException {
        final TestRunner runner = TestRunners.newTestRunner(AttributeRollingWindow.class);
        MockStateManager mockStateManager = runner.getStateManager();
        final AttributeRollingWindow processor = (AttributeRollingWindow) runner.getProcessor();
        final ProcessSessionFactory processSessionFactory = runner.getProcessSessionFactory();

        runner.setProperty(AttributeRollingWindow.VALUE_TO_TRACK, "${value}");
        runner.setProperty(AttributeRollingWindow.TIME_WINDOW, "3 sec");

        processor.onScheduled(runner.getProcessContext());

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("value", "1");

        mockStateManager.setFailOnStateGet(Scope.LOCAL, true);

        runner.enqueue(new byte[0], attributes);
        processor.onTrigger(runner.getProcessContext(), processSessionFactory.createSession());

        runner.assertQueueNotEmpty();

        mockStateManager.setFailOnStateGet(Scope.LOCAL, false);
        mockStateManager.setFailOnStateSet(Scope.LOCAL, true);

        processor.onTrigger(runner.getProcessContext(), processSessionFactory.createSession());

        runner.assertQueueEmpty();

        runner.assertAllFlowFilesTransferred(AttributeRollingWindow.REL_FAILED_SET_STATE, 1);
        MockFlowFile mockFlowFile = runner.getFlowFilesForRelationship(REL_FAILED_SET_STATE).get(0);
        mockFlowFile.assertAttributeNotExists(ROLLING_WINDOW_VALUE_KEY);
        mockFlowFile.assertAttributeNotExists(ROLLING_WINDOW_COUNT_KEY);
        mockFlowFile.assertAttributeNotExists(ROLLING_WINDOW_MEAN_KEY);
        mockFlowFile.assertAttributeNotExists(ROLLING_WINDOW_VARIANCE_KEY);
        mockFlowFile.assertAttributeNotExists(ROLLING_WINDOW_STDDEV_KEY);
    }

    private boolean isWindowsEnvironment() {
        return System.getProperty("os.name").toLowerCase().startsWith("windows");
    }

    @Test
    public void testBasic() throws InterruptedException {
        final TestRunner runner = TestRunners.newTestRunner(AttributeRollingWindow.class);

        runner.setProperty(AttributeRollingWindow.VALUE_TO_TRACK, "${value}");
        runner.setProperty(AttributeRollingWindow.TIME_WINDOW, "300 ms");


        final Map<String, String> attributes = new HashMap<>();
        attributes.put("value", "1");


        runner.enqueue("1".getBytes(), attributes);
        runner.run(1);
        runner.assertAllFlowFilesTransferred(AttributeRollingWindow.REL_SUCCESS, 1);
        MockFlowFile flowFile = runner.getFlowFilesForRelationship(AttributeRollingWindow.REL_SUCCESS).get(0);
        runner.clearTransferState();
        flowFile.assertAttributeEquals(ROLLING_WINDOW_VALUE_KEY, "1.0");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_COUNT_KEY, "1");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_MEAN_KEY, "1.0");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_VARIANCE_KEY, "0.0");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_STDDEV_KEY, "0.0");

        runner.enqueue("2".getBytes(), attributes);
        runner.run(1);
        runner.assertAllFlowFilesTransferred(AttributeRollingWindow.REL_SUCCESS, 1);
         flowFile = runner.getFlowFilesForRelationship(AttributeRollingWindow.REL_SUCCESS).get(0);
        runner.clearTransferState();
        flowFile.assertAttributeEquals(ROLLING_WINDOW_VALUE_KEY, "2.0");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_COUNT_KEY, "2");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_MEAN_KEY, "1.0");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_VARIANCE_KEY, "0.0");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_STDDEV_KEY, "0.0");

        Thread.sleep(500L);

        runner.enqueue("2".getBytes(), attributes);
        runner.run(1);
        runner.assertAllFlowFilesTransferred(AttributeRollingWindow.REL_SUCCESS, 1);
        flowFile = runner.getFlowFilesForRelationship(AttributeRollingWindow.REL_SUCCESS).get(0);
        runner.clearTransferState();
        flowFile.assertAttributeEquals(ROLLING_WINDOW_VALUE_KEY, "1.0");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_COUNT_KEY, "1");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_MEAN_KEY, "1.0");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_VARIANCE_KEY, "0.0");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_STDDEV_KEY, "0.0");

    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testVerifyCount() throws InterruptedException {
        final TestRunner runner = TestRunners.newTestRunner(AttributeRollingWindow.class);

        runner.setProperty(AttributeRollingWindow.VALUE_TO_TRACK, "${value}");
        runner.setProperty(AttributeRollingWindow.TIME_WINDOW, "10 sec");

        MockFlowFile flowFile;

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("value", "1");
        for (int i = 1; i < 61; i++) {
            runner.enqueue(String.valueOf(i).getBytes(), attributes);
            runner.run();

            flowFile = runner.getFlowFilesForRelationship(AttributeRollingWindow.REL_SUCCESS).get(0);
            runner.clearTransferState();
            Double value = (double) i;
            Double mean = value / i;

            flowFile.assertAttributeEquals(ROLLING_WINDOW_VALUE_KEY, String.valueOf(value));
            flowFile.assertAttributeEquals(ROLLING_WINDOW_COUNT_KEY, String.valueOf(i));
            flowFile.assertAttributeEquals(ROLLING_WINDOW_MEAN_KEY, String.valueOf(mean));
            flowFile.assertAttributeEquals(ROLLING_WINDOW_VARIANCE_KEY, "0.0");
            flowFile.assertAttributeEquals(ROLLING_WINDOW_STDDEV_KEY, "0.0");
            Thread.sleep(10L);
        }

        runner.setProperty(AttributeRollingWindow.VALUE_TO_TRACK, "${value}");
        runner.setProperty(AttributeRollingWindow.SUB_WINDOW_LENGTH, "500 ms");
        runner.setProperty(AttributeRollingWindow.TIME_WINDOW, "10 sec");

        for (int i = 1; i < 10; i++) {
            runner.enqueue(String.valueOf(i).getBytes(), attributes);

            runner.run();

            flowFile = runner.getFlowFilesForRelationship(AttributeRollingWindow.REL_SUCCESS).get(0);
            runner.clearTransferState();
            Double value = (double) i;
            Double mean = value / i;

            flowFile.assertAttributeEquals(ROLLING_WINDOW_VALUE_KEY, String.valueOf(Double.valueOf(i)));
            flowFile.assertAttributeEquals(ROLLING_WINDOW_COUNT_KEY, String.valueOf(i));
            flowFile.assertAttributeEquals(ROLLING_WINDOW_MEAN_KEY, String.valueOf(mean));
            flowFile.assertAttributeEquals(ROLLING_WINDOW_VARIANCE_KEY, "0.0");
            flowFile.assertAttributeEquals(ROLLING_WINDOW_STDDEV_KEY, "0.0");

            Thread.sleep(10L);
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testVerifyVarianceAndStandardDeviation() throws InterruptedException {
        final TestRunner runner = TestRunners.newTestRunner(AttributeRollingWindow.class);

        runner.setProperty(AttributeRollingWindow.VALUE_TO_TRACK, "${value}");
        runner.setProperty(AttributeRollingWindow.TIME_WINDOW, "10 sec");

        MockFlowFile flowFile;

        final Map<String, String> attributes = new HashMap<>();
        final Variance variance = new Variance();
        double sum = 0.0D;
        for (int i = 1; i < 61; i++) {
            attributes.put("value", String.valueOf(i));
            runner.enqueue(new byte[]{}, attributes);
            runner.run();

            flowFile = runner.getFlowFilesForRelationship(AttributeRollingWindow.REL_SUCCESS).get(0);
            runner.clearTransferState();
            sum += i;
            double mean = sum / i;
            variance.increment(i);

            flowFile.assertAttributeEquals(ROLLING_WINDOW_VALUE_KEY, String.valueOf(sum));
            flowFile.assertAttributeEquals(ROLLING_WINDOW_COUNT_KEY, String.valueOf(i));
            assertTrue(Precision.equals(mean, Double.parseDouble(flowFile.getAttribute(ROLLING_WINDOW_MEAN_KEY)), EPSILON));
            try {
                assertTrue(Precision.equals(variance.getResult(), Double.parseDouble(flowFile.getAttribute(ROLLING_WINDOW_VARIANCE_KEY)), EPSILON));
            } catch (AssertionFailedError ae) {
                System.err.println("Error at " + i + ": " + variance.getResult() + " != " + Double.parseDouble(flowFile.getAttribute(ROLLING_WINDOW_VARIANCE_KEY)));
            }
            assertTrue(Precision.equals(Math.sqrt(variance.getResult()), Double.parseDouble(flowFile.getAttribute(ROLLING_WINDOW_STDDEV_KEY)), EPSILON));
            Thread.sleep(10L);
        }
    }

    @EnabledIfSystemProperty(named = "nifi.test.unstable",
            matches = "true",
            disabledReason = "this test is too unstable in terms of timing on different size/types of testing envs")
    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testMicroBatching() throws InterruptedException {
        final TestRunner runner = TestRunners.newTestRunner(AttributeRollingWindow.class);

        runner.setProperty(AttributeRollingWindow.VALUE_TO_TRACK, "${value}");
        runner.setProperty(AttributeRollingWindow.SUB_WINDOW_LENGTH, "500 ms");
        runner.setProperty(AttributeRollingWindow.TIME_WINDOW, "1 sec");


        final Map<String, String> attributes = new HashMap<>();
        attributes.put("value", "2");

        runner.enqueue("1".getBytes(), attributes);
        runner.run(1);
        runner.assertAllFlowFilesTransferred(AttributeRollingWindow.REL_SUCCESS, 1);
        MockFlowFile flowFile = runner.getFlowFilesForRelationship(AttributeRollingWindow.REL_SUCCESS).get(0);
        runner.clearTransferState();
        flowFile.assertAttributeEquals(ROLLING_WINDOW_VALUE_KEY, "2.0");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_COUNT_KEY, "1");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_MEAN_KEY, "2.0");

        Thread.sleep(200L);

        runner.enqueue("2".getBytes(), attributes);
        runner.run(1);
        runner.assertAllFlowFilesTransferred(AttributeRollingWindow.REL_SUCCESS, 1);
        flowFile = runner.getFlowFilesForRelationship(AttributeRollingWindow.REL_SUCCESS).get(0);
        runner.clearTransferState();
        flowFile.assertAttributeEquals(ROLLING_WINDOW_VALUE_KEY, "4.0");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_COUNT_KEY, "2");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_MEAN_KEY, "2.0");


        Thread.sleep(300L);

        runner.enqueue("2".getBytes(), attributes);
        runner.run(1);
        runner.assertAllFlowFilesTransferred(AttributeRollingWindow.REL_SUCCESS, 1);
        flowFile = runner.getFlowFilesForRelationship(AttributeRollingWindow.REL_SUCCESS).get(0);
        runner.clearTransferState();
        flowFile.assertAttributeEquals(ROLLING_WINDOW_VALUE_KEY, "6.0");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_COUNT_KEY, "3");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_MEAN_KEY, "2.0");

        Thread.sleep(200L);
        runner.enqueue("2".getBytes(), attributes);
        runner.run(1);
        runner.assertAllFlowFilesTransferred(AttributeRollingWindow.REL_SUCCESS, 1);
        flowFile = runner.getFlowFilesForRelationship(AttributeRollingWindow.REL_SUCCESS).get(0);
        runner.clearTransferState();
        flowFile.assertAttributeEquals(ROLLING_WINDOW_VALUE_KEY, "8.0");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_COUNT_KEY, "4");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_MEAN_KEY, "2.0");

        Thread.sleep(300L);

        runner.enqueue("2".getBytes(), attributes);
        runner.run(1);
        runner.assertAllFlowFilesTransferred(AttributeRollingWindow.REL_SUCCESS, 1);
        flowFile = runner.getFlowFilesForRelationship(AttributeRollingWindow.REL_SUCCESS).get(0);
        runner.clearTransferState();
        flowFile.assertAttributeEquals(ROLLING_WINDOW_VALUE_KEY, "6.0");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_COUNT_KEY, "3");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_MEAN_KEY, "2.0");

        runner.enqueue("2".getBytes(), attributes);
        runner.run(1);
        runner.assertAllFlowFilesTransferred(AttributeRollingWindow.REL_SUCCESS, 1);
        flowFile = runner.getFlowFilesForRelationship(AttributeRollingWindow.REL_SUCCESS).get(0);
        runner.clearTransferState();
        flowFile.assertAttributeEquals(ROLLING_WINDOW_VALUE_KEY, "8.0");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_COUNT_KEY, "4");
        flowFile.assertAttributeEquals(ROLLING_WINDOW_MEAN_KEY, "2.0");
    }

}