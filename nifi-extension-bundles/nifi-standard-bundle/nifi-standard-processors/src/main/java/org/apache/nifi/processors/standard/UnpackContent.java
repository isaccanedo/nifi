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

import java.nio.charset.Charset;
import net.lingala.zip4j.io.inputstream.ZipInputStream;
import net.lingala.zip4j.model.LocalFileHeader;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.Charsets;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.SideEffectFree;
import org.apache.nifi.annotation.behavior.SupportsBatching;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.documentation.UseCase;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.lifecycle.OnStopped;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.PropertyValue;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.flowfile.attributes.FragmentAttributes;
import org.apache.nifi.flowfile.attributes.StandardFlowFileMediaType;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.processor.util.file.transfer.FileInfo;
import org.apache.nifi.stream.io.StreamUtils;
import org.apache.nifi.util.FlowFileUnpackager;
import org.apache.nifi.util.FlowFileUnpackagerV1;
import org.apache.nifi.util.FlowFileUnpackagerV2;
import org.apache.nifi.util.FlowFileUnpackagerV3;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@SideEffectFree
@SupportsBatching
@InputRequirement(Requirement.INPUT_REQUIRED)
@Tags({"Unpack", "un-merge", "tar", "zip", "archive", "flowfile-stream", "flowfile-stream-v3"})
@CapabilityDescription("Unpacks the content of FlowFiles that have been packaged with one of several different Packaging Formats, emitting one to many "
        + "FlowFiles for each input FlowFile. Supported formats are TAR, ZIP, and FlowFile Stream packages.")
@ReadsAttribute(attribute = "mime.type", description = "If the <Packaging Format> property is set to use mime.type attribute, this attribute is used "
        + "to determine the FlowFile's MIME Type. In this case, if the attribute is set to application/tar, the TAR Packaging Format will be used. If "
        + "the attribute is set to application/zip, the ZIP Packaging Format will be used. If the attribute is set to application/flowfile-v3 or "
        + "application/flowfile-v2 or application/flowfile-v1, the appropriate FlowFile Packaging Format will be used. If this attribute is missing, "
        + "the FlowFile will be routed to 'failure'. Otherwise, if the attribute's value is not one of those mentioned above, the FlowFile will be "
        + "routed to 'success' without being unpacked. Use the File Filter property only extract files matching a specific regular expression.")
@WritesAttributes({
    @WritesAttribute(attribute = "mime.type", description = "If the FlowFile is successfully unpacked, its MIME Type is no longer known, so the mime.type "
            + "attribute is set to application/octet-stream."),
    @WritesAttribute(attribute = "fragment.identifier", description = "All unpacked FlowFiles produced from the same parent FlowFile will have the same randomly generated "
            + "UUID added for this attribute"),
    @WritesAttribute(attribute = "fragment.index", description = "A one-up number that indicates the ordering of the unpacked FlowFiles that were created from a single "
            + "parent FlowFile"),
    @WritesAttribute(attribute = "fragment.count", description = "The number of unpacked FlowFiles generated from the parent FlowFile"),
    @WritesAttribute(attribute = "segment.original.filename ", description = "The filename of the parent FlowFile. Extensions of .tar, .zip or .pkg are removed because "
            + "the MergeContent processor automatically adds those extensions if it is used to rebuild the original FlowFile"),
    @WritesAttribute(attribute = "file.lastModifiedTime", description = "The date and time that the unpacked file was last modified (tar only)."),
    @WritesAttribute(attribute = "file.creationTime", description = "The date and time that the file was created. This attribute holds always the same value as file.lastModifiedTime (tar only)."),
    @WritesAttribute(attribute = "file.owner", description = "The owner of the unpacked file (tar only)"),
    @WritesAttribute(attribute = "file.group", description = "The group owner of the unpacked file (tar only)"),
    @WritesAttribute(attribute = "file.permissions", description = "The read/write/execute permissions of the unpacked file (tar only)"),
    @WritesAttribute(attribute = "file.encryptionMethod", description = "The encryption method for entries in Zip archives")})
