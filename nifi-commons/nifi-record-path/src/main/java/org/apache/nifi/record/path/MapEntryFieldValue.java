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
import org.apache.nifi.serialization.record.RecordField;

import java.util.Map;
import java.util.Objects;

public class MapEntryFieldValue extends StandardFieldValue {
    private final String mapKey;

    public MapEntryFieldValue(final Object value, final RecordField field, final FieldValue parent, final String mapKey) {
        super(value, field, validateParentRecord(parent));
        this.mapKey = mapKey;
    }

    public String getMapKey() {
        return mapKey;
    }

    @Override
    public void updateValue(final Object newValue) {
        getParentRecord().ifPresent(parent -> parent.setMapValue(getField().getFieldName(), getMapKey(), newValue));
    }

    @Override
    public void updateValue(final Object newValue, final DataType dataType) {
        getParentRecord().ifPresent(parent -> parent.setMapValue(getField().getFieldName(), getMapKey(), newValue));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void remove() {
        getParent().ifPresent(parent -> {
            if (parent.getValue() instanceof Map) {
                ((Map<String, Object>) parent.getValue()).remove(mapKey);
            }
        });
    }

    @Override
    public int hashCode() {
        return Objects.hash(getValue(), getField(), getParent(), mapKey);
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof MapEntryFieldValue)) {
            return false;
        }

        final MapEntryFieldValue other = (MapEntryFieldValue) obj;
        return Objects.equals(getValue(), other.getValue()) && Objects.equals(getField(), other.getField())
            && Objects.equals(getParent(), other.getParent()) && Objects.equals(getMapKey(), other.getMapKey());
    }
}
