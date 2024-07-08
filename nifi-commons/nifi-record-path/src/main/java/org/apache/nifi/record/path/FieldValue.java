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

package org.apache.nifi.record.path;

import org.apache.nifi.serialization.record.DataType;
import org.apache.nifi.serialization.record.Record;
import org.apache.nifi.serialization.record.RecordField;

import java.util.Optional;

public interface FieldValue {
    /**
     * @return the value of the field
     */
    Object getValue();

    /**
     * @return the field that the associated value belongs to
     */
    RecordField getField();

    /**
     * @return the FieldValue that applies to the parent of this field, or an empty Optional if the
     *         field represents the 'root' record.
     */
    Optional<FieldValue> getParent();

    /**
     * @return the Record that the field belongs to, or an empty Optional if the
     *         field represents the 'root' record.
     */
    Optional<Record> getParentRecord();

    /**
     * Updates the record to which the field belongs, so that it now has the given value.
     *
     * @param newValue the new value to set on the record field
     */
    void updateValue(Object newValue);

    /**
     * Updates the record to which the field belongs, so that it now has the given value. If the FieldValue
     * points to a Field that does not currently exist in the Record, the field will be created in the Record Schema
     * as an 'inactive field', which can then be incoporated into the Record's schema by calling {@link Record#incorporateInactiveFields()}.
     *
     * @param newValue the value to set for the field
     * @param dataType the data type to use if the Record's schema does not already include this field
     */
    void updateValue(Object newValue, DataType dataType);

    /**
     * Removes this FieldValue instance from its parent FieldValue instance
     */
    void remove();

    /**
     * Removes the content of this FieldValue instance
     */
    void removeContent();
}
