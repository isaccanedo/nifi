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
package org.apache.nifi.distributed.cache.server.map;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.OverlappingFileLockException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.nifi.wali.SequentialAccessWriteAheadLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.wali.SerDe;
import org.wali.SerDeFactory;
import org.wali.UpdateType;
import org.wali.WriteAheadRepository;

public class PersistentMapCache implements MapCache {

    private static final Logger logger = LoggerFactory.getLogger(PersistentMapCache.class);

    private final MapCache wrapped;
    private final WriteAheadRepository<MapWaliRecord> wali;

    private final AtomicLong modifications = new AtomicLong(0L);

    public PersistentMapCache(final String serviceIdentifier, final File persistencePath, final MapCache cacheToWrap) throws IOException {
        try {
            wali = new SequentialAccessWriteAheadLog<>(persistencePath, new SerdeFactory());
        } catch (OverlappingFileLockException ex) {
            logger.error("OverlappingFileLockException thrown: Check lock location - possible duplicate persistencePath conflict in PersistentMapCache.");
            // Propagate the exception
            throw ex;
        }
        wrapped = cacheToWrap;
    }

    synchronized void restore() throws IOException {
        final Collection<MapWaliRecord> recovered = wali.recoverRecords();
        for (final MapWaliRecord record : recovered) {
            if (record.getUpdateType() == UpdateType.CREATE) {
                wrapped.putIfAbsent(record.getKey(), record.getValue());
            }
        }
    }

    @Override
    public MapPutResult putIfAbsent(final ByteBuffer key, final ByteBuffer value) throws IOException {
        final MapPutResult putResult = wrapped.putIfAbsent(key, value);
        putWriteAheadLog(key, value, putResult);
        return putResult;
    }

    @Override
    public MapPutResult put(final ByteBuffer key, final ByteBuffer value) throws IOException {
        final MapPutResult putResult = wrapped.put(key, value);
        putWriteAheadLog(key, value, putResult);
        return putResult;
    }

    protected void putWriteAheadLog(ByteBuffer key, ByteBuffer value, MapPutResult putResult) throws IOException {
        if ( putResult.isSuccessful() ) {
            // The put was successful.
            final MapWaliRecord record = new MapWaliRecord(UpdateType.CREATE, key, value);
            final List<MapWaliRecord> records = new ArrayList<>();
            records.add(record);

            final MapCacheRecord evicted = putResult.getEvicted();
            if ( evicted != null ) {
                records.add(new MapWaliRecord(UpdateType.DELETE, evicted.getKey(), evicted.getValue()));
            }

            wali.update(records, false);

            final long modCount = modifications.getAndIncrement();
            if ( modCount > 0 && modCount % 100000 == 0 ) {
                wali.checkpoint();
            }
        }
    }

    @Override
    public boolean containsKey(final ByteBuffer key) throws IOException {
        return wrapped.containsKey(key);
    }

    @Override
    public ByteBuffer get(final ByteBuffer key) throws IOException {
        return wrapped.get(key);
    }

    @Override
    public Map<ByteBuffer, ByteBuffer> subMap(List<ByteBuffer> keys) throws IOException {
        if (keys == null) {
            return null;
        }
        Map<ByteBuffer, ByteBuffer> results = new HashMap<>(keys.size());
        for (ByteBuffer key : keys) {
            results.put(key, wrapped.get(key));
        }
        return results;
    }

    @Override
    public MapCacheRecord fetch(ByteBuffer key) throws IOException {
        return wrapped.fetch(key);
    }

    @Override
    public MapPutResult replace(MapCacheRecord record) throws IOException {
        final MapPutResult putResult = wrapped.replace(record);
        putWriteAheadLog(record.getKey(), record.getValue(), putResult);
        return putResult;
    }

