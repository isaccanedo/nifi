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

package org.apache.nifi.wali;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wali.DummyRecord;
import org.wali.DummyRecordSerde;
import org.wali.SerDeFactory;
import org.wali.SingletonSerDeFactory;
import org.wali.UpdateType;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLengthDelimitedJournal {
    private final File journalFile = new File("target/testLengthDelimitedJournal/testJournal.journal");
    private SerDeFactory<DummyRecord> serdeFactory;
    private DummyRecordSerde serde;
    private ObjectPool<ByteArrayDataOutputStream> streamPool;
    private static final int BUFFER_SIZE = 4096;

    @BeforeEach
    public void setupJournal() throws IOException {
        Files.deleteIfExists(journalFile.toPath());

        if (!journalFile.getParentFile().exists()) {
            Files.createDirectories(journalFile.getParentFile().toPath());
        }

        serde = new DummyRecordSerde();
        serdeFactory = new SingletonSerDeFactory<>(serde);
        streamPool = new BlockingQueuePool<>(1,
            () -> new ByteArrayDataOutputStream(BUFFER_SIZE),
            stream -> stream.getByteArrayOutputStream().size() < BUFFER_SIZE,
            stream -> stream.getByteArrayOutputStream().reset());
    }

    @Test
    public void testHandlingOfTrailingNulBytes() throws IOException {
        try (final LengthDelimitedJournal<DummyRecord> journal = new LengthDelimitedJournal<>(journalFile, serdeFactory, streamPool, 0L)) {
            journal.writeHeader();

            final List<DummyRecord> firstTransaction = new ArrayList<>();
            firstTransaction.add(new DummyRecord("1", UpdateType.CREATE));
            firstTransaction.add(new DummyRecord("2", UpdateType.CREATE));
            firstTransaction.add(new DummyRecord("3", UpdateType.CREATE));

            final List<DummyRecord> secondTransaction = new ArrayList<>();
            secondTransaction.add(new DummyRecord("1", UpdateType.UPDATE).setProperty("abc", "123"));
            secondTransaction.add(new DummyRecord("2", UpdateType.UPDATE).setProperty("cba", "123"));
            secondTransaction.add(new DummyRecord("3", UpdateType.UPDATE).setProperty("aaa", "123"));

            final List<DummyRecord> thirdTransaction = new ArrayList<>();
            thirdTransaction.add(new DummyRecord("1", UpdateType.DELETE));
            thirdTransaction.add(new DummyRecord("2", UpdateType.DELETE));

            journal.update(firstTransaction, id -> null);
            journal.update(secondTransaction, id -> null);
            journal.update(thirdTransaction, id -> null);
        }

        // Truncate the contents of the journal file by 8 bytes. Then replace with 28 trailing NUL bytes,
        // as this is what we often see when we have a sudden power loss.
        final byte[] contents = Files.readAllBytes(journalFile.toPath());
        final byte[] truncated = Arrays.copyOfRange(contents, 0, contents.length - 8);
        final byte[] withNuls = new byte[truncated.length + 28];
        System.arraycopy(truncated, 0, withNuls, 0, truncated.length);

        try (final OutputStream fos = new FileOutputStream(journalFile)) {
            fos.write(withNuls);
        }


        try (final LengthDelimitedJournal<DummyRecord> journal = new LengthDelimitedJournal<>(journalFile, serdeFactory, streamPool, 0L)) {
            final Map<Object, DummyRecord> recordMap = new HashMap<>();
            final Set<String> swapLocations = new HashSet<>();

            journal.recoverRecords(recordMap, swapLocations);

            assertFalse(recordMap.isEmpty());
            assertEquals(3, recordMap.size());

            final DummyRecord record1 = recordMap.get("1");
            assertNotNull(record1);
            assertEquals(Collections.singletonMap("abc", "123"), record1.getProperties());

            final DummyRecord record2 = recordMap.get("2");
            assertNotNull(record2);
            assertEquals(Collections.singletonMap("cba", "123"), record2.getProperties());

            final DummyRecord record3 = recordMap.get("3");
            assertNotNull(record3);
            assertEquals(Collections.singletonMap("aaa", "123"), record3.getProperties());
        }
    }

    @Test
    public void testUpdateOnlyAppliedIfEntireTransactionApplied() throws IOException {
        try (final LengthDelimitedJournal<DummyRecord> journal = new LengthDelimitedJournal<>(journalFile, serdeFactory, streamPool, 0L)) {
            journal.writeHeader();

            for (int i = 0; i < 3; i++) {
                final DummyRecord record = new DummyRecord(String.valueOf(i), UpdateType.CREATE);
                journal.update(Collections.singleton(record), key -> null);
            }

            final DummyRecord swapOut1Record = new DummyRecord("1", UpdateType.SWAP_OUT);
            swapOut1Record.setSwapLocation("swap12");
            journal.update(Collections.singleton(swapOut1Record), id -> null);

            final DummyRecord swapOut2Record = new DummyRecord("2", UpdateType.SWAP_OUT);
            swapOut2Record.setSwapLocation("swap12");
            journal.update(Collections.singleton(swapOut2Record), id -> null);

            final List<DummyRecord> records = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                final DummyRecord record = new DummyRecord("1" + i, UpdateType.CREATE);
                records.add(record);
            }

            final DummyRecord swapIn1Record = new DummyRecord("1", UpdateType.SWAP_IN);
            swapIn1Record.setSwapLocation("swap12");
            records.add(swapIn1Record);

            final DummyRecord swapOut1AgainRecord = new DummyRecord("1", UpdateType.SWAP_OUT);
            swapOut1AgainRecord.setSwapLocation("swap12");
            records.add(swapOut1AgainRecord);

            final DummyRecord swapIn2Record = new DummyRecord("2", UpdateType.SWAP_IN);
            swapIn2Record.setSwapLocation("swap12");
            records.add(swapIn2Record);

            final DummyRecord swapOut0Record = new DummyRecord("0", UpdateType.SWAP_OUT);
            swapOut0Record.setSwapLocation("swap0");
            records.add(swapOut0Record);

            journal.update(records, id -> null);
        }

        // Truncate the last 8 bytes so that we will get an EOFException when reading the last transaction.
        try (final FileOutputStream fos = new FileOutputStream(journalFile, true)) {
            fos.getChannel().truncate(journalFile.length() - 8);
        }


        try (final LengthDelimitedJournal<DummyRecord> journal = new LengthDelimitedJournal<>(journalFile, serdeFactory, streamPool, 0L)) {
            final Map<Object, DummyRecord> recordMap = new HashMap<>();
            final Set<String> swapLocations = new HashSet<>();

            final JournalRecovery recovery = journal.recoverRecords(recordMap, swapLocations);
            assertEquals(5L, recovery.getMaxTransactionId());
            assertEquals(5, recovery.getUpdateCount());

            final Set<String> expectedSwap = Collections.singleton("swap12");
            assertEquals(expectedSwap, swapLocations);

            final Map<Object, DummyRecord> expectedRecordMap = new HashMap<>();
            expectedRecordMap.put("0", new DummyRecord("0", UpdateType.CREATE));
            assertEquals(expectedRecordMap, recordMap);
        }
    }

    @Test
    public void testPoisonedJournalNotWritableAfterIOE() throws IOException {
        try (final LengthDelimitedJournal<DummyRecord> journal = new LengthDelimitedJournal<>(journalFile, serdeFactory, streamPool, 0L)) {
            journal.writeHeader();

            serde.setThrowIOEAfterNSerializeEdits(2);

            final DummyRecord firstRecord = new DummyRecord("1", UpdateType.CREATE);
            journal.update(Collections.singleton(firstRecord), key -> null);

            final DummyRecord secondRecord = new DummyRecord("1", UpdateType.UPDATE);
            journal.update(Collections.singleton(secondRecord), key -> firstRecord);

            final DummyRecord thirdRecord = new DummyRecord("1", UpdateType.UPDATE);
            final RecordLookup<DummyRecord> lookup = key -> secondRecord;
            assertThrows(IOException.class, () -> journal.update(Collections.singleton(thirdRecord), lookup));

            serde.setThrowIOEAfterNSerializeEdits(-1);

            final Collection<DummyRecord> records = Collections.singleton(thirdRecord);
            for (int i = 0; i < 10; i++) {
                assertThrows(IOException.class, () -> journal.update(records, lookup));
                assertThrows(IOException.class, () -> journal.fsync());
            }
        }
    }

    @Test
    public void testPoisonedJournalNotWritableAfterOOME() throws IOException {
        try (final LengthDelimitedJournal<DummyRecord> journal = new LengthDelimitedJournal<>(journalFile, serdeFactory, streamPool, 0L)) {
            journal.writeHeader();

            serde.setThrowOOMEAfterNSerializeEdits(2);

            final DummyRecord firstRecord = new DummyRecord("1", UpdateType.CREATE);
            journal.update(Collections.singleton(firstRecord), key -> null);

            final DummyRecord secondRecord = new DummyRecord("1", UpdateType.UPDATE);
            journal.update(Collections.singleton(secondRecord), key -> firstRecord);

            final DummyRecord thirdRecord = new DummyRecord("1", UpdateType.UPDATE);
            final RecordLookup<DummyRecord> lookup = key -> secondRecord;
            assertThrows(OutOfMemoryError.class, () -> journal.update(Collections.singleton(thirdRecord), lookup));

            serde.setThrowOOMEAfterNSerializeEdits(-1);

            final Collection<DummyRecord> records = Collections.singleton(thirdRecord);
            for (int i = 0; i < 10; i++) {
                assertThrows(IOException.class, () -> journal.update(records, lookup));
                assertThrows(IOException.class, () -> journal.fsync());
            }
        }
    }

    @Test
    public void testSuccessfulRoundTrip() throws IOException {
        try (final LengthDelimitedJournal<DummyRecord> journal = new LengthDelimitedJournal<>(journalFile, serdeFactory, streamPool, 0L)) {
            journal.writeHeader();

            final DummyRecord firstRecord = new DummyRecord("1", UpdateType.CREATE);
            journal.update(Collections.singleton(firstRecord), key -> null);

            final DummyRecord secondRecord = new DummyRecord("1", UpdateType.UPDATE);
            journal.update(Collections.singleton(secondRecord), key -> firstRecord);

            final DummyRecord thirdRecord = new DummyRecord("1", UpdateType.UPDATE);
            journal.update(Collections.singleton(thirdRecord), key -> secondRecord);

            final Map<Object, DummyRecord> recordMap = new HashMap<>();
            final Set<String> swapLocations = new HashSet<>();
            final JournalRecovery recovery = journal.recoverRecords(recordMap, swapLocations);
            assertFalse(recovery.isEOFExceptionEncountered());

            assertEquals(2L, recovery.getMaxTransactionId());
            assertEquals(3, recovery.getUpdateCount());

            assertTrue(swapLocations.isEmpty());
            assertEquals(1, recordMap.size());

            final DummyRecord retrieved = recordMap.get("1");
            assertNotNull(retrieved);
            assertEquals(thirdRecord, retrieved);
        }
    }

    @Test
    public void testMultipleThreadsCreatingOverflowDirectory() throws IOException, InterruptedException {
        final LengthDelimitedJournal<DummyRecord> journal = new LengthDelimitedJournal<DummyRecord>(journalFile, serdeFactory, streamPool, 3820L, 100) {
            @Override
            protected void createOverflowDirectory(final Path path) throws IOException {
                // Create the overflow directory.
                super.createOverflowDirectory(path);

                // Ensure that a second call to create the overflow directory will not cause an issue.
                super.createOverflowDirectory(path);
            }
        };

        // Ensure that the overflow directory does not exist.
        journal.dispose();

        try {
            journal.writeHeader();

            final List<DummyRecord> largeCollection1 = new ArrayList<>();
            for (int i = 0; i < 1_000; i++) {
                largeCollection1.add(new DummyRecord(String.valueOf(i), UpdateType.CREATE));
            }
            final Map<String, DummyRecord> recordMap = largeCollection1.stream()
                .collect(Collectors.toMap(DummyRecord::getId, rec -> rec));

            final List<DummyRecord> largeCollection2 = new ArrayList<>();
            for (int i = 0; i < 1_000; i++) {
                largeCollection2.add(new DummyRecord(String.valueOf(5_000_000 + i), UpdateType.CREATE));
            }
            final Map<String, DummyRecord> recordMap2 = largeCollection2.stream()
                .collect(Collectors.toMap(DummyRecord::getId, rec -> rec));

            final AtomicReference<Exception> thread1Failure = new AtomicReference<>();
            final Thread t1 = new Thread(() -> {
                try {
                    journal.update(largeCollection1, recordMap::get);
                } catch (final Exception e) {
                    e.printStackTrace();
                    thread1Failure.set(e);
                }
            });
            t1.start();

            final AtomicReference<Exception> thread2Failure = new AtomicReference<>();
            final Thread t2 = new Thread(() -> {
                try {
                    journal.update(largeCollection2, recordMap2::get);
                } catch (final Exception e) {
                    e.printStackTrace();
                    thread2Failure.set(e);
                }
            });
            t2.start();

            t1.join();
            t2.join();

            assertNull(thread1Failure.get());
            assertNull(thread2Failure.get());
        } finally {
            journal.close();
        }
    }

    @Test
    public void testTruncatedJournalFile() throws IOException {
        final DummyRecord firstRecord, secondRecord;
        try (final LengthDelimitedJournal<DummyRecord> journal = new LengthDelimitedJournal<>(journalFile, serdeFactory, streamPool, 0L)) {
            journal.writeHeader();

            firstRecord = new DummyRecord("1", UpdateType.CREATE);
            journal.update(Collections.singleton(firstRecord), key -> null);

            secondRecord = new DummyRecord("2", UpdateType.CREATE);
            journal.update(Collections.singleton(secondRecord), key -> firstRecord);

            final DummyRecord thirdRecord = new DummyRecord("1", UpdateType.UPDATE);
            journal.update(Collections.singleton(thirdRecord), key -> secondRecord);
        }

        // Truncate the file
        try (final FileOutputStream fos = new FileOutputStream(journalFile, true)) {
            fos.getChannel().truncate(journalFile.length() - 8);
        }

        // Ensure that we are able to recover the first two records without an issue but the third is lost.
        try (final LengthDelimitedJournal<DummyRecord> journal = new LengthDelimitedJournal<>(journalFile, serdeFactory, streamPool, 0L)) {
            final Map<Object, DummyRecord> recordMap = new HashMap<>();
            final Set<String> swapLocations = new HashSet<>();
            final JournalRecovery recovery = journal.recoverRecords(recordMap, swapLocations);
            assertTrue(recovery.isEOFExceptionEncountered());

            assertEquals(2L, recovery.getMaxTransactionId()); // transaction ID is still 2 because that's what was written to the journal
            assertEquals(2, recovery.getUpdateCount()); // only 2 updates because the last update will incur an EOFException and be skipped

            assertTrue(swapLocations.isEmpty());
            assertEquals(2, recordMap.size());

            final DummyRecord retrieved1 = recordMap.get("1");
            assertNotNull(retrieved1);
            assertEquals(firstRecord, retrieved1);

            final DummyRecord retrieved2 = recordMap.get("2");
            assertNotNull(retrieved2);
            assertEquals(secondRecord, retrieved2);
        }
    }

    /**
     * This test is rather complicated and creates a lot of odd objects with Thread.sleep, etc., and it may not be at-all clear what is happening. The intent of this
     * test is to try to cause a race condition to occur that would cause the journal to become corrupt. Consider the following scenario:
     *
     * 1. Thread 1 attempts to update the Journal. In the #update method, it checks the state of the Journal, which is healthy.
     * 2. Thread 2 attempts to update the Journal. In the #update method, it checks the state of the Journal, which is healthy.
     * 3. Thread 1 now throws an Exception when attempt to write to disk (in this case, an IOException but could also be an OutOfMemoryError, etc.) The Exception is caught by the Journal,
     *    and the #poisoni method is called. Before the #poison method is able to close the underlying Output Stream, Thread 2 is able to write to the Output Stream (which is no longer guarded
     *    by Thread 1 because of the Exception that was just thrown).
     * 4. Thread 2 writes to the Journal, after Thread 1 had written only a partial update. The repository has now become corrupt.
     * 5. Thread 1 closes the file handle, but does so too late. Corruption has already occurred!
     *
     * In order to replicate the above series of steps in a unit test, we need to introduce some oddities.
     *
     * - The #poison method, when called, needs to wait a bit before actually closing the underlying FileOutputStream, so that Thread 2 has a chance to run after Thread 1 stop guarding the Output
     * Stream and before Thread 1 closes the Output Stream. (This is handled in the test by subclassing the Journal and overriding the #poison method to pause).
     * - We need to ensure that Thread 1 and Thread 2 are both able to pass the #checkState check in #update before the corruption occurs. (This is handled in the test by the 'pausingBados' object,
     * which will enter the #update method, then pause before returning the ByteArrayOutputStream, which essentially yields to Thread 1, the 'corrupting thread').
     * - After both threads have passed the #checkState check, we need Thread 1 to write a partial update, then throw an Exception (This is handled in the test by the 'corruptingBados' object).
     * - After the Exception is thrown, we need Thread 2 to update the contents of the repository before the file is closed. (This is handled in the test by subclassing the Journal and calling
     * Thread.sleep in the #poison method).
     *
     */
    @Test
    public void testFailureDuringWriteCannotCauseCorruption() throws IOException, InterruptedException {
        // Create a ByteArrayDataOutputStream such that when attempting to write the data to another OutputStream via its ByteArrayOutputStream,
        // the BADOS will copy 5 bytes, then throw an IOException. This should result in the journal being poisoned such that it can no longer
        // be written to.
        final ByteArrayDataOutputStream corruptingBados = new ByteArrayDataOutputStream(4096) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream() {
                @Override
                public synchronized void writeTo(final OutputStream out) throws IOException {
                    out.write(buf, 0, 5);
                    throw new IOException("Intentional Exception in Unit Test designed to cause corruption");
                }

                @Override
                public synchronized String toString() {
                    return "Corrupting ByteArrayOutputStream[" + super.toString() + "]";
                }
            };

            @Override
            public ByteArrayOutputStream getByteArrayOutputStream() {
                return baos;
            }

            @Override
            public DataOutputStream getDataOutputStream() {
                return new DataOutputStream(baos);
            }
        };

        // Create a ByteArrayDataOutputStream such that when attempting to write the data to another OutputStream via its ByteArrayOutputStream,
        // the BADOS will sleep for 1 second before writing. This allwos other threads to trigger corruption in the repo in the meantime.
        final ByteArrayDataOutputStream pausingBados = new ByteArrayDataOutputStream(4096) {
            private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            private final AtomicInteger count = new AtomicInteger(0);

            @Override
            public ByteArrayOutputStream getByteArrayOutputStream() {
                // Pause only on the second iteration.
                if (count.getAndIncrement() == 1) {
                    try {
                        Thread.sleep(1000L);
                    } catch (final InterruptedException ie) {
                    }
                }

                return baos;
            }

            @Override
            public DataOutputStream getDataOutputStream() {
                return new DataOutputStream(baos);
            }
        };


        final Supplier<ByteArrayDataOutputStream> badosSupplier = new Supplier<ByteArrayDataOutputStream>() {
            private final AtomicInteger count = new AtomicInteger(0);

            @Override
            public ByteArrayDataOutputStream get() {
                if (count.getAndIncrement() == 0) {
                    return pausingBados;
                }

                return corruptingBados;
            }
        };


        final ObjectPool<ByteArrayDataOutputStream> corruptingStreamPool = new BlockingQueuePool<>(2,
            badosSupplier,
            stream -> true,
            stream -> stream.getByteArrayOutputStream().reset());


        final Thread[] threads = new Thread[2];

        final LengthDelimitedJournal<DummyRecord> journal = new LengthDelimitedJournal<DummyRecord>(journalFile, serdeFactory, corruptingStreamPool, 0L) {
            private final AtomicInteger count = new AtomicInteger(0);

            @Override
            protected void poison(final Throwable t)  {
                if (count.getAndIncrement() == 0) { // it is only important that we sleep the first time. If we sleep every time, it just slows the test down.
                    try {
                        Thread.sleep(3000L);
                    } catch (InterruptedException e) {
                    }
                }

                super.poison(t);
            }
        };


        final DummyRecord firstRecord, secondRecord;
        try {
            journal.writeHeader();

            firstRecord = new DummyRecord("1", UpdateType.CREATE);
            secondRecord = new DummyRecord("2", UpdateType.CREATE);

            final Thread t1 = new Thread(() -> {
                try {
                    journal.update(Collections.singleton(firstRecord), key -> null);
                } catch (final IOException ioe) {
                }
            });


            final Thread t2 = new Thread(() -> {
                try {
                    journal.update(Collections.singleton(secondRecord), key -> firstRecord);
                } catch (final IOException ioe) {
                }
            });

            threads[0] = t1;
            threads[1] = t2;

            t1.start();
            t2.start();

            for (Thread thread : threads) {
                thread.join();
            }
        } finally {
            journal.close();
        }

        // Now, attempt to read from the Journal to ensure that it is not corrupt.
        try (final LengthDelimitedJournal<DummyRecord> recoveryJournal = new LengthDelimitedJournal<>(journalFile, serdeFactory, corruptingStreamPool, 0L)) {
            final Map<Object, DummyRecord> recordMap = new HashMap<>();
            final Set<String> swapLocations = new HashSet<>();

            recoveryJournal.recoverRecords(recordMap, swapLocations);
            assertEquals(0, recordMap.size());
        }
    }
}