@SeeAlso(MergeContent.class)
@UseCase(
    description = "Unpack Zip containing filenames with special characters, created on Windows with filename charset 'Cp437' or 'IBM437'.",
    configuration = """
        Set "Packaging Format" value to "zip" or "use mime.type attribute".
        Set "Filename Character Set" value to "Cp437" or "IBM437".
        """
)
public class UnpackContent extends AbstractProcessor {
    // attribute keys
    public static final String FRAGMENT_ID = FragmentAttributes.FRAGMENT_ID.key();
    public static final String FRAGMENT_INDEX = FragmentAttributes.FRAGMENT_INDEX.key();
    public static final String FRAGMENT_COUNT = FragmentAttributes.FRAGMENT_COUNT.key();
    public static final String SEGMENT_ORIGINAL_FILENAME = FragmentAttributes.SEGMENT_ORIGINAL_FILENAME.key();

    public static final String AUTO_DETECT_FORMAT_NAME = "use mime.type attribute";
    public static final String TAR_FORMAT_NAME = "tar";
    public static final String ZIP_FORMAT_NAME = "zip";
    public static final String FLOWFILE_STREAM_FORMAT_V3_NAME = "flowfile-stream-v3";
    public static final String FLOWFILE_STREAM_FORMAT_V2_NAME = "flowfile-stream-v2";
    public static final String FLOWFILE_TAR_FORMAT_NAME = "flowfile-tar-v1";

    public static final String OCTET_STREAM = "application/octet-stream";

    public static final String FILE_LAST_MODIFIED_TIME_ATTRIBUTE = "file.lastModifiedTime";
    public static final String FILE_CREATION_TIME_ATTRIBUTE = "file.creationTime";
    public static final String FILE_OWNER_ATTRIBUTE = "file.owner";
    public static final String FILE_GROUP_ATTRIBUTE = "file.group";
    public static final String FILE_PERMISSIONS_ATTRIBUTE = "file.permissions";
    public static final String FILE_ENCRYPTION_METHOD_ATTRIBUTE = "file.encryptionMethod";

    public static final String FILE_MODIFIED_DATE_ATTR_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(FILE_MODIFIED_DATE_ATTR_FORMAT).withZone(ZoneId.systemDefault());

    public static final PropertyDescriptor PACKAGING_FORMAT = new PropertyDescriptor.Builder()
            .name("Packaging Format")
            .description("The Packaging Format used to create the file")
            .required(true)
            .allowableValues(PackageFormat.AUTO_DETECT_FORMAT.toString(), PackageFormat.TAR_FORMAT.toString(),
                    PackageFormat.ZIP_FORMAT.toString(), PackageFormat.FLOWFILE_STREAM_FORMAT_V3.toString(),
                    PackageFormat.FLOWFILE_STREAM_FORMAT_V2.toString(), PackageFormat.FLOWFILE_TAR_FORMAT.toString())
            .defaultValue(PackageFormat.AUTO_DETECT_FORMAT.toString())
            .build();
    public static final PropertyDescriptor ZIP_FILENAME_CHARSET = new PropertyDescriptor.Builder()
            .name("Filename Character Set")
            .displayName("Filename Character Set")
            .description(
                "If supplied this character set will be supplied to the Zip utility to attempt to decode filenames using the specific character set. "
                    + "If not specified the default platform character set will be used. This is useful if a Zip was created with a different character "
                    + "set than the platform default and the zip uses non standard values to specify.")
            .required(false)
            .dependsOn(
                PACKAGING_FORMAT,
                PackageFormat.ZIP_FORMAT.toString(),
                PackageFormat.AUTO_DETECT_FORMAT.toString())
            .addValidator(StandardValidators.CHARACTER_SET_VALIDATOR)
            .defaultValue(Charset.defaultCharset().toString())
            .build();

    public static final PropertyDescriptor FILE_FILTER = new PropertyDescriptor.Builder()
            .name("File Filter")
            .description("Only files contained in the archive whose names match the given regular expression will be extracted (tar/zip only)")
            .required(true)
            .defaultValue(".*")
            .addValidator(StandardValidators.REGULAR_EXPRESSION_VALIDATOR)
            .build();

