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
package org.apache.nifi.processors.standard;

import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "Test only runs on *nix")
public class TestPutFile {

    public static final String TARGET_DIRECTORY = "target/put-file";
    private File targetDir;

    @BeforeEach
    public void prepDestDirectory() throws IOException {
        targetDir = new File(TARGET_DIRECTORY);
        if (!targetDir.exists()) {
            Files.createDirectories(targetDir.toPath());
            return;
        }

        targetDir.setReadable(true);

        deleteDirectoryContent(targetDir);
    }

    private void deleteDirectoryContent(File directory) throws IOException {
        for (final File file : directory.listFiles()) {
            if (file.isDirectory()) {
                deleteDirectoryContent(file);
            }
            Files.delete(file.toPath());
        }
    }

    @Test
    public void testCreateDirectory() throws IOException {
        final TestRunner runner = TestRunners.newTestRunner(new PutFile());
        String newDir = targetDir.getAbsolutePath() + "/new-folder";
        runner.setProperty(PutFile.DIRECTORY, newDir);
        runner.setProperty(PutFile.CONFLICT_RESOLUTION, PutFile.REPLACE_RESOLUTION);

        Map<String, String> attributes = new HashMap<>();
        attributes.put(CoreAttributes.FILENAME.key(), "targetFile.txt");
        runner.enqueue("Hello world!!".getBytes(), attributes);
        runner.run();
        runner.assertAllFlowFilesTransferred(FetchFile.REL_SUCCESS, 1);
        Path targetPath = Paths.get(TARGET_DIRECTORY + "/new-folder/targetFile.txt");
        byte[] content = Files.readAllBytes(targetPath);
        assertEquals("Hello world!!", new String(content));
    }

    @Test
    public void testCreateRelativeDirectory() throws IOException {
        final TestRunner runner = TestRunners.newTestRunner(new PutFile());
        String newDir = TARGET_DIRECTORY + "/new-folder";
        runner.setProperty(PutFile.DIRECTORY, newDir);
        runner.setProperty(PutFile.CONFLICT_RESOLUTION, PutFile.REPLACE_RESOLUTION);

        Map<String, String> attributes = new HashMap<>();
        attributes.put(CoreAttributes.FILENAME.key(), "targetFile.txt");
        runner.enqueue("Hello world!!".getBytes(), attributes);
        runner.run();
        runner.assertAllFlowFilesTransferred(FetchFile.REL_SUCCESS, 1);
        Path targetPath = Paths.get(newDir + "/targetFile.txt");
        byte[] content = Files.readAllBytes(targetPath);
        assertEquals("Hello world!!", new String(content));
    }

    @Test
    public void testCreateEmptyStringDirectory() {
        final TestRunner runner = TestRunners.newTestRunner(new PutFile());
        String newDir = "";
        runner.setProperty(PutFile.DIRECTORY, newDir);
        runner.setProperty(PutFile.CONFLICT_RESOLUTION, PutFile.REPLACE_RESOLUTION);

        runner.assertNotValid();
    }

    @Test
    public void testReplaceConflictResolution() throws IOException {
        final TestRunner runner = TestRunners.newTestRunner(new PutFile());
        runner.setProperty(PutFile.DIRECTORY, targetDir.getAbsolutePath());
        runner.setProperty(PutFile.CONFLICT_RESOLUTION, PutFile.REPLACE_RESOLUTION);

        Map<String, String> attributes = new HashMap<>();
        attributes.put(CoreAttributes.FILENAME.key(), "targetFile.txt");
        runner.enqueue("Hello world!!".getBytes(), attributes);
        runner.run();
        runner.assertAllFlowFilesTransferred(FetchFile.REL_SUCCESS, 1);
        Path targetPath = Paths.get(TARGET_DIRECTORY + "/targetFile.txt");
        byte[] content = Files.readAllBytes(targetPath);
        assertEquals("Hello world!!", new String(content));

        //Second file
        attributes.put(CoreAttributes.FILENAME.key(), "targetFile.txt");
        runner.enqueue("Another file".getBytes(), attributes);
        runner.run();
        runner.assertTransferCount(FetchFile.REL_SUCCESS, 2);
        File dir = new File(TARGET_DIRECTORY);
        assertEquals(1, dir.list().length);
        targetPath = Paths.get(TARGET_DIRECTORY + "/targetFile.txt");
        content = Files.readAllBytes(targetPath);
        assertEquals("Another file", new String(content));
    }

