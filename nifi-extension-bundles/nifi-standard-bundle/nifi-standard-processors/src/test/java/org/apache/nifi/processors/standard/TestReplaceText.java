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

import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestReplaceText {

    public TestRunner getRunner() {
        TestRunner runner = TestRunners.newTestRunner(ReplaceText.class);

        /**
         * we have to disable validation of expression language because the scope of the evaluation
         * for the search value depends of another property (the evaluation mode). If not disabling
         * the validation, it'll throw an error about the eval
         */
        runner.setValidateExpressionUsage(false);

        return runner;
    }

    @Test
    public void testLiteralReplaceWithExpressionLanguageInSearchEntireText() {
        final TestRunner runner = getRunner();
        runner.enqueue("Me, you, and the other", Collections.singletonMap("search.value", "you, and"));
        runner.setProperty(ReplaceText.SEARCH_VALUE, "Me, ${search.value}");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "\"Replacement\"");
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.ENTIRE_TEXT);
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.LITERAL_REPLACE.getValue());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("\"Replacement\" the other");
    }

    @Test
    public void testLiteralReplaceWithExpressionLanguageInReplacementEntireText() {
        final TestRunner runner = getRunner();
        runner.enqueue("Me, you, and the other", Collections.singletonMap("replacement.value", "us"));
        runner.setProperty(ReplaceText.SEARCH_VALUE, "Me, you,");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "With ${replacement.value}");
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.ENTIRE_TEXT);
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.LITERAL_REPLACE.getValue());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("With us and the other");
    }

    @Test
    public void testLiteralReplaceWithExpressionLanguageInSearchLineByLine() {
        final TestRunner runner = getRunner();
        runner.enqueue("Me, you, and the other", Collections.singletonMap("search.value", "you, and"));
        runner.setProperty(ReplaceText.SEARCH_VALUE, "Me, ${search.value}");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "\"Replacement\"");
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.LITERAL_REPLACE.getValue());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("\"Replacement\" the other");
    }

    @Test
    public void testLiteralReplaceWithExpressionLanguageInReplacementLineByLine() {
        final TestRunner runner = getRunner();
        runner.enqueue("Me, you, and the other", Collections.singletonMap("replacement.value", "us"));
        runner.setProperty(ReplaceText.SEARCH_VALUE, "Me, you,");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "With ${replacement.value}");
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.LITERAL_REPLACE.getValue());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("With us and the other");
    }

    @Test
    public void testConfigurationCornerCase() throws IOException {
        final TestRunner runner = getRunner();
        runner.run();
        runner.enqueue(Paths.get("src/test/resources/hello.txt"));
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(Paths.get("src/test/resources/hello.txt"));
    }

    @Test
    public void testIterativeRegexReplace() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "\"([a-z]+?)\":\"(.*?)\"");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "\"${'$1':toUpper()}\":\"$2\"");

        runner.enqueue("{\"name\":\"Smith\",\"middle\":\"nifi\",\"firstname\":\"John\"}");
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("{\"NAME\":\"Smith\",\"MIDDLE\":\"nifi\",\"FIRSTNAME\":\"John\"}");
    }

    @Test
    public void testIterativeRegexReplaceLineByLine() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "\"([a-z]+?)\":\"(.*?)\"");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "\"${'$1':toUpper()}\":\"$2\"");

        runner.enqueue("{\"name\":\"Smith\",\"middle\":\"nifi\",\"firstname\":\"John\"}");
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("{\"NAME\":\"Smith\",\"MIDDLE\":\"nifi\",\"FIRSTNAME\":\"John\"}");
    }


    @Test
    public void testSimple() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "ell");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "lle");

        runner.enqueue(Paths.get("src/test/resources/hello.txt"));
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("Hlleo, World!".getBytes("UTF-8"));
    }

    @Test
    public void testWithEscaped$InReplacement() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(?s:^.*$)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "a\\$b");

        runner.enqueue("a$a,b,c,d");
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("a\\$b".getBytes("UTF-8"));
    }

    @Test
    public void testWithUnEscaped$InReplacement() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(?s:^.*$)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "a$b");

        runner.enqueue("a$a,b,c,d");
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("a$b".getBytes("UTF-8"));
    }

    @Test
    public void testPrependSimple() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "TEST");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.PREPEND);

        runner.enqueue(Paths.get("src/test/resources/hello.txt"));
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("TESTHello, World!".getBytes("UTF-8"));
    }

    @Test
    public void testPrependLineByLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "_");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.PREPEND);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);

        runner.enqueue("hello\nthere\nmadam".getBytes());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("_hello\n_there\n_madam".getBytes("UTF-8"));
    }

    @Test
    public void testPrependFirstLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "_");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.PREPEND);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.LINE_BY_LINE_EVALUATION_MODE, ReplaceText.FIRST_LINE);

        runner.enqueue("hello\nthere\nmadam".getBytes());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("_hello\nthere\nmadam".getBytes("UTF-8"));
    }

    @Test
    public void testPrependLastLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "_");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.PREPEND);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.LINE_BY_LINE_EVALUATION_MODE, ReplaceText.LAST_LINE);

        runner.enqueue("hello\nthere\nmadam".getBytes());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("hello\nthere\n_madam".getBytes("UTF-8"));
    }

    @Test
    public void testPrependExceptFirstLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "_");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.PREPEND);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.LINE_BY_LINE_EVALUATION_MODE, ReplaceText.EXCEPT_FIRST_LINE);

        runner.enqueue("hello\nthere\nmadam".getBytes());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("hello\n_there\n_madam".getBytes("UTF-8"));
    }


    @Test
    public void testPrependExceptLastLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "_");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.PREPEND);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.LINE_BY_LINE_EVALUATION_MODE, ReplaceText.EXCEPT_LAST_LINE);

        runner.enqueue("hello\nthere\nmadam".getBytes());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("_hello\n_there\nmadam".getBytes("UTF-8"));
    }

    @Test
    public void testAppendSimple() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "TEST");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.APPEND);

        runner.enqueue(Paths.get("src/test/resources/hello.txt"));
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("Hello, World!TEST".getBytes("UTF-8"));
    }

    @Test
    public void testAppendWithCarriageReturn() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "!");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.APPEND);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);

        runner.enqueue("hello\rthere\rsir".getBytes());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("hello!\rthere!\rsir!");
    }


    @Test
    public void testAppendFirstLineWithCarriageReturn() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "!");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.APPEND);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.LINE_BY_LINE_EVALUATION_MODE, ReplaceText.FIRST_LINE);

        runner.enqueue("hello\rthere\rsir".getBytes());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("hello!\rthere\rsir");
    }

    @Test
    public void testAppendExceptFirstLineWithCarriageReturn() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "!");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.APPEND);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.LINE_BY_LINE_EVALUATION_MODE, ReplaceText.EXCEPT_FIRST_LINE);

        runner.enqueue("hello\rthere\rsir".getBytes());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("hello\rthere!\rsir!");
    }

    @Test
    public void testAppendLastLineWithCarriageReturn() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "!");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.APPEND);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.LINE_BY_LINE_EVALUATION_MODE, ReplaceText.LAST_LINE);

        runner.enqueue("hello\rthere\rsir".getBytes());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("hello\rthere\rsir!");
    }

    @Test
    public void testAppendExceptLastLineWithCarriageReturn() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "!");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.APPEND);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.LINE_BY_LINE_EVALUATION_MODE, ReplaceText.EXCEPT_LAST_LINE);

        runner.enqueue("hello\rthere\rsir".getBytes());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("hello!\rthere!\rsir");
    }

    @Test
    public void testAppendWithNewLine() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "!");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.APPEND);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);

        runner.enqueue("hello\nthere\nsir".getBytes());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("hello!\nthere!\nsir!");
    }

    @Test
    public void testAppendWithCarriageReturnNewLine() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "!");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.APPEND);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);

        runner.enqueue("hello\r\nthere\r\nsir".getBytes());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("hello!\r\nthere!\r\nsir!");
    }

    @Test
    public void testAppendFirstLineWithCarriageReturnNewLine() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "!");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.APPEND);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.LINE_BY_LINE_EVALUATION_MODE, ReplaceText.FIRST_LINE);

        runner.enqueue("hello\r\nthere\r\nsir".getBytes());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("hello!\r\nthere\r\nsir");
    }


    @Test
    public void testAppendLastLineWithCarriageReturnNewLine() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "!");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.APPEND);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.LINE_BY_LINE_EVALUATION_MODE, ReplaceText.LAST_LINE);

        runner.enqueue("hello\r\nthere\r\nsir".getBytes());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("hello\r\nthere\r\nsir!");
    }


    @Test
    public void testAppendExceptFistLineWithCarriageReturnNewLine() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "!");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.APPEND);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.LINE_BY_LINE_EVALUATION_MODE, ReplaceText.EXCEPT_FIRST_LINE);

        runner.enqueue("hello\r\nthere\r\nsir".getBytes());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("hello\r\nthere!\r\nsir!");
    }


    @Test
    public void testAppendExceptLastLineWithCarriageReturnNewLine() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "!");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.APPEND);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.LINE_BY_LINE_EVALUATION_MODE, ReplaceText.EXCEPT_LAST_LINE);


        runner.enqueue("hello\r\nthere\r\nsir".getBytes());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("hello!\r\nthere!\r\nsir");
    }

    @Test
    public void testLiteralSimple() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "ell");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "lle");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.LITERAL_REPLACE);

        runner.enqueue(Paths.get("src/test/resources/hello.txt"));
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("Hlleo, World!".getBytes("UTF-8"));
    }

    @Test
    public void testLiteralBackReference() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "ell");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "[$1]");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.LITERAL_REPLACE);

        runner.enqueue(Paths.get("src/test/resources/hello.txt"));
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("H[$1]o, World!");
    }

    @Test
    public void testLiteral() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, ".ell.");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "test");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.LITERAL_REPLACE);

        runner.enqueue(Paths.get("src/test/resources/hello.txt"));
        runner.run();
        runner.enqueue("H.ell.o, World! .ell.".getBytes());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 2);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("Hello, World!");
        final MockFlowFile out2 = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(1);
        out2.assertContentEquals("Htesto, World! test");
    }

    @Test
    public void testBackReference() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(ell)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "[$1]");

        runner.enqueue(Paths.get("src/test/resources/hello.txt"));
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("H[ell]o, World!");
    }

    @Test
    public void testBackRefFollowedByNumbers() throws IOException {
        final TestRunner runner = getRunner();
        String expected = "Hell23o, World!";
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(ell)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "$123");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("abc", "notSupported");
        runner.enqueue(Paths.get("src/test/resources/hello.txt"), attributes);
        runner.run();
        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        final String actual = new String(out.toByteArray(), StandardCharsets.UTF_8);
        System.out.println(actual);
        assertEquals(expected, actual);
    }

    @Test
    public void testBackRefWithNoCapturingGroup() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "ell");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "$0123");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("abc", "notSupported");
        runner.enqueue(Paths.get("src/test/resources/hello.txt"), attributes);
        runner.run();
        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        final String actual = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertEquals("Hell123o, World!", actual);
    }

    @Test
    public void testReplacementWithExpressionLanguage() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "${replaceKey}");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "GoodBye");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("replaceKey", "H.*o");
        runner.enqueue(Paths.get("src/test/resources/hello.txt"), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("Hello, World!");
    }

    @Test
    public void testReplacementWithExpressionLanguageIsEscaped() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(ell)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "[${abc}]");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("abc", "$1");
        runner.enqueue(Paths.get("src/test/resources/hello.txt"), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("H[$1]o, World!");
    }

    @Test
    public void testRegexWithExpressionLanguage() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "${replaceKey}");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "${replaceValue}");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("replaceKey", "Hello");
        attributes.put("replaceValue", "Good-bye");
        runner.enqueue(Paths.get("src/test/resources/hello.txt"), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("Good-bye, World!");
    }

    @Test
    public void testRegexWithExpressionLanguageIsEscaped() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "${replaceKey}");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "${replaceValue}");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("replaceKey", "H.*o");
        attributes.put("replaceValue", "Good-bye");
        runner.enqueue(Paths.get("src/test/resources/hello.txt"), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("Hello, World!");
    }

    @Test
    public void testBackReferenceWithTooLargeOfIndexIsEscaped() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(ell)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "$1$2");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("replaceKey", "H.*o");
        attributes.put("replaceValue", "Good-bye");
        runner.enqueue(Paths.get("src/test/resources/hello.txt"), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("Hell$2o, World!");
    }

    @Test
    public void testBackReferenceWithInvalidReferenceIsEscaped() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(ell)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "$d");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("replaceKey", "H.*o");
        attributes.put("replaceValue", "Good-bye");
        runner.enqueue(Paths.get("src/test/resources/hello.txt"), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("H$do, World!");
    }

    @Test
    public void testEscapingDollarSign() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(ell)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "\\$1");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("replaceKey", "H.*o");
        attributes.put("replaceValue", "Good-bye");
        runner.enqueue(Paths.get("src/test/resources/hello.txt"), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("H$1o, World!");
    }

    @Test
    public void testReplaceWithEmptyString() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(ell)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "");

        runner.enqueue(Paths.get("src/test/resources/hello.txt"));
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("Ho, World!");
    }

    @Test
    public void testWithNoMatch() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "Z");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "Morning");

        runner.enqueue(Paths.get("src/test/resources/hello.txt"));
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("Hello, World!");
    }

    @Test
    public void testWithMultipleMatches() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "l");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "R");

        runner.enqueue(Paths.get("src/test/resources/hello.txt"));
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("HeRRo, WorRd!");
    }

    @Test
    public void testAttributeToContent() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, ".*");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "${abc}");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("abc", "Good");
        runner.enqueue(Paths.get("src/test/resources/hello.txt"), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("Good");
    }

    @Test
    public void testRoutesToFailureIfTooLarge() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "[123]");
        runner.setProperty(ReplaceText.MAX_BUFFER_SIZE, "1 b");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "${abc}");
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.ENTIRE_TEXT);

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("abc", "Good");
        runner.enqueue(Paths.get("src/test/resources/hello.txt"), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_FAILURE, 1);
    }

    @Test
    public void testRoutesToFailureIfLineTooLarge() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "[123]");
        runner.setProperty(ReplaceText.MAX_BUFFER_SIZE, "5 kb");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "${abc}");
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("abc", "Good");
        runner.enqueue(Paths.get("src/test/resources/TestReplaceTextLineByLine/apacheLicenseOnOneLine.txt"), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_FAILURE, 1);
    }

    @Test
    public void testRoutesToSuccessIfTooLargeButRegexIsDotAsterisk() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, ".*");
        runner.setProperty(ReplaceText.MAX_BUFFER_SIZE, "1 b");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "${abc}");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("abc", "Good");
        runner.enqueue(Paths.get("src/test/resources/hello.txt"), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("Good");
    }

    @Test
    public void testProblematicCase1() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, ".*");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "${filename}\t${now():format(\"yyyy/MM/dd'T'HHmmss'Z'\")}\t${fileSize}\n");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("filename", "abc.txt");
        runner.enqueue(Paths.get("src/test/resources/hello.txt"), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        final String outContent = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(outContent.startsWith("abc.txt\t"));
        System.out.println(outContent);
        assertTrue(outContent.endsWith("13\n"));
    }

    @Test
    public void testGetExistingContent() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(?s)(^.*)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "attribute header\n\n${filename}\n\ndata header\n\n$1\n\nfooter");
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.ENTIRE_TEXT);

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("filename", "abc.txt");
        runner.enqueue("Hello\nWorld!".getBytes(), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        final String outContent = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(outContent.equals("attribute header\n\nabc.txt\n\ndata header\n\nHello\nWorld!\n\nfooter"));
        System.out.println(outContent);
    }

    @Test
    public void testReplaceWithinCurlyBraces() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, ".+");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "{ ${filename} }");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("filename", "abc.txt");
        runner.enqueue("Hello".getBytes(), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("{ abc.txt }");
    }

    @Test
    public void testDefaultReplacement() {
        final String defaultValue = "default-replacement-value";

        // leave the default regex settings
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, defaultValue);

        final Map<String, String> attributes = new HashMap<>();
        runner.enqueue("original-text".getBytes(StandardCharsets.UTF_8), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(defaultValue);
    }

    @Test
    public void testDefaultMultilineReplacement() {
        final String defaultValue = "default-replacement-value";

        // leave the default regex settings
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, defaultValue);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.ENTIRE_TEXT);

        final Map<String, String> attributes = new HashMap<>();
        runner.enqueue(("original-text-line-1" + System.lineSeparator() + "original-text-line-2").getBytes(StandardCharsets.UTF_8), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(defaultValue);
    }

    /* Line by Line */

    @Test
    public void testSimpleLineByLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "odo");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "ood");

        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")));
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/food.txt")));
    }


    @Test
    public void testZeroByteContentFileLineByLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "odo");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "ood");

        final File zeroByteFile = File.createTempFile("zeroByte", ".txt");
        runner.enqueue(translateNewLines(zeroByteFile.getPath()));
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(zeroByteFile.getPath()));
    }


    @Test
    public void testPrependSimpleLineByLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.PREPEND);
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "TEST ");

        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")));
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/PrependLineByLineTest.txt")));
    }

    @Test
    public void testAppendSimpleLineByLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.APPEND);
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, " TEST");

        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")));
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/AppendLineByLineTest.txt")));
    }

    @Test
    public void testAppendEndlineCR() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "TEST");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.APPEND);

        runner.enqueue("Hello \rWorld \r".getBytes());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("Hello TEST\rWorld TEST\r".getBytes("UTF-8"));
    }

    @Test
    public void testAppendEndlineCRLF() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "TEST");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.APPEND);

        runner.enqueue("Hello \r\nWorld \r\n".getBytes());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("Hello TEST\r\nWorld TEST\r\n".getBytes("UTF-8"));
    }

    @Test
    public void testSimpleLiteral() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "odo");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "ood");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.LITERAL_REPLACE);

        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")));
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/food.txt")));
    }

    @Test
    public void testLiteralBackReferenceLineByLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "jo");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "[$1]");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.LITERAL_REPLACE);

        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")));

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/cu[$1]_Po[$1].txt")));
    }

    @Test
    public void testLiteralLineByLine() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, ".ell.");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "test");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.LITERAL_REPLACE);

        runner.enqueue("H.ell.o, World! .ell. \n .ell. .ell.".getBytes());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("Htesto, World! test \n test test");
    }

    @Test
    public void testBackReferenceLineByLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(DODO)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "[$1]");

        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")));
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/[DODO].txt")));
    }

    @Test
    public void testReplacementWithExpressionLanguageIsEscapedLineByLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(jo)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "[${abc}]");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("abc", "$1");
        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/cu[$1]_Po[$1].txt")));
    }

    @Test
    public void testRegexWithExpressionLanguageLineByLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "${replaceKey}");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "${replaceValue}");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("replaceKey", "Riley");
        attributes.put("replaceValue", "Spider");
        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/Spider.txt")));
    }

    @Test
    public void testRegexWithExpressionLanguageIsEscapedLineByLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "${replaceKey}");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "${replaceValue}");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("replaceKey", "R.*y");
        attributes.put("replaceValue", "Spider");
        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/testFile.txt")));
    }

    @Test
    public void testBackReferenceWithTooLargeOfIndexIsEscapedLineByLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(lu)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "$1$2");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("replaceKey", "R.*y");
        attributes.put("replaceValue", "Spiderman");
        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/Blu$2e_clu$2e.txt")));
    }


    @Test
    public void testBackReferenceWithTooLargeOfIndexIsEscapedFirstLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.LINE_BY_LINE_EVALUATION_MODE, ReplaceText.FIRST_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(H)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "$1$2");

        final Map<String, String> attributes = new HashMap<>();
        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/ReplaceFirstLine.txt")));
    }


    @Test
    public void testBackReferenceWithTooLargeOfIndexIsEscapedLastLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.LINE_BY_LINE_EVALUATION_MODE, ReplaceText.LAST_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(O)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "$1$2");

        final Map<String, String> attributes = new HashMap<>();
        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/ReplaceLastLine.txt")));
    }



    @Test
    public void testBackReferenceWithTooLargeOfIndexIsEscapedExceptFirstLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.LINE_BY_LINE_EVALUATION_MODE, ReplaceText.EXCEPT_FIRST_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(H)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "$1$2");

        final Map<String, String> attributes = new HashMap<>();
        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/ReplaceExceptFirstLine.txt")));
    }




    @Test
    public void testBackReferenceWithTooLargeOfIndexIsEscapedExceptLastLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.LINE_BY_LINE_EVALUATION_MODE, ReplaceText.EXCEPT_LAST_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(O)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "$1$2");

        final Map<String, String> attributes = new HashMap<>();
        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/ReplaceExceptLastLine.txt")));
    }



    @Test
    public void testLiteralBackReferenceFistLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.LINE_BY_LINE_EVALUATION_MODE, ReplaceText.FIRST_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "H");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "[$1]");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.LITERAL_REPLACE);

        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")));

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/LiteralReplaceFirstLine.txt")));
    }




    @Test
    public void testLiteralBackReferenceExceptFirstLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.LINE_BY_LINE_EVALUATION_MODE, ReplaceText.EXCEPT_FIRST_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "H");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "[$1]");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.LITERAL_REPLACE);

        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")));

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/LiteralReplaceExceptFirstLine.txt")));
    }


    @Test
    public void testLiteralBackReferenceLastLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.LINE_BY_LINE_EVALUATION_MODE, ReplaceText.LAST_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "O");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "[$1]");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.LITERAL_REPLACE);

        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")));

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/LiteralReplaceLastLine.txt")));
    }


    @Test
    public void testLiteralBackReferenceExceptLastLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.LINE_BY_LINE_EVALUATION_MODE, ReplaceText.EXCEPT_LAST_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "O");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "[$1]");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.LITERAL_REPLACE);

        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")));

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/LiteralReplaceExceptLastLine.txt")));
    }

    @Test
    public void testBackReferenceWithInvalidReferenceIsEscapedLineByLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(ew)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "$d");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("replaceKey", "H.*o");
        attributes.put("replaceValue", "Good-bye");
        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/D$d_h$d.txt")));
    }

    @Test
    public void testEscapingDollarSignLineByLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(DO)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "\\$1");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("replaceKey", "H.*o");
        attributes.put("replaceValue", "Good-bye");
        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/$1$1.txt")));
    }

    @Test
    public void testReplaceWithEmptyStringLineByLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(jo)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "");

        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")));
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/cu_Po.txt")));
    }

    @Test
    public void testWithNoMatchLineByLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "Z");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "Morning");

        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")));
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/testFile.txt")));
    }

    @Test
    public void testWithMultipleMatchesLineByLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "l");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "R");

        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")));
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(translateNewLines(new File("src/test/resources/TestReplaceTextLineByLine/BRue_cRue_RiRey.txt")));
    }

    @Test
    public void testAttributeToContentLineByLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, ".*");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "${abc}");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("abc", "Good");
        runner.enqueue(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt"), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("Good\nGood\nGood\nGood\nGood\nGood\nGood\nGood\nGood\nGood\nGood");
    }

    @Test
    public void testAttributeToContentWindows() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, ".*");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "${abc}");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("abc", "Good");
        runner.enqueue("<<<HEADER>>>\r\n<<BODY>>\r\n<<<FOOTER>>>\r".getBytes(), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("Good\r\nGood\r\nGood\r");
    }

    @Test
    public void testProblematicCase1LineByLine() throws IOException {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, ".*");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "${filename}\t${now():format(\"yyyy/MM/dd'T'HHmmss'Z'\")}\t${fileSize}\n");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("filename", "abc.txt");
        runner.enqueue(translateNewLines(Paths.get("src/test/resources/TestReplaceTextLineByLine/testFile.txt")), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        final String outContent = translateNewLines(new String(out.toByteArray(), StandardCharsets.UTF_8));
        assertTrue(outContent.startsWith("abc.txt\t"));
        System.out.println(outContent);
        assertTrue(outContent.endsWith("193\n") || outContent.endsWith("203\r\n"));
    }

    @Test
    public void testGetExistingContentLineByLine() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(?s)(^.*)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "attribute header\n\n${filename}\n\ndata header\n\n$1\n\nfooter\n");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("filename", "abc.txt");
        runner.enqueue("Hello\nWorld!".getBytes(), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        final String outContent = new String(out.toByteArray(), StandardCharsets.UTF_8);
        System.out.println(outContent);
        final String expectedContent = "attribute header\n\nabc.txt\n\ndata header\n\nHello\n\n\nfooter\n"
                + "attribute header\n\nabc.txt\n\ndata header\n\nWorld!\n\nfooter\n";
        assertEquals(expectedContent, outContent);
    }

    @Test
    public void testCapturingGroupInExpressionLanguage() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(.*?),(.*?),(\\d+.*)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "$1,$2,${ '$3':toDate('ddMMMyyyy'):format('yyyy/MM/dd') }");

        final String csvIn =
              "2006,10-01-2004,10may2004\n"
            + "2007,15-05-2006,10jun2005\r\n"
            + "2009,8-8-2008,10aug2008";
        final String expectedCsvOut =
            "2006,10-01-2004,2004/05/10\n"
          + "2007,15-05-2006,2005/06/10\r\n"
          + "2009,8-8-2008,2008/08/10";

        runner.enqueue(csvIn.getBytes());

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(expectedCsvOut);
    }

    @Test
    public void testCapturingGroupInExpressionLanguage2() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(.*)/(.*?).jpg");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "$1/${ '$2':substring(0,1) }.png");

        final String csvIn =
              "1,2,3,https://123.jpg,email@mydomain.com\n"
            + "3,2,1,https://321.jpg,other.email@mydomain.com";
        final String expectedCsvOut =
            "1,2,3,https://1.png,email@mydomain.com\n"
          + "3,2,1,https://3.png,other.email@mydomain.com";

        runner.enqueue(csvIn.getBytes());

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(expectedCsvOut);
    }

    @Test
    public void testAlwaysReplaceEntireText() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.ENTIRE_TEXT);
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.ALWAYS_REPLACE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "i do not exist anywhere in the text");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "${filename}");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("filename", "abc.txt");
        runner.enqueue("Hello\nWorld!".getBytes(), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("abc.txt");
    }

    @Test
    public void testAlwaysReplaceLineByLine() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.ALWAYS_REPLACE);
        runner.setProperty(ReplaceText.SEARCH_VALUE, "i do not exist anywhere in the text");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "${filename}");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("filename", "abc.txt");
        runner.enqueue("Hello\nWorld!\r\ntoday!\n".getBytes(), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("abc.txt\nabc.txt\r\nabc.txt\n");
    }

    @Test
    public void testRegexWithBadCaptureGroup() {
        // Test the old Default Regex and with a custom Replacement Value that should fail because the
        // Perl regex "(?s:^.*$)" must be written "(?s)(^.*$)" in Java for there to be a capture group.
        //      private static final String DEFAULT_REGEX = "(?s:^.*$)";
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(?s:^.*$)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "${'$1':toUpper()}"); // should uppercase group but there is none
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.REGEX_REPLACE);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.ENTIRE_TEXT);

        runner.enqueue("testing\n123".getBytes());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("");
    }

    @Test
    public void testRegexWithGoodCaptureGroup() {
        // Test the new Default Regex and with a custom Replacement Values that should succeed.
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(?s)(^.*$)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "${'$1':toUpper()}"); // will uppercase group with good Java regex
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.REGEX_REPLACE);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.ENTIRE_TEXT);

        runner.enqueue("testing\n123".getBytes());
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("TESTING\n123");
    }

    @Test
    public void testRegexNoCaptureDefaultReplacement() {
        // Test the old Default Regex and new Default Regex with the default replacement.  This should fail
        // because the regex does not create a capture group.
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(?s:^.*$)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "$1");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.REGEX_REPLACE);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.ENTIRE_TEXT);

        runner.enqueue("testing\n123".getBytes());
        final AssertionError e = assertThrows(AssertionError.class, () -> {
            runner.run();
        });
        assertInstanceOf(IndexOutOfBoundsException.class, e.getCause());
        assertTrue(e.getMessage().contains("java.lang.IndexOutOfBoundsException: No group 1"));
    }

    @Test
    public void testProcessorConfigurationRegexNotValid() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(?<!\\),*");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "hello");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.REGEX_REPLACE);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.ENTIRE_TEXT);
        runner.assertNotValid();

        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.LITERAL_REPLACE);
        runner.assertValid();
        runner.enqueue("(?<!\\),*".getBytes());
        runner.run();
        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("hello");

        runner.setProperty(ReplaceText.SEARCH_VALUE, "");
        runner.assertNotValid();

        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.APPEND);
        runner.assertValid();
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.PREPEND);
        runner.assertValid();
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.ALWAYS_REPLACE);
        runner.assertValid();
    }

    @Test
    public void testSurroundWithEntireText() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.SURROUND);
        runner.setProperty(ReplaceText.PREPEND_TEXT, "<pre>");
        runner.setProperty(ReplaceText.APPEND_TEXT, "<post>");
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.ENTIRE_TEXT);

        final String input = "Hello\nThere\nHow are you\nToday?";
        runner.enqueue(input);
        runner.run();
        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);

        final MockFlowFile output = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        output.assertContentEquals("<pre>" + input + "<post>");
    }

    @Test
    public void testSurroundLineByLine() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.SURROUND);
        runner.setProperty(ReplaceText.PREPEND_TEXT, "<pre>");
        runner.setProperty(ReplaceText.APPEND_TEXT, "<post>");
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.LINE_BY_LINE);

        final String input = "Hello\nThere\nHow are you\nToday?";
        runner.enqueue(input);
        runner.run();
        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);

        final MockFlowFile output = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        output.assertContentEquals("<pre>Hello<post>\n<pre>There<post>\n<pre>How are you<post>\n<pre>Today?<post>");
    }


    @Test
    public void testBackReferenceEscapeWithRegexReplaceUsingEL() {
        final TestRunner runner = getRunner();

        runner.setProperty(ReplaceText.SEARCH_VALUE, "(?s)(^.*$)");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "${'$1':toUpper()}");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.REGEX_REPLACE);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.ENTIRE_TEXT);
        runner.assertValid();

        runner.enqueue("wo$rd".getBytes());
        runner.run();
        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals("WO$RD");

        runner.enqueue("wo$1rd".getBytes());
        runner.run();
        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 2);
        out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(1);
        out.assertContentEquals("WO$1RD");

        runner.enqueue("wo$1r$2d".getBytes());
        runner.run();
        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 3);
        out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(2);
        out.assertContentEquals("WO$1R$2D");
    }

    @Test
    public void testSubstituteVariablesWithEvaluationModeEntireText() {
        testSubstituteVariables("Line1: ${var1}\nLine2: ${var2}", "Line1: foo\nLine2: bar", ReplaceText.ENTIRE_TEXT, createAttributesMap());
    }

    @Test
    public void testSubstituteVariablesWithEvaluationModeLineByLine() {
        testSubstituteVariables("Line1: ${var1}\nLine2: ${var2}", "Line1: foo\nLine2: bar", ReplaceText.LINE_BY_LINE, createAttributesMap());
    }

    @Test
    public void testSubstituteVariablesWhenVariableValueMissing() {
        testSubstituteVariables("Line1: ${var1}\nLine2: ${var2}", "Line1: ${var1}\nLine2: ${var2}", ReplaceText.ENTIRE_TEXT, Collections.emptyMap());
    }

    @Test
    public void testSubstituteVariablesWhenVariableReferenceEscaped() {
        testSubstituteVariables("Line1: $${var1}\nLine2: $${var2}", "Line1: ${var1}\nLine2: ${var2}", ReplaceText.ENTIRE_TEXT, createAttributesMap());
    }

    @Test
    public void testSubstituteVariablesWhenVariableNameEmpty() {
        testSubstituteVariables("Line1: ${}\nLine2: ${}", "Line1: ${}\nLine2: ${}", ReplaceText.ENTIRE_TEXT, createAttributesMap());
    }

    private void testSubstituteVariables(String inputContent, String expectedContent, String evaluationMode, Map<String, String> attributesMap) {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, evaluationMode);
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.SUBSTITUTE_VARIABLES.getValue());
        runner.enqueue(inputContent, attributesMap);
        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_SUCCESS, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_SUCCESS).get(0);
        out.assertContentEquals(expectedContent);
    }

    private Map<String, String> createAttributesMap() {
        final Map<String, String> attributesMap = new HashMap<>();
        attributesMap.put("var1", "foo");
        attributesMap.put("var2", "bar");
        return attributesMap;
    }

    /*
     * A repeated alternation regex such as (A|B)* can lead to StackOverflowError
     * on large input strings.
     */
    @Test
    public void testForStackOverflow() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "New text");
        runner.setProperty(ReplaceText.REPLACEMENT_STRATEGY, ReplaceText.REGEX_REPLACE);
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.ENTIRE_TEXT);
        runner.setProperty(ReplaceText.MAX_BUFFER_SIZE, "10 MB");
        runner.setProperty(ReplaceText.SEARCH_VALUE, "(?s)(^(A|B)*$)");
        runner.assertValid();

        char[] data = new char[1_000_000];
        Arrays.fill(data, 'A');
        runner.enqueue(new String(data));

        runner.run();

        // we want the large file to fail, rather than rollback and yield
        runner.assertAllFlowFilesTransferred(ReplaceText.REL_FAILURE, 1);
    }

    /**
     * Related to
     * <a href="https://issues.apache.org/jira/browse/NIFI-5761">NIFI-5761</a>. It
     * verifies that if a runtime exception is raised during replace text
     * evaluation, it sends the error to failure relationship.
     */
    @Test
    public void testWithInvalidExpression() {
        final TestRunner runner = getRunner();
        runner.setProperty(ReplaceText.EVALUATION_MODE, ReplaceText.ENTIRE_TEXT);
        runner.setProperty(ReplaceText.SEARCH_VALUE, ".*");
        runner.setProperty(ReplaceText.REPLACEMENT_VALUE, "${date:toDate(\"yyyy/MM/dd\")}");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("date", "12");
        runner.enqueue("hi", attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(ReplaceText.REL_FAILURE, 1);
        final MockFlowFile out = runner.getFlowFilesForRelationship(ReplaceText.REL_FAILURE).get(0);
        final String outContent = translateNewLines(new String(out.toByteArray(), StandardCharsets.UTF_8));
        assertEquals("hi", outContent);
    }

    private String translateNewLines(final File file) throws IOException {
        return translateNewLines(file.toPath());
    }

    private String translateNewLines(final Path path) throws IOException {
        final byte[] data = Files.readAllBytes(path);
        final String text = new String(data, StandardCharsets.UTF_8);
        return translateNewLines(text);
    }

    private String translateNewLines(final String text) {
        final String lineSeparator = System.getProperty("line.separator");
        final Pattern pattern = Pattern.compile("\n", Pattern.MULTILINE);
        final String translated = pattern.matcher(text).replaceAll(lineSeparator);
        return translated;
    }
}