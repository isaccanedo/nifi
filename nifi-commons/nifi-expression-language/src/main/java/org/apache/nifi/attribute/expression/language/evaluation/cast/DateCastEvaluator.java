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
package org.apache.nifi.attribute.expression.language.evaluation.cast;

import org.apache.nifi.attribute.expression.language.EvaluationContext;
import org.apache.nifi.attribute.expression.language.evaluation.DateEvaluator;
import org.apache.nifi.attribute.expression.language.evaluation.DateQueryResult;
import org.apache.nifi.attribute.expression.language.evaluation.Evaluator;
import org.apache.nifi.attribute.expression.language.evaluation.NumberQueryResult;
import org.apache.nifi.attribute.expression.language.evaluation.QueryResult;
import org.apache.nifi.attribute.expression.language.evaluation.StringQueryResult;
import org.apache.nifi.attribute.expression.language.exception.AttributeExpressionLanguageException;
import org.apache.nifi.attribute.expression.language.exception.AttributeExpressionLanguageParsingException;
import org.apache.nifi.expression.AttributeExpression.ResultType;
import org.apache.nifi.util.FormatUtils;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateCastEvaluator extends DateEvaluator {

    public static final String DATE_TO_STRING_FORMAT = "EEE MMM dd HH:mm:ss zzz yyyy";
    public static final DateTimeFormatter DATE_TO_STRING_FORMATTER = FormatUtils.prepareLenientCaseInsensitiveDateTimeFormatter(DATE_TO_STRING_FORMAT);
    public static final Pattern DATE_TO_STRING_PATTERN = Pattern.compile("(?:[a-zA-Z]{3} ){2}\\d{2} \\d{2}\\:\\d{2}\\:\\d{2} (?:.*?) \\d{4}");

    public static final String ALTERNATE_FORMAT_WITHOUT_MILLIS = "yyyy/MM/dd HH:mm:ss";
    public static final String ALTERNATE_FORMAT_WITH_MILLIS = "yyyy/MM/dd HH:mm:ss.SSS";
    public static final DateTimeFormatter ALTERNATE_FORMATTER_WITHOUT_MILLIS = FormatUtils.prepareLenientCaseInsensitiveDateTimeFormatter(ALTERNATE_FORMAT_WITHOUT_MILLIS);
    public static final DateTimeFormatter ALTERNATE_FORMATTER_WITH_MILLIS = FormatUtils.prepareLenientCaseInsensitiveDateTimeFormatter(ALTERNATE_FORMAT_WITH_MILLIS);
    public static final Pattern ALTERNATE_PATTERN = Pattern.compile("\\d{4}/\\d{2}/\\d{2} \\d{2}\\:\\d{2}\\:\\d{2}(\\.\\d{3})?");

    public static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    private final Evaluator<?> subjectEvaluator;

    public DateCastEvaluator(final Evaluator<?> subjectEvaluator) {
        if (subjectEvaluator.getResultType() == ResultType.BOOLEAN) {
            throw new AttributeExpressionLanguageParsingException("Cannot implicitly convert Data Type " + subjectEvaluator.getResultType() + " to " + ResultType.DATE);
        }

        this.subjectEvaluator = subjectEvaluator;
    }

    @Override
    public QueryResult<Date> evaluate(final EvaluationContext evaluationContext) {
        final QueryResult<?> result = subjectEvaluator.evaluate(evaluationContext);
        if (result.getValue() == null) {
            return new DateQueryResult(null);
        }

        switch (result.getResultType()) {
            case DATE:
                return (DateQueryResult) result;
            case STRING:
                final String value = ((StringQueryResult) result).getValue().trim();
                if (DATE_TO_STRING_PATTERN.matcher(value).matches()) {
                    try {
                        final Date date = Date.from(DATE_TO_STRING_FORMATTER.parse(value, Instant::from));
                        return new DateQueryResult(date);
                    } catch (final DateTimeParseException pe) {
                        final String details = "Format: '" + DATE_TO_STRING_FORMAT + "' Value: '" + value + "'";
                        throw new AttributeExpressionLanguageException("Could not parse date using " + details, pe);
                    }
                } else if (NUMBER_PATTERN.matcher(value).matches()) {
                    return new DateQueryResult(new Date(Long.valueOf(value)));
                } else {
                    final Matcher altMatcher = ALTERNATE_PATTERN.matcher(value);
                    if (altMatcher.matches()) {
                        final String millisValue = altMatcher.group(1);

                        final DateTimeFormatter formatter;
                        if (millisValue == null) {
                            formatter = ALTERNATE_FORMATTER_WITHOUT_MILLIS;
                        } else {
                            formatter = ALTERNATE_FORMATTER_WITH_MILLIS;
                        }

                        try {
                            final Date date = Date.from(FormatUtils.parseToInstant(formatter, value));
                            return new DateQueryResult(date);
                        } catch (final DateTimeParseException pe) {
                            throw new AttributeExpressionLanguageException("Could not parse input as date", pe);
                        }
                    } else {
                        throw new AttributeExpressionLanguageException("Could not implicitly convert input to DATE: " + value);
                    }
                }
            case WHOLE_NUMBER:
                return new DateQueryResult(new Date((Long) result.getValue()));
            case DECIMAL:
                Double resultDouble = (Double) result.getValue();
                return new DateQueryResult(new Date(resultDouble.longValue()));
            case NUMBER:
                final Number numberValue = ((NumberQueryResult) result).getValue();
                return new DateQueryResult(new Date(numberValue.longValue()));
            default:
                return new DateQueryResult(null);
        }
    }

    @Override
    public Evaluator<?> getSubjectEvaluator() {
        return subjectEvaluator;
    }

}
