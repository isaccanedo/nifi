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

package org.apache.nifi.avro;

import org.apache.avro.generic.GenericRecord;
import org.apache.nifi.serialization.MalformedRecordException;
import org.apache.nifi.serialization.RecordReader;
import org.apache.nifi.serialization.record.MapRecord;
import org.apache.nifi.serialization.record.Record;
import org.apache.nifi.serialization.record.RecordSchema;

import java.io.IOException;
import java.util.Map;

public abstract class AvroRecordReader implements RecordReader {

    protected abstract GenericRecord nextAvroRecord() throws IOException;

    @Override
    public Record nextRecord(final boolean coerceTypes, final boolean dropUnknownFields) throws IOException, MalformedRecordException {
        try {
            GenericRecord record = nextAvroRecord();
            if (record == null) {
                return null;
            }

            final RecordSchema schema = getSchema();
            final Map<String, Object> values = AvroTypeUtil.convertAvroRecordToMap(record, schema);
            return new MapRecord(schema, values);
        } catch (IOException e) {
            throw e;
        } catch (MalformedRecordException e) {
            throw e;
        } catch (Exception e) {
            throw new MalformedRecordException("Error while getting next record", e);
        }
    }
}