    public static final PropertyDescriptor PASSWORD = new PropertyDescriptor.Builder()
            .name("Password")
            .displayName("Password")
            .description("Password used for decrypting Zip archives encrypted with ZipCrypto or AES. Configuring a password disables support for alternative Zip compression algorithms.")
            .required(false)
            .sensitive(true)
            .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
            .build();

    public static final PropertyDescriptor ALLOW_STORED_ENTRIES_WITH_DATA_DESCRIPTOR = new PropertyDescriptor.Builder()
            .name("allow-stored-entries-wdd")
            .displayName("Allow Stored Entries With Data Descriptor")
            .description("Some zip archives contain stored entries with data descriptors which by spec should not " +
                    "happen.  If this property is true they will be read anyway.  If false and such an entry is discovered " +
                    "the zip will fail to process.")
            .required(true)
            .defaultValue("false")
            .sensitive(false)
            .allowableValues("true", "false")
            .dependsOn(PACKAGING_FORMAT, PackageFormat.ZIP_FORMAT.toString())
            .addValidator(StandardValidators.BOOLEAN_VALIDATOR)
            .build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("Unpacked FlowFiles are sent to this relationship")
            .build();
    public static final Relationship REL_ORIGINAL = new Relationship.Builder()
            .name("original")
            .description("The original FlowFile is sent to this relationship after it has been successfully unpacked")
            .build();
    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("The original FlowFile is sent to this relationship when it cannot be unpacked for some reason")
            .build();

    private static final Set<Relationship> relationships = Set.of(
        REL_SUCCESS,
        REL_FAILURE,
        REL_ORIGINAL
    );

    private static final List<PropertyDescriptor> properties = List.of(
        PACKAGING_FORMAT,
        ZIP_FILENAME_CHARSET,
        FILE_FILTER,
        PASSWORD,
        ALLOW_STORED_ENTRIES_WITH_DATA_DESCRIPTOR
    );

    private Pattern fileFilter;

    private Unpacker tarUnpacker;
    private Unpacker zipUnpacker;


    @Override
    public Set<Relationship> getRelationships() {
        return relationships;
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return properties;
    }

    @OnStopped
    public void onStopped() {
        fileFilter = null;
    }

    @OnScheduled
    public void onScheduled(ProcessContext context) throws ProcessException {
        if (fileFilter == null) {
            fileFilter = Pattern.compile(context.getProperty(FILE_FILTER).getValue());
            tarUnpacker = new TarUnpacker(fileFilter);

            char[] password = null;
            final PropertyValue passwordProperty = context.getProperty(PASSWORD);
            if (passwordProperty.isSet()) {
                password = passwordProperty.getValue().toCharArray();
            }
            final PropertyValue allowStoredEntriesWithDataDescriptorVal = context.getProperty(ALLOW_STORED_ENTRIES_WITH_DATA_DESCRIPTOR);
            final boolean allowStoredEntriesWithDataDescriptor = allowStoredEntriesWithDataDescriptorVal.isSet() ? allowStoredEntriesWithDataDescriptorVal.asBoolean() : false;

            final String filenamesEncodingVal = context.getProperty(ZIP_FILENAME_CHARSET).getValue();
            Charset filenamesEncoding = Charsets.toCharset(filenamesEncodingVal);
            zipUnpacker = new ZipUnpacker(fileFilter, password, allowStoredEntriesWithDataDescriptor, filenamesEncoding);
        }
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
        FlowFile flowFile = session.get();
        if (flowFile == null) {
            return;
        }

        final ComponentLog logger = getLogger();
        PackageFormat packagingFormat = PackageFormat.getFormat(context.getProperty(PACKAGING_FORMAT).getValue().toLowerCase());
        if (packagingFormat == PackageFormat.AUTO_DETECT_FORMAT) {
            packagingFormat = null;
            final String mimeType = flowFile.getAttribute(CoreAttributes.MIME_TYPE.key());
            if (mimeType == null) {
                logger.error("No mime.type attribute set for {}; routing to failure", flowFile);
                session.transfer(flowFile, REL_FAILURE);
                return;
            }

            for (PackageFormat format: PackageFormat.values()) {
                if (mimeType.toLowerCase().equals(format.getMimeType())) {
                    packagingFormat = format;
                }
            }
            if (packagingFormat == null) {
                logger.info("Cannot unpack {} because its mime.type attribute is set to '{}', which is not a format that can be unpacked; routing to 'success'", flowFile, mimeType);
                session.transfer(flowFile, REL_SUCCESS);
                return;
            }
        }

        // set the Unpacker to use for this FlowFile.  FlowFileUnpackager objects maintain state and are not reusable.
        final Unpacker unpacker;
        final boolean addFragmentAttrs = switch (packagingFormat) {
          case TAR_FORMAT, X_TAR_FORMAT -> {
            unpacker = tarUnpacker;
            yield true;
          }
          case ZIP_FORMAT -> {
            unpacker = zipUnpacker;
            yield true;
          }
          case FLOWFILE_STREAM_FORMAT_V2 -> {
            unpacker = new FlowFileStreamUnpacker(new FlowFileUnpackagerV2());
            yield false;
          }
          case FLOWFILE_STREAM_FORMAT_V3 -> {
            unpacker = new FlowFileStreamUnpacker(new FlowFileUnpackagerV3());
            yield false;
          }
          case FLOWFILE_TAR_FORMAT -> {
            unpacker = new FlowFileStreamUnpacker(new FlowFileUnpackagerV1());
            yield false;
          }
          default ->
            // The format of the unpacker should be known before initialization
            throw new ProcessException(packagingFormat + " is not a valid packaging format");
        };

      final List<FlowFile> unpacked = new ArrayList<>();
        try {
            unpacker.unpack(session, flowFile, unpacked);
            if (unpacked.isEmpty()) {
                logger.error("Unable to unpack {} because it does not appear to have any entries; routing to failure", flowFile);
                session.transfer(flowFile, REL_FAILURE);
                return;
            }

            if (addFragmentAttrs) {
                finishFragmentAttributes(session, flowFile, unpacked);
            }
            session.transfer(unpacked, REL_SUCCESS);
            final String fragmentId = !unpacked.isEmpty() ? unpacked.getFirst().getAttribute(FRAGMENT_ID) : null;
            flowFile = FragmentAttributes.copyAttributesToOriginal(session, flowFile, fragmentId, unpacked.size());
            session.transfer(flowFile, REL_ORIGINAL);
            session.getProvenanceReporter().fork(flowFile, unpacked);
            logger.info("Unpacked {} into {} and transferred to success", flowFile, unpacked);
        } catch (final Exception e) {
            logger.error("Unable to unpack {}; routing to failure", flowFile, e);
            session.transfer(flowFile, REL_FAILURE);
            session.remove(unpacked);
        }
    }

    private static abstract class Unpacker {
        protected Pattern fileFilter = null;

        public Unpacker() { }

        public Unpacker(final Pattern fileFilter) {
            this.fileFilter = fileFilter;
        }

        abstract void unpack(ProcessSession session, FlowFile source, List<FlowFile> unpacked);

        protected boolean fileMatches(final ArchiveEntry entry) {
            return fileMatches(entry.getName());
        }

        protected boolean fileMatches(final String entryName) {
            return fileFilter == null || fileFilter.matcher(entryName).find();
        }
    }

    private static class TarUnpacker extends Unpacker {
        public TarUnpacker(Pattern fileFilter) {
            super(fileFilter);
        }

        @Override
        public void unpack(final ProcessSession session, final FlowFile source, final List<FlowFile> unpacked) {
            final String fragmentId = UUID.randomUUID().toString();
            session.read(source, inputStream -> {
                int fragmentCount = 0;
                try (final TarArchiveInputStream tarIn = new TarArchiveInputStream(new BufferedInputStream(inputStream))) {
                    TarArchiveEntry tarEntry;
                    while ((tarEntry = tarIn.getNextEntry()) != null) {
                        if (tarEntry.isDirectory() || !fileMatches(tarEntry)) {
                            continue;
                        }
                        final File file = new File(tarEntry.getName());
                        final Path filePath = file.toPath();
                        String filePathString = filePath.getParent() == null ? "/" : filePath.getParent() + "/";

                        FlowFile unpackedFile = session.create(source);
                        try {
                            final Map<String, String> attributes = new HashMap<>();
                            attributes.put(CoreAttributes.FILENAME.key(), file.getName());
                            attributes.put(CoreAttributes.PATH.key(), filePathString);
                            attributes.put(CoreAttributes.MIME_TYPE.key(), OCTET_STREAM);

                            attributes.put(FILE_PERMISSIONS_ATTRIBUTE, FileInfo.permissionToString(tarEntry.getMode()));
                            attributes.put(FILE_OWNER_ATTRIBUTE, String.valueOf(tarEntry.getUserName()));
                            attributes.put(FILE_GROUP_ATTRIBUTE, String.valueOf(tarEntry.getGroupName()));

                            final String timeAsString = DATE_TIME_FORMATTER.format(tarEntry.getModTime().toInstant());
                            attributes.put(FILE_LAST_MODIFIED_TIME_ATTRIBUTE, timeAsString);
                            attributes.put(FILE_CREATION_TIME_ATTRIBUTE, timeAsString);

                            attributes.put(FRAGMENT_ID, fragmentId);
                            attributes.put(FRAGMENT_INDEX, String.valueOf(++fragmentCount));

                            unpackedFile = session.putAllAttributes(unpackedFile, attributes);

                            final long fileSize = tarEntry.getSize();
                            unpackedFile = session.write(unpackedFile, outputStream -> StreamUtils.copy(tarIn, outputStream, fileSize));
                        } finally {
                            unpacked.add(unpackedFile);
                        }
                    }
                }
            });
        }
    }

    private static class ZipUnpacker extends Unpacker {
        private final char[] password;
        private final boolean allowStoredEntriesWithDataDescriptor;
        private final Charset filenameEncoding;
        public ZipUnpacker(final Pattern fileFilter, final char[] password, final boolean allowStoredEntriesWithDataDescriptor, final Charset filenameEncoding) {
            super(fileFilter);
            this.password = password;
            this.allowStoredEntriesWithDataDescriptor = allowStoredEntriesWithDataDescriptor;
            this.filenameEncoding = filenameEncoding;
        }

        @Override
        public void unpack(final ProcessSession session, final FlowFile source, final List<FlowFile> unpacked) {
            final String fragmentId = UUID.randomUUID().toString();
            if (password == null) {
                session.read(source, new CompressedZipInputStreamCallback(fileFilter, session, source, unpacked, fragmentId, allowStoredEntriesWithDataDescriptor, filenameEncoding));
            } else {
                session.read(source, new EncryptedZipInputStreamCallback(fileFilter, session, source, unpacked, fragmentId, password, filenameEncoding));
            }
        }

        private abstract static class ZipInputStreamCallback implements InputStreamCallback {
            private static final String PATH_SEPARATOR = "/";

            private final Pattern fileFilter;

            private final ProcessSession session;

            private final FlowFile sourceFlowFile;

            private final List<FlowFile> unpacked;

            private final String fragmentId;

            private int fragmentIndex;

            private ZipInputStreamCallback(
                    final Pattern fileFilter,
                    final ProcessSession session,
                    final FlowFile sourceFlowFile,
                    final List<FlowFile> unpacked,
                    final String fragmentId
            ) {
                this.fileFilter = fileFilter;
                this.session = session;
                this.sourceFlowFile = sourceFlowFile;
                this.unpacked = unpacked;
                this.fragmentId = fragmentId;
            }

            protected boolean isFileEntryMatched(final boolean directory, final String fileName) {
                return !directory && (fileFilter == null || fileFilter.matcher(fileName).find());
            }

