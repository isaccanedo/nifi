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

package org.apache.nifi.serialization.record.type;

import org.apache.nifi.serialization.record.DataType;
import org.apache.nifi.serialization.record.RecordFieldRemovalPath;
import org.apache.nifi.serialization.record.RecordFieldType;
import org.apache.nifi.serialization.record.RecordSchema;

import java.util.List;
import java.util.Objects;

public class ArrayDataType extends DataType {

    public static final boolean DEFAULT_NULLABLE = false;

    private final DataType elementType;
    private final boolean elementsNullable;

    public ArrayDataType(final DataType elementType) {
        this(elementType, DEFAULT_NULLABLE);
    }

    public ArrayDataType(final DataType elementType, boolean elementsNullable) {
        super(RecordFieldType.ARRAY, null);
        this.elementType = elementType;
        this.elementsNullable = elementsNullable;
    }

    public DataType getElementType() {
        return elementType;
    }

    @Override
    public RecordFieldType getFieldType() {
        return RecordFieldType.ARRAY;
    }

    public boolean isElementsNullable() {
        return elementsNullable;
    }

    @Override
    public void removePath(final RecordFieldRemovalPath path) {
        if (path.length() == 0) {
            return;
        }
        getElementType().removePath(path.tail());
    }

    @Override
    public boolean isRecursive(final List<RecordSchema> schemas) {
        // allow for null elementType during schema inference
        return getElementType() != null && getElementType().isRecursive(schemas);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArrayDataType)) return false;
        if (!super.equals(o)) return false;
        ArrayDataType that = (ArrayDataType) o;
        return isElementsNullable() == that.isElementsNullable()
                && Objects.equals(getElementType(), that.getElementType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getElementType(), isElementsNullable());
    }

    @Override
    public String toString() {
        return "ARRAY[" + elementType + "]";
    }
}
