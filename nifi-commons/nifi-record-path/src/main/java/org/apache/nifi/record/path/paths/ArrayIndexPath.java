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

package org.apache.nifi.record.path.paths;

import java.util.stream.Stream;

import org.apache.nifi.record.path.ArrayIndexFieldValue;
import org.apache.nifi.record.path.FieldValue;
import org.apache.nifi.record.path.RecordPathEvaluationContext;
import org.apache.nifi.record.path.util.Filters;
import org.apache.nifi.serialization.record.DataType;
import org.apache.nifi.serialization.record.RecordField;
import org.apache.nifi.serialization.record.RecordFieldType;
import org.apache.nifi.serialization.record.type.ArrayDataType;

public class ArrayIndexPath extends RecordPathSegment {
    private final int index;

    ArrayIndexPath(final int index, final RecordPathSegment parent, final boolean absolute) {
        super("[" + index + "]", parent, absolute);
        this.index = index;
    }

    @Override
    public Stream<FieldValue> evaluate(final RecordPathEvaluationContext context) {
        final Stream<FieldValue> parentResult = getParentPath().evaluate(context);

        return parentResult
            .filter(Filters.fieldTypeFilter(RecordFieldType.ARRAY))
            .filter(fieldValue -> fieldValue.getValue() != null && ((Object[]) fieldValue.getValue()).length > getArrayIndex(((Object[]) fieldValue.getValue()).length))
            .map(fieldValue -> {
                final ArrayDataType arrayDataType = (ArrayDataType) fieldValue.getField().getDataType();
                final DataType elementDataType = arrayDataType.getElementType();
                final RecordField arrayField = new RecordField(fieldValue.getField().getFieldName(), elementDataType);
                final Object[] values = (Object[]) fieldValue.getValue();
                final int arrayIndex = getArrayIndex(values.length);
                final RecordField elementField = new RecordField(arrayField.getFieldName(), elementDataType);
                final FieldValue result = new ArrayIndexFieldValue(values[arrayIndex], elementField, fieldValue, arrayIndex);
                return result;
            });
    }

    private int getArrayIndex(final int arrayLength) {
        return index < 0 ? arrayLength + index : index;
    }
}
