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

package org.apache.nifi.record.path.filter;

import org.apache.nifi.record.path.FieldValue;
import org.apache.nifi.record.path.RecordPathEvaluationContext;
import org.apache.nifi.record.path.StandardFieldValue;
import org.apache.nifi.record.path.paths.RecordPathSegment;
import org.apache.nifi.serialization.record.RecordField;
import org.apache.nifi.serialization.record.RecordFieldType;

import java.util.stream.Stream;

public abstract class FunctionFilter implements RecordPathFilter {
    private final RecordPathSegment recordPath;

    protected FunctionFilter(final RecordPathSegment recordPath) {
        this.recordPath = recordPath;
    }

    @Override
    public Stream<FieldValue> filter(final RecordPathEvaluationContext context, final boolean invert) {
        return recordPath.evaluate(context)
            .filter(fv -> invert ? !test(fv, context) : test(fv, context));
    }

    @Override
    public Stream<FieldValue> mapToBoolean(final RecordPathEvaluationContext context) {
        return recordPath.evaluate(context)
            .map(fv -> new StandardFieldValue(test(fv, context), new RecordField("<function>", RecordFieldType.BOOLEAN.getDataType()), null));
    }

    protected abstract boolean test(FieldValue fieldValue, final RecordPathEvaluationContext context);
}
