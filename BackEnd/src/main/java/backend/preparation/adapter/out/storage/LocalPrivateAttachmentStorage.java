package backend.preparation.adapter.out.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import backend.preparation.application.InvalidPatientAttachmentException;
import backend.preparation.application.InvalidPatientAttachmentException.Reason;
import backend.preparation.application.PatientAttachmentStorageUnavailableException;
import backend.preparation.application.port.in.PatientAttachmentUpload;
import backend.preparation.application.port.out.PrivateAttachmentStorage;
import backend.preparation.application.port.out.StagedAttachment;
import backend.preparation.application.port.out.StagedAttachmentDeletion;
import backend.preparation.domain.AttachmentDigest;
import backend.preparation.domain.AttachmentMediaType;
import backend.preparation.domain.AttachmentStorageKey;
import backend.preparation.domain.PreparationAttachment;

@Component
public final class LocalPrivateAttachmentStorage implements PrivateAttachmentStorage {

    private static final byte[] PDF_PREFIX = { '%', 'P', 'D', 'F', '-' };
    private static final byte[] PDF_SUFFIX = { '%', '%', 'E', 'O', 'F' };
    private static final byte[] JPEG_PREFIX = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final byte[] JPEG_SUFFIX = { (byte) 0xFF, (byte) 0xD9 };
    private static final byte[] PNG_PREFIX = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
    private static final byte[] PNG_SUFFIX = {
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
            (byte) 0xAE, 0x42, 0x60, (byte) 0x82 };
    private static final int TAIL_CAPACITY = 2_048;
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);

    private final Path root;
    private final Path staging;
    private final Path pendingDeletions;
    private final long maxSizeBytes;

    public LocalPrivateAttachmentStorage(
            @Value("${app.preparation.attachment-storage-dir}") String storageDirectory,
            @Value("${app.preparation.attachment-max-size-bytes:10485760}") long maxSizeBytes) {
        if (storageDirectory == null || storageDirectory.isBlank()) {
            throw new IllegalArgumentException("Preparation attachment storage directory is required");
        }
        if (maxSizeBytes <= 0 || maxSizeBytes > PreparationAttachment.ABSOLUTE_MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Preparation attachment size limit is invalid");
        }
        this.root = Paths.get(storageDirectory).toAbsolutePath().normalize();
        this.staging = root.resolve(".staging");
        this.pendingDeletions = root.resolve(".pending-deletions");
        this.maxSizeBytes = maxSizeBytes;
    }

    @Override
    public StagedAttachment stage(PatientAttachmentUpload upload) {
        if (upload == null) {
            throw invalid(Reason.BAD_REQUEST);
        }
        long declaredSize = upload.declaredSizeBytes();
        if (declaredSize <= 0) {
            throw invalid(Reason.BAD_REQUEST);
        }
        if (declaredSize > maxSizeBytes) {
            throw invalid(Reason.TOO_LARGE);
        }
        String declaredContentType = normalizeContentType(upload.declaredContentType());
        if (declaredContentType == null) {
            throw invalid(Reason.UNSUPPORTED_MEDIA_TYPE);
        }

        AttachmentStorageKey storageKey = new AttachmentStorageKey(UUID.randomUUID());
        Path stagedPath = stagingPath(storageKey);
        try {
            ensureStorageDirectories();
            StoredBytes storedBytes = writeAndInspect(upload, stagedPath);
            if (storedBytes.sizeBytes() != declaredSize) {
                throw invalid(Reason.BAD_REQUEST);
            }
            AttachmentMediaType mediaType = detectMediaType(storedBytes.firstBytes(), storedBytes.tailBytes());
            if (!mediaType.contentType().equals(declaredContentType)) {
                throw invalid(Reason.UNSUPPORTED_MEDIA_TYPE);
            }
            String displayFilename = sanitizeDisplayFilename(upload.originalFilename(), mediaType);
            return new StagedAttachment(
                    storageKey,
                    displayFilename,
                    mediaType,
                    storedBytes.sizeBytes(),
                    storedBytes.digest());
        } catch (InvalidPatientAttachmentException exception) {
            deleteQuietly(stagedPath, exception);
            throw exception;
        } catch (IOException | RuntimeException exception) {
            deleteQuietly(stagedPath, exception);
            if (exception instanceof PatientAttachmentStorageUnavailableException unavailable) {
                throw unavailable;
            }
            throw new PatientAttachmentStorageUnavailableException(exception);
        }
    }

    @Override
    public void promote(StagedAttachment stagedAttachment) {
        if (stagedAttachment == null) {
            throw invalid(Reason.BAD_REQUEST);
        }
        Path source = stagingPath(stagedAttachment.storageKey());
        Path target = finalPath(stagedAttachment.storageKey());
        try {
            ensureStorageDirectories();
            requireRegularFile(source);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Attachment storage target already exists");
            }
            move(source, target);
            setFilePermissions(target);
        } catch (IOException exception) {
            throw new PatientAttachmentStorageUnavailableException(exception);
        }
    }

    @Override
    public void discardStaged(StagedAttachment stagedAttachment) {
        if (stagedAttachment != null) {
            discardStagedKey(stagedAttachment.storageKey());
        }
    }

    @Override
    public void deleteFinal(AttachmentStorageKey storageKey) {
        Path path = finalPath(storageKey);
        try {
            deleteRegularFileIfPresent(path);
        } catch (IOException exception) {
            throw new PatientAttachmentStorageUnavailableException(exception);
        }
    }

    @Override
    public StagedAttachmentDeletion stageDeletion(AttachmentStorageKey storageKey) {
        Path source = finalPath(storageKey);
        Path stagedDeletion = pendingDeletionPath(storageKey);
        try {
            ensureStorageDirectories();
            if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
                return new StagedAttachmentDeletion(storageKey, false);
            }
            requireRegularFile(source);
            if (Files.exists(stagedDeletion, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Attachment deletion target already exists");
            }
            move(source, stagedDeletion);
            return new StagedAttachmentDeletion(storageKey, true);
        } catch (IOException exception) {
            throw new PatientAttachmentStorageUnavailableException(exception);
        }
    }

    @Override
    public void restoreStagedDeletion(StagedAttachmentDeletion deletion) {
        if (deletion == null || !deletion.existed()) {
            return;
        }
        Path source = pendingDeletionPath(deletion.storageKey());
        Path target = finalPath(deletion.storageKey());
        try {
            requireRegularFile(source);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Attachment restore target already exists");
            }
            move(source, target);
        } catch (IOException exception) {
            throw new PatientAttachmentStorageUnavailableException(exception);
        }
    }

    @Override
    public void finalizeStagedDeletion(StagedAttachmentDeletion deletion) {
        if (deletion == null || !deletion.existed()) {
            return;
        }
        Path path = pendingDeletionPath(deletion.storageKey());
        try {
            deleteRegularFileIfPresent(path);
        } catch (IOException exception) {
            throw new PatientAttachmentStorageUnavailableException(exception);
        }
    }

    List<AttachmentStorageKey> stagedKeysOlderThan(Instant cutoff) {
        return keysOlderThan(staging, cutoff);
    }

    List<AttachmentStorageKey> finalKeysOlderThan(Instant cutoff) {
        return keysOlderThan(root, cutoff);
    }

    List<AttachmentStorageKey> pendingDeletionKeys() {
        return keysOlderThan(pendingDeletions, Instant.MAX);
    }

    boolean finalFileExists(AttachmentStorageKey storageKey) {
        Path path = finalPath(storageKey);
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path);
    }

    void discardStagedKey(AttachmentStorageKey storageKey) {
        try {
            deleteRegularFileIfPresent(stagingPath(storageKey));
        } catch (IOException exception) {
            throw new PatientAttachmentStorageUnavailableException(exception);
        }
    }

    void initializeAndValidate() {
        try {
            ensureStorageDirectories();
            if (!Files.isWritable(root)
                    || !Files.isWritable(staging)
                    || !Files.isWritable(pendingDeletions)) {
                throw new IOException("Preparation attachment storage directory is not writable");
            }
        } catch (IOException exception) {
            throw new PatientAttachmentStorageUnavailableException(exception);
        }
    }

    private StoredBytes writeAndInspect(PatientAttachmentUpload upload, Path target) throws IOException {
        MessageDigest messageDigest = sha256();
        byte[] first = new byte[PNG_PREFIX.length];
        int firstLength = 0;
        TailBuffer tail = new TailBuffer(TAIL_CAPACITY);
        long total = 0;

        try (InputStream input = upload.openStream();
                OutputStream output = Files.newOutputStream(
                        target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            if (input == null) {
                throw invalid(Reason.BAD_REQUEST);
            }
            setFilePermissions(target);
            byte[] buffer = new byte[8_192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                total += read;
                if (total > maxSizeBytes) {
                    throw invalid(Reason.TOO_LARGE);
                }
                int firstCopy = Math.min(read, first.length - firstLength);
                if (firstCopy > 0) {
                    System.arraycopy(buffer, 0, first, firstLength, firstCopy);
                    firstLength += firstCopy;
                }
                tail.append(buffer, read);
                messageDigest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        if (total == 0) {
            throw invalid(Reason.BAD_REQUEST);
        }
        return new StoredBytes(
                Arrays.copyOf(first, firstLength),
                tail.bytes(),
                total,
                new AttachmentDigest(HexFormat.of().formatHex(messageDigest.digest())));
    }

    private AttachmentMediaType detectMediaType(byte[] first, byte[] tail) {
        if (startsWith(first, PDF_PREFIX) && contains(tail, PDF_SUFFIX)) {
            return AttachmentMediaType.PDF;
        }
        if (startsWith(first, JPEG_PREFIX) && endsWith(tail, JPEG_SUFFIX)) {
            return AttachmentMediaType.JPEG;
        }
        if (startsWith(first, PNG_PREFIX) && endsWith(tail, PNG_SUFFIX)) {
            return AttachmentMediaType.PNG;
        }
        throw invalid(Reason.UNSUPPORTED_MEDIA_TYPE);
    }

    private String sanitizeDisplayFilename(String originalFilename, AttachmentMediaType mediaType) {
        String candidate = originalFilename == null ? "" : originalFilename.replace('\\', '/');
        int separator = candidate.lastIndexOf('/');
        candidate = separator >= 0 ? candidate.substring(separator + 1) : candidate;
        candidate = Normalizer.normalize(candidate, Normalizer.Form.NFC)
                .codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .filter(codePoint -> Character.getType(codePoint) != Character.FORMAT)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .trim();
        while (candidate.endsWith(".") || candidate.endsWith(" ")) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        if (candidate.isBlank() || candidate.equals(".") || candidate.equals("..")) {
            candidate = "document";
        }
        int extensionIndex = candidate.lastIndexOf('.');
        if (extensionIndex > 0) {
            candidate = candidate.substring(0, extensionIndex);
        }
        if (candidate.isBlank()) {
            candidate = "document";
        }
        String extension = mediaType.canonicalExtension();
        int maxBaseCodePoints = PreparationAttachment.MAX_DISPLAY_FILENAME_LENGTH - extension.length();
        candidate = truncateCodePoints(candidate, maxBaseCodePoints).trim();
        if (candidate.isBlank()) {
            candidate = "document";
        }
        return candidate + extension;
    }

    private String truncateCodePoints(String value, int maxCodePoints) {
        int count = value.codePointCount(0, value.length());
        if (count <= maxCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        int parameter = contentType.indexOf(';');
        String normalized = (parameter >= 0 ? contentType.substring(0, parameter) : contentType)
                .trim()
                .toLowerCase(Locale.ROOT);
        return Arrays.stream(AttachmentMediaType.values())
                .map(AttachmentMediaType::contentType)
                .filter(normalized::equals)
                .findFirst()
                .orElse(null);
    }

    private List<AttachmentStorageKey> keysOlderThan(Path directory, Instant cutoff) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) {
            return List.of();
        }
        try (var paths = Files.list(directory)) {
            return paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> lastModified(path).isBefore(cutoff))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .map(this::parseStorageKey)
                    .flatMap(java.util.Optional::stream)
                    .sorted((left, right) -> left.value().compareTo(right.value()))
                    .toList();
        } catch (IOException exception) {
            throw new PatientAttachmentStorageUnavailableException(exception);
        }
    }

    private java.util.Optional<AttachmentStorageKey> parseStorageKey(String value) {
        try {
            return java.util.Optional.of(new AttachmentStorageKey(UUID.fromString(value)));
        } catch (IllegalArgumentException exception) {
            return java.util.Optional.empty();
        }
    }

    private Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant();
        } catch (IOException exception) {
            throw new PatientAttachmentStorageUnavailableException(exception);
        }
    }

    private void ensureStorageDirectories() throws IOException {
        ensureDirectory(root);
        ensureDirectory(staging);
        ensureDirectory(pendingDeletions);
    }

    private void ensureDirectory(Path directory) throws IOException {
        Files.createDirectories(directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) {
            throw new IOException("Attachment storage directory is unsafe");
        }
        setDirectoryPermissions(directory);
    }

    private void requireRegularFile(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("Attachment storage object is unavailable");
        }
    }

    private void deleteRegularFileIfPresent(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        requireRegularFile(path);
        Files.delete(path);
    }

    private Path finalPath(AttachmentStorageKey storageKey) {
        return resolveDirectChild(root, storageKey);
    }

    private Path stagingPath(AttachmentStorageKey storageKey) {
        return resolveDirectChild(staging, storageKey);
    }

    private Path pendingDeletionPath(AttachmentStorageKey storageKey) {
        return resolveDirectChild(pendingDeletions, storageKey);
    }

    private Path resolveDirectChild(Path parent, AttachmentStorageKey storageKey) {
        if (storageKey == null) {
            throw invalid(Reason.BAD_REQUEST);
        }
        Path resolved = parent.resolve(storageKey.value().toString()).normalize();
        if (!parent.equals(resolved.getParent())) {
            throw invalid(Reason.BAD_REQUEST);
        }
        return resolved;
    }

    private void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void setDirectoryPermissions(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, DIRECTORY_PERMISSIONS);
        } catch (UnsupportedOperationException ignored) {
            // The filesystem does not expose POSIX permissions; deployment controls still apply.
        }
    }

    private void setFilePermissions(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, FILE_PERMISSIONS);
        } catch (UnsupportedOperationException ignored) {
            // The filesystem does not expose POSIX permissions; deployment controls still apply.
        }
    }

    private void deleteQuietly(Path path, Throwable original) {
        try {
            deleteRegularFileIfPresent(path);
        } catch (IOException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
        }
    }

    private boolean startsWith(byte[] source, byte[] prefix) {
        return source.length >= prefix.length
                && Arrays.equals(Arrays.copyOf(source, prefix.length), prefix);
    }

    private boolean endsWith(byte[] source, byte[] suffix) {
        return source.length >= suffix.length
                && Arrays.equals(
                        Arrays.copyOfRange(source, source.length - suffix.length, source.length), suffix);
    }

    private boolean contains(byte[] source, byte[] sequence) {
        for (int index = 0; index <= source.length - sequence.length; index++) {
            boolean match = true;
            for (int offset = 0; offset < sequence.length; offset++) {
                if (source[index + offset] != sequence[offset]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    private InvalidPatientAttachmentException invalid(Reason reason) {
        return new InvalidPatientAttachmentException(reason);
    }

    private record StoredBytes(
            byte[] firstBytes,
            byte[] tailBytes,
            long sizeBytes,
            AttachmentDigest digest) {
    }

    private static final class TailBuffer {

        private final byte[] buffer;
        private int position;
        private int length;

        private TailBuffer(int capacity) {
            buffer = new byte[capacity];
        }

        private void append(byte[] source, int sourceLength) {
            for (int index = 0; index < sourceLength; index++) {
                buffer[position] = source[index];
                position = (position + 1) % buffer.length;
                length = Math.min(length + 1, buffer.length);
            }
        }

        private byte[] bytes() {
            byte[] result = new byte[length];
            int start = (position - length + buffer.length) % buffer.length;
            for (int index = 0; index < length; index++) {
                result[index] = buffer[(start + index) % buffer.length];
            }
            return result;
        }
    }
}
