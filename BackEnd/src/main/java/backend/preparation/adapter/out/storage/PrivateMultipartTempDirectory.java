package backend.preparation.adapter.out.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public final class PrivateMultipartTempDirectory {

    private static final Logger log = LoggerFactory.getLogger(PrivateMultipartTempDirectory.class);
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);

    private final Path directory;
    private final Duration retention;
    private final Clock clock;

    public PrivateMultipartTempDirectory(
            @Value("${spring.servlet.multipart.location}") String directory,
            @Value("${app.preparation.attachment-orphan-grace:PT1H}") Duration retention,
            Clock preparationClock) {
        if (directory == null || directory.isBlank()) {
            throw new IllegalArgumentException("Private multipart directory is required");
        }
        if (retention == null || retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("Private multipart retention must be positive");
        }
        this.directory = Paths.get(directory).toAbsolutePath().normalize();
        this.retention = retention;
        this.clock = preparationClock;
    }

    @PostConstruct
    void initialize() {
        int removed = 0;
        try {
            Files.createDirectories(directory);
            requireSafeDirectory();
            setDirectoryPermissions();
            var cutoff = clock.instant().minus(retention);
            try (var paths = Files.list(directory)) {
                for (Path path : paths.toList()) {
                    if (Files.isSymbolicLink(path)
                            || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Private multipart directory contains an unsafe entry");
                    }
                    if (Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff)) {
                        Files.delete(path);
                        removed++;
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Private multipart directory initialization failed closed", exception);
        }
        if (removed > 0) {
            log.info("Private multipart recovery removed {} abandoned files", removed);
        }
    }

    private void requireSafeDirectory() throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) {
            throw new IOException("Private multipart directory is unsafe");
        }
    }

    private void setDirectoryPermissions() throws IOException {
        try {
            Files.setPosixFilePermissions(directory, DIRECTORY_PERMISSIONS);
        } catch (UnsupportedOperationException ignored) {
            // Deployment controls apply on filesystems without POSIX permissions.
        }
    }
}
