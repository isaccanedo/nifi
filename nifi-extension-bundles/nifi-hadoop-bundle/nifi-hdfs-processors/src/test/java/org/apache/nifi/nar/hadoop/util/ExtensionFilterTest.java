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
package org.apache.nifi.nar.hadoop.util;

import org.apache.hadoop.fs.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExtensionFilterTest {

    @Test
    public void testValid() {
        // given
        final ExtensionFilter testSubject = new ExtensionFilter("txt");
        final Path path = new Path("test.txt");

        // when
        final boolean result = testSubject.accept(path);

        // then
        assertTrue(result);
    }

    @Test
    public void testValidWhenUppercase() {
        // given
        final ExtensionFilter testSubject = new ExtensionFilter("txt");
        final Path path = new Path("test.TXT");

        // when
        final boolean result = testSubject.accept(path);

        // then
        assertTrue(result);
    }

    @Test
    public void testInvalidWhenDifferentExtension() {
        // given
        final ExtensionFilter testSubject = new ExtensionFilter("txt");
        final Path path = new Path("test.json");

        // when
        final boolean result = testSubject.accept(path);

        // then
        assertFalse(result);
    }

    @Test
    public void testInvalidWhenMistypedExtension() {
        // given
        final ExtensionFilter testSubject = new ExtensionFilter("txt");
        final Path path = new Path("test.ttxt");

        // when
        final boolean result = testSubject.accept(path);

        // then
        assertFalse(result);
    }

    @Test
    public void testInvalidWhenMultipleExtension() {
        // given
        final ExtensionFilter testSubject = new ExtensionFilter("txt");
        final Path path = new Path("test.txt.json");

        // when
        final boolean result = testSubject.accept(path);

        // then
        assertFalse(result);
    }

    @Test
    public void testFolder() {
        // given
        final ExtensionFilter testSubject = new ExtensionFilter("ttxt");
        final Path path = new Path("testtxt");

        // when
        final boolean result = testSubject.accept(path);

        // then
        assertFalse(result);
    }

}