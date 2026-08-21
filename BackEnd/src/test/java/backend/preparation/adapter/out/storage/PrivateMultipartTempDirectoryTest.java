package backend.preparation.adapter.out.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrivateMultipartTempDirectoryTest {

    private static final Instant NOW = Instant.parse("2026-08-21T12:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void createsPrivateDirectoryAndRemovesOnlyAbandonedRegularFiles() throws Exception {
        Path directory = tempDir.resolve("private-multipart");
        Files.createDirectory(directory);
        Path abandoned = Files.writeString(directory.resolve("abandoned.tmp"), "synthetic");
        Path recent = Files.writeString(directory.resolve("recent.tmp"), "synthetic");
        Files.setLastModifiedTime(abandoned, FileTime.from(NOW.minus(Duration.ofHours(2))));
        Files.setLastModifiedTime(recent, FileTime.from(NOW.minus(Duration.ofMinutes(5))));

        newDirectory(directory).initialize();

        assertThat(abandoned).doesNotExist();
        assertThat(recent).exists();
    }

    @Test
    void refusesSymlinkDirectory() throws Exception {
        Path actual = Files.createDirectory(tempDir.resolve("actual"));
        Path symlink = Files.createSymbolicLink(tempDir.resolve("link"), actual);

        assertThatThrownBy(() -> newDirectory(symlink).initialize())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Private multipart directory initialization failed closed");
    }

    @Test
    void refusesUnsafeDirectoryEntries() throws Exception {
        Path directory = Files.createDirectory(tempDir.resolve("private-multipart"));
        Path target = Files.writeString(tempDir.resolve("outside.tmp"), "synthetic");
        Files.createSymbolicLink(directory.resolve("unsafe.tmp"), target);

        assertThatThrownBy(() -> newDirectory(directory).initialize())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Private multipart directory initialization failed closed");
    }

    private PrivateMultipartTempDirectory newDirectory(Path directory) {
        return new PrivateMultipartTempDirectory(
                directory.toString(),
                Duration.ofHours(1),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
