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
package org.apache.nifi.util.locale;

import org.junit.jupiter.api.Test;

import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Testing of the test suite environment {@link java.util.Locale}.  The locales specified
 * in ".github/workflows/ci-workflow.yml" are exercised.  This test is inert when run in alternate locales.
 */
public class TestLocaleOfTestSuite {

    /**
     * Test behaviors associated with non-standard ".github/workflows/ci-workflow.yml" {@link java.util.Locale} "en-AU".
     */
    @Test
    public void testLocaleCI_EN_AU() {
        final Locale locale = Locale.getDefault();
        assumeTrue(locale.toLanguageTag().equals("en-AU"));

        assertEquals("e", DecimalFormatSymbols.getInstance(locale).getExponentSeparator());
        assertEquals("1,000", NumberFormat.getInstance(locale).format(1000));
    }

    /**
     * Test behaviors associated with ".github/workflows/ci-workflow.yml" {@link java.util.Locale#JAPAN}.
     */
    @Test
    public void testLocaleCI_JA_JP() {
        final Locale locale = Locale.getDefault();
        assumeTrue(locale.toLanguageTag().equals("ja-JP"));

        assertEquals("E", DecimalFormatSymbols.getInstance(locale).getExponentSeparator());
        assertEquals("1,000", NumberFormat.getInstance(locale).format(1000));
    }

    /**
     * Test behaviors associated with ".github/workflows/ci-workflow.yml" {@link java.util.Locale#FRANCE}.
     */
    @Test
    public void testLocaleCI_FR_FR() {
        final Locale locale = Locale.getDefault();
        assumeTrue(locale.toLanguageTag().equals("fr-FR"));

        assertEquals("E", DecimalFormatSymbols.getInstance(locale).getExponentSeparator());
    }
}