    @Test
    public void testIgnoreConflictResolution() throws IOException {
        final TestRunner runner = TestRunners.newTestRunner(new PutFile());
        runner.setProperty(PutFile.DIRECTORY, targetDir.getAbsolutePath());
        runner.setProperty(PutFile.CONFLICT_RESOLUTION, PutFile.IGNORE_RESOLUTION);

        Map<String, String> attributes = new HashMap<>();
        attributes.put(CoreAttributes.FILENAME.key(), "targetFile.txt");
        runner.enqueue("Hello world!!".getBytes(), attributes);
        runner.run();
        runner.assertAllFlowFilesTransferred(FetchFile.REL_SUCCESS, 1);
        Path targetPath = Paths.get(TARGET_DIRECTORY + "/targetFile.txt");
        byte[] content = Files.readAllBytes(targetPath);
        assertEquals("Hello world!!", new String(content));

        //Second file
        attributes.put(CoreAttributes.FILENAME.key(), "targetFile.txt");
        runner.enqueue("Another file".getBytes(), attributes);
        runner.run();
        runner.assertTransferCount(FetchFile.REL_SUCCESS, 2);
        File dir = new File(TARGET_DIRECTORY);
        assertEquals(1, dir.list().length);
        targetPath = Paths.get(TARGET_DIRECTORY + "/targetFile.txt");
        content = Files.readAllBytes(targetPath);
        assertEquals("Hello world!!", new String(content));
    }

    @Test
    public void testFailConflictResolution() throws IOException {
        final TestRunner runner = TestRunners.newTestRunner(new PutFile());
        runner.setProperty(PutFile.DIRECTORY, targetDir.getAbsolutePath());
        runner.setProperty(PutFile.CONFLICT_RESOLUTION, PutFile.FAIL_RESOLUTION);

        Map<String, String> attributes = new HashMap<>();
        attributes.put(CoreAttributes.FILENAME.key(), "targetFile.txt");
        runner.enqueue("Hello world!!".getBytes(), attributes);
        runner.run();
        runner.assertAllFlowFilesTransferred(FetchFile.REL_SUCCESS, 1);
        Path targetPath = Paths.get(TARGET_DIRECTORY + "/targetFile.txt");
        byte[] content = Files.readAllBytes(targetPath);
        assertEquals("Hello world!!", new String(content));

        //Second file
        attributes.put(CoreAttributes.FILENAME.key(), "targetFile.txt");
        runner.enqueue("Another file".getBytes(), attributes);
        runner.run();
        runner.assertTransferCount(PutFile.REL_SUCCESS, 1);
        runner.assertTransferCount(PutFile.REL_FAILURE, 1);
        runner.assertPenalizeCount(1);
    }

    @Test
    public void testMaxFileLimitReach() throws IOException {
        final TestRunner runner = TestRunners.newTestRunner(new PutFile());
        runner.setProperty(PutFile.DIRECTORY, targetDir.getAbsolutePath());
        runner.setProperty(PutFile.CONFLICT_RESOLUTION, PutFile.REPLACE_RESOLUTION);
        runner.setProperty(PutFile.MAX_DESTINATION_FILES, "1");

        Map<String, String> attributes = new HashMap<>();
        attributes.put(CoreAttributes.FILENAME.key(), "targetFile.txt");
        runner.enqueue("Hello world!!".getBytes(), attributes);
        runner.run();
        runner.assertAllFlowFilesTransferred(FetchFile.REL_SUCCESS, 1);
        Path targetPath = Paths.get(TARGET_DIRECTORY + "/targetFile.txt");
        byte[] content = Files.readAllBytes(targetPath);
        assertEquals("Hello world!!", new String(content));

        //Second file
        attributes.put(CoreAttributes.FILENAME.key(), "secondFile.txt");
        runner.enqueue("Hello world!!".getBytes(), attributes);
        runner.run();
        runner.assertTransferCount(PutFile.REL_FAILURE, 1);
        runner.assertPenalizeCount(1);
    }

    @Test
    public void testReplaceAndMaxFileLimitReach() throws IOException {
        final TestRunner runner = TestRunners.newTestRunner(new PutFile());
        runner.setProperty(PutFile.DIRECTORY, targetDir.getAbsolutePath());
        runner.setProperty(PutFile.CONFLICT_RESOLUTION, PutFile.REPLACE_RESOLUTION);
        runner.setProperty(PutFile.MAX_DESTINATION_FILES, "1");

        Map<String, String> attributes = new HashMap<>();
        attributes.put(CoreAttributes.FILENAME.key(), "targetFile.txt");
        runner.enqueue("Hello world!!".getBytes(), attributes);
        runner.run();
        runner.assertAllFlowFilesTransferred(FetchFile.REL_SUCCESS, 1);
        Path targetPath = Paths.get(TARGET_DIRECTORY + "/targetFile.txt");
        byte[] content = Files.readAllBytes(targetPath);
        assertEquals("Hello world!!", new String(content));

        //Second file
        attributes.put(CoreAttributes.FILENAME.key(), "targetFile.txt");
        runner.enqueue("Another file".getBytes(), attributes);
        runner.run();
        runner.assertTransferCount(FetchFile.REL_SUCCESS, 2);
        File dir = new File(TARGET_DIRECTORY);
        assertEquals(1, dir.list().length);
        targetPath = Paths.get(TARGET_DIRECTORY + "/targetFile.txt");
        content = Files.readAllBytes(targetPath);
        assertEquals("Another file", new String(content));
    }