            protected void processEntry(final InputStream zipInputStream, final boolean directory, final String zipEntryName, final EncryptionMethod encryptionMethod) {
                if (isFileEntryMatched(directory, zipEntryName)) {
                    final File file = new File(zipEntryName);
                    final String parentDirectory = (file.getParent() == null) ? PATH_SEPARATOR : file.getParent();

                    FlowFile unpackedFile = session.create(sourceFlowFile);
                    try {
                        final Map<String, String> attributes = new HashMap<>();
                        attributes.put(CoreAttributes.FILENAME.key(), file.getName());
                        attributes.put(CoreAttributes.PATH.key(), parentDirectory);
                        attributes.put(CoreAttributes.MIME_TYPE.key(), OCTET_STREAM);
                        attributes.put(FILE_ENCRYPTION_METHOD_ATTRIBUTE, encryptionMethod.toString());

                        attributes.put(FRAGMENT_ID, fragmentId);
                        attributes.put(FRAGMENT_INDEX, String.valueOf(++fragmentIndex));

                        unpackedFile = session.putAllAttributes(unpackedFile, attributes);
                        unpackedFile = session.write(unpackedFile, outputStream -> StreamUtils.copy(zipInputStream, outputStream));
                    } finally {
                        unpacked.add(unpackedFile);
                    }
                }
            }
        }

        private static class CompressedZipInputStreamCallback extends ZipInputStreamCallback {

            private final boolean allowStoredEntriesWithDataDescriptor;
            private final Charset filenameEncoding;

            private CompressedZipInputStreamCallback(
                    final Pattern fileFilter,
                    final ProcessSession session,
                    final FlowFile sourceFlowFile,
                    final List<FlowFile> unpacked,
                    final String fragmentId,
                    final boolean allowStoredEntriesWithDataDescriptor,
                    final Charset filenameEncoding
            ) {
                super(fileFilter, session, sourceFlowFile, unpacked, fragmentId);
                this.allowStoredEntriesWithDataDescriptor = allowStoredEntriesWithDataDescriptor;
                this.filenameEncoding = filenameEncoding;
            }

            @Override
            public void process(final InputStream inputStream) throws IOException {
                try (final ZipArchiveInputStream zipInputStream = new ZipArchiveInputStream(new BufferedInputStream(inputStream),
                    filenameEncoding.toString(), true, allowStoredEntriesWithDataDescriptor)) {
                    ZipArchiveEntry zipEntry;
                    while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                        processEntry(zipInputStream, zipEntry.isDirectory(), zipEntry.getName(), EncryptionMethod.NONE);
                    }
                }
            }
        }

        private static class EncryptedZipInputStreamCallback extends ZipInputStreamCallback {
            private final char[] password;
            private final Charset filenameEncoding;

            private EncryptedZipInputStreamCallback(
                    final Pattern fileFilter,
                    final ProcessSession session,
                    final FlowFile sourceFlowFile,
                    final List<FlowFile> unpacked,
                    final String fragmentId,
                    final char[] password,
                    final Charset filenameEncoding
            ) {
                super(fileFilter, session, sourceFlowFile, unpacked, fragmentId);
                this.password = password;
                this.filenameEncoding = filenameEncoding;
            }