    @Override
    public ByteBuffer remove(final ByteBuffer key) throws IOException {
        final ByteBuffer removeResult = wrapped.remove(key);
        if (removeResult != null) {
            final MapWaliRecord record = new MapWaliRecord(UpdateType.DELETE, key, removeResult);
            final List<MapWaliRecord> records = new ArrayList<>(1);
            records.add(record);
            wali.update(records, false);

            final long modCount = modifications.getAndIncrement();
            if (modCount > 0 && modCount % 1000 == 0) {
                wali.checkpoint();
            }
        }
        return removeResult;
    }

    @Override
    public Set<ByteBuffer> keySet() throws IOException {
        return wrapped.keySet();
    }

    @Override
    public void shutdown() throws IOException {
        wali.shutdown();
    }

    private static class MapWaliRecord {

        private final UpdateType updateType;
        private final ByteBuffer key;
        private final ByteBuffer value;

        public MapWaliRecord(final UpdateType updateType, final ByteBuffer key, final ByteBuffer value) {
            this.updateType = updateType;
            this.key = key;
            this.value = value;
        }

        public UpdateType getUpdateType() {
            return updateType;
        }

        public ByteBuffer getKey() {
            return key;
        }

        public ByteBuffer getValue() {
            return value;
        }
    }

    private static class Serde implements SerDe<MapWaliRecord> {

        @Override
        public void serializeEdit(final MapWaliRecord previousRecordState, final MapWaliRecord newRecordState, final java.io.DataOutputStream out) throws IOException {
            final UpdateType updateType = newRecordState.getUpdateType();
            if (updateType == UpdateType.DELETE) {
                out.write(0);
            } else {
                out.write(1);
            }

            final byte[] key = newRecordState.getKey().array();
            final byte[] value = newRecordState.getValue().array();

            out.writeInt(key.length);
            out.write(key);
            out.writeInt(value.length);
            out.write(value);
        }

        @Override
        public void serializeRecord(final MapWaliRecord record, final java.io.DataOutputStream out) throws IOException {
            serializeEdit(null, record, out);
        }

        @Override
        public MapWaliRecord deserializeEdit(final DataInputStream in, final Map<Object, MapWaliRecord> currentRecordStates, final int version) throws IOException {
            final int updateTypeValue = in.read();
            if (updateTypeValue < 0) {
                throw new EOFException();
            }

            final UpdateType updateType = (updateTypeValue == 0 ? UpdateType.DELETE : UpdateType.CREATE);

            final int keySize = in.readInt();
            final byte[] key = new byte[keySize];
            in.readFully(key);

            final int valueSize = in.readInt();
            final byte[] value = new byte[valueSize];
            in.readFully(value);

            return new MapWaliRecord(updateType, ByteBuffer.wrap(key), ByteBuffer.wrap(value));
        }

        @Override
        public MapWaliRecord deserializeRecord(final DataInputStream in, final int version) throws IOException {
            return deserializeEdit(in, new HashMap<>(), version);
        }

        @Override
        public Object getRecordIdentifier(final MapWaliRecord record) {
            return record.getKey();
        }

        @Override
        public UpdateType getUpdateType(final MapWaliRecord record) {
            return record.getUpdateType();
        }

        @Override
        public String getLocation(final MapWaliRecord record) {
            return null;
        }

        @Override
        public int getVersion() {
            return 1;
        }
    }

    private static class SerdeFactory implements SerDeFactory<MapWaliRecord> {

        private final Serde serde;

        public SerdeFactory() {
            this.serde = new Serde();
        }

        @Override
        public SerDe<MapWaliRecord> createSerDe(String encodingName) {
            return this.serde;
        }

        @Override
        public Object getRecordIdentifier(MapWaliRecord record) {
            return this.serde.getRecordIdentifier(record);
        }

        @Override
        public UpdateType getUpdateType(MapWaliRecord record) {
            return this.serde.getUpdateType(record);
        }

        @Override
        public String getLocation(MapWaliRecord record) {
            return this.serde.getLocation(record);
        }

    }
}