    private TestRunner putFileRunner;

    private final String testFile = "src" + File.separator + "test" + File.separator + "resources" + File.separator + "hello.txt";

    @BeforeEach
    public void setup() throws IOException {

        putFileRunner = TestRunners.newTestRunner(PutFile.class);
        putFileRunner.setProperty(PutFile.CHANGE_OWNER, System.getProperty("user.name"));
        putFileRunner.setProperty(PutFile.CHANGE_PERMISSIONS, "rw-r-----");
        putFileRunner.setProperty(PutFile.CREATE_DIRS, "true");
        putFileRunner.setProperty(PutFile.DIRECTORY, "target/test/data/out/PutFile/1/2/3/4/5");

        putFileRunner.setValidateExpressionUsage(false);
    }

    @AfterEach
    public void tearDown() throws IOException {
        emptyTestDirectory();
    }

    @Test
    public void testPutFile() throws IOException {
        emptyTestDirectory();

        Map<String, String> attributes = new HashMap<>();
        attributes.put("filename", "testfile.txt");

        putFileRunner.enqueue(Paths.get(testFile), attributes);
        putFileRunner.run();

        putFileRunner.assertTransferCount(PutSFTP.REL_SUCCESS, 1);

        //verify directory exists
        Path newDirectory = Paths.get("target/test/data/out/PutFile/1/2/3/4/5");
        Path newFile = newDirectory.resolve("testfile.txt");
        assertTrue(newDirectory.toAbsolutePath().toFile().exists(), "New directory not created.");
        assertTrue(newFile.toAbsolutePath().toFile().exists(), "New File not created.");

        PosixFileAttributeView filePosixAttributeView = Files.getFileAttributeView(newFile.toAbsolutePath(), PosixFileAttributeView.class);
        assertEquals(System.getProperty("user.name"), filePosixAttributeView.getOwner().getName());
        Set<PosixFilePermission> filePermissions = filePosixAttributeView.readAttributes().permissions();
        assertTrue(filePermissions.contains(PosixFilePermission.OWNER_READ));
        assertTrue(filePermissions.contains(PosixFilePermission.OWNER_WRITE));
        assertFalse(filePermissions.contains(PosixFilePermission.OWNER_EXECUTE));
        assertTrue(filePermissions.contains(PosixFilePermission.GROUP_READ));
        assertFalse(filePermissions.contains(PosixFilePermission.GROUP_WRITE));
        assertFalse(filePermissions.contains(PosixFilePermission.GROUP_EXECUTE));
        assertFalse(filePermissions.contains(PosixFilePermission.OTHERS_READ));
        assertFalse(filePermissions.contains(PosixFilePermission.OTHERS_WRITE));
        assertFalse(filePermissions.contains(PosixFilePermission.OTHERS_EXECUTE));

        PosixFileAttributeView dirPosixAttributeView = Files.getFileAttributeView(newDirectory.toAbsolutePath(), PosixFileAttributeView.class);
        assertEquals(System.getProperty("user.name"), dirPosixAttributeView.getOwner().getName());
        Set<PosixFilePermission> dirPermissions = dirPosixAttributeView.readAttributes().permissions();
        assertTrue(dirPermissions.contains(PosixFilePermission.OWNER_READ));
        assertTrue(dirPermissions.contains(PosixFilePermission.OWNER_WRITE));
        assertTrue(dirPermissions.contains(PosixFilePermission.OWNER_EXECUTE));
        assertTrue(dirPermissions.contains(PosixFilePermission.GROUP_READ));
        assertFalse(dirPermissions.contains(PosixFilePermission.GROUP_WRITE));
        assertTrue(dirPermissions.contains(PosixFilePermission.GROUP_EXECUTE));
        assertFalse(dirPermissions.contains(PosixFilePermission.OTHERS_READ));
        assertFalse(dirPermissions.contains(PosixFilePermission.OTHERS_WRITE));
        assertFalse(dirPermissions.contains(PosixFilePermission.OTHERS_EXECUTE));

        putFileRunner.clearTransferState();
    }


    private void emptyTestDirectory() throws IOException {
        Files.walkFileTree(Paths.get("target/test/data/out/PutFile"), new FileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