            @Override
            public void process(final InputStream inputStream) throws IOException {
                try (final ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(inputStream), password, filenameEncoding)) {
                    LocalFileHeader zipEntry;
                    while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                        processEntry(zipInputStream, zipEntry.isDirectory(), zipEntry.getFileName(), zipEntry.getEncryptionMethod());
                    }
                }
            }
        }
    }

    private static class FlowFileStreamUnpacker extends Unpacker {

        private final FlowFileUnpackager unpackager;

        public FlowFileStreamUnpacker(final FlowFileUnpackager unpackager) {
            this.unpackager = unpackager;
        }

        @Override
        public void unpack(final ProcessSession session, final FlowFile source, final List<FlowFile> unpacked) {
            session.read(source, inputStream -> {
                try (final InputStream in = new BufferedInputStream(inputStream)) {
                    while (unpackager.hasMoreData()) {
                        final AtomicReference<Map<String, String>> attributesRef = new AtomicReference<>(null);
                        FlowFile unpackedFile = session.create(source);
                        try {
                            unpackedFile = session.write(unpackedFile, outputStream -> {
                                try (final OutputStream out = new BufferedOutputStream(outputStream)) {
                                    final Map<String, String> attributes = unpackager.unpackageFlowFile(in, out);
                                    if (attributes == null) {
                                        throw new IOException("Failed to unpack " + source + ": stream had no Attributes");
                                    }
                                    attributesRef.set(attributes);
                                }
                            });

                            final Map<String, String> attributes = attributesRef.get();

                            // Remove the UUID from the attributes because we don't want to use the same UUID for this FlowFile.
                            // If we do, then we get into a weird situation if we use MergeContent to create a FlowFile Package
                            // and later unpack it -- in this case, we have two FlowFiles with the same UUID.
                            attributes.remove(CoreAttributes.UUID.key());

                            if (!attributes.containsKey(CoreAttributes.MIME_TYPE.key())) {
                                attributes.put(CoreAttributes.MIME_TYPE.key(), OCTET_STREAM);
                            }

                            unpackedFile = session.putAllAttributes(unpackedFile, attributes);
                        } finally {
                            unpacked.add(unpackedFile);
                        }
                    }
                }
            });
        }
    }


    private void finishFragmentAttributes(final ProcessSession session, final FlowFile source, final List<FlowFile> unpacked) {
        // first pass verifies all FlowFiles have the FRAGMENT_INDEX attribute and gets the total number of fragments
        int fragmentCount = 0;
        for (FlowFile ff : unpacked) {
            String fragmentIndex = ff.getAttribute(FRAGMENT_INDEX);
            if (fragmentIndex != null) {
                fragmentCount++;
            } else {
                return;
            }
        }

        String originalFilename = source.getAttribute(CoreAttributes.FILENAME.key());
        if (originalFilename.endsWith(".tar") || originalFilename.endsWith(".zip") || originalFilename.endsWith(".pkg")) {
            originalFilename = originalFilename.substring(0, originalFilename.length() - 4);
        }

        // second pass adds fragment attributes
        ArrayList<FlowFile> newList = new ArrayList<>(unpacked);
        unpacked.clear();
        for (FlowFile ff : newList) {
            final Map<String, String> attributes = new HashMap<>();
            attributes.put(FRAGMENT_COUNT, String.valueOf(fragmentCount));
            attributes.put(SEGMENT_ORIGINAL_FILENAME, originalFilename);
            FlowFile newFF = session.putAllAttributes(ff, attributes);
            unpacked.add(newFF);
        }
    }

    protected enum PackageFormat {
        AUTO_DETECT_FORMAT(AUTO_DETECT_FORMAT_NAME),
        TAR_FORMAT(TAR_FORMAT_NAME, "application/tar"),
        X_TAR_FORMAT(TAR_FORMAT_NAME, "application/x-tar"),
        ZIP_FORMAT(ZIP_FORMAT_NAME, "application/zip"),
        FLOWFILE_STREAM_FORMAT_V3(FLOWFILE_STREAM_FORMAT_V3_NAME, StandardFlowFileMediaType.VERSION_3.getMediaType()),
        FLOWFILE_STREAM_FORMAT_V2(FLOWFILE_STREAM_FORMAT_V2_NAME, StandardFlowFileMediaType.VERSION_2.getMediaType()),
        FLOWFILE_TAR_FORMAT(FLOWFILE_TAR_FORMAT_NAME, StandardFlowFileMediaType.VERSION_1.getMediaType());


        private final String textValue;
        private String mimeType;

        PackageFormat(String textValue, String mimeType) {
            this.textValue = textValue;
            this.mimeType = mimeType;
        }

        PackageFormat(String textValue) {
            this.textValue = textValue;
        }

        @Override public String toString() {
            return textValue;
        }

        public String getMimeType() {
            return mimeType;
        }

        public static PackageFormat getFormat(String textValue) {
            return switch (textValue) {
                case AUTO_DETECT_FORMAT_NAME -> AUTO_DETECT_FORMAT;
                case TAR_FORMAT_NAME -> TAR_FORMAT;
                case ZIP_FORMAT_NAME -> ZIP_FORMAT;
                case FLOWFILE_STREAM_FORMAT_V3_NAME -> FLOWFILE_STREAM_FORMAT_V3;
                case FLOWFILE_STREAM_FORMAT_V2_NAME -> FLOWFILE_STREAM_FORMAT_V2;
                case FLOWFILE_TAR_FORMAT_NAME -> FLOWFILE_TAR_FORMAT;
                default -> null;
            };
        }
    }
}
