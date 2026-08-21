package backend.preparation.adapter.out.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import backend.preparation.application.port.in.PatientAttachmentUpload;
import backend.preparation.application.PatientAttachmentStorageUnavailableException;
import backend.preparation.application.port.out.PreparationAttachmentRepository;
import backend.preparation.application.port.out.StagedAttachment;
import backend.preparation.domain.AttachmentStorageKey;

class LocalAttachmentStorageRecoveryTest {

    private static final Instant NOW = Instant.parse("2026-08-21T12:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void reconcilesCrashResiduesAgainstDatabaseReferencesWithoutReadingContent() throws Exception {
        LocalPrivateAttachmentStorage storage = new LocalPrivateAttachmentStorage(tempDir.toString(), 1_000_000);
        StagedAttachment abandoned = storage.stage(upload("abandoned.pdf"));
        StagedAttachment orphaned = promote(storage, "orphaned.pdf");
        StagedAttachment referenced = promote(storage, "referenced.pdf");
        StagedAttachment toRestore = promote(storage, "restore.pdf");
        StagedAttachment toFinalize = promote(storage, "finalize.pdf");
        storage.stageDeletion(toRestore.storageKey());
        storage.stageDeletion(toFinalize.storageKey());
        makeOld(tempDir.resolve(".staging").resolve(abandoned.storageKey().value().toString()));
        makeOld(tempDir.resolve(orphaned.storageKey().value().toString()));
        makeOld(tempDir.resolve(referenced.storageKey().value().toString()));

        Set<AttachmentStorageKey> databaseKeys = Set.of(referenced.storageKey(), toRestore.storageKey());
        PreparationAttachmentRepository attachments = mock(PreparationAttachmentRepository.class);
        when(attachments.existsByStorageKey(any())).thenAnswer(invocation ->
                databaseKeys.contains(invocation.getArgument(0)));
        when(attachments.findAllStorageKeys()).thenReturn(List.copyOf(databaseKeys));

        var recovery = new LocalAttachmentStorageRecovery(
                storage,
                attachments,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofHours(1));

        recovery.reconcile();

        assertThat(storage.stagedKeysOlderThan(Instant.MAX)).isEmpty();
        assertThat(storage.finalFileExists(orphaned.storageKey())).isFalse();
        assertThat(storage.finalFileExists(referenced.storageKey())).isTrue();
        assertThat(storage.finalFileExists(toRestore.storageKey())).isTrue();
        assertThat(storage.finalFileExists(toFinalize.storageKey())).isFalse();
        assertThat(storage.pendingDeletionKeys()).isEmpty();
    }

    @Test
    void failsClosedWhenDatabaseReferencesMissingContent() {
        LocalPrivateAttachmentStorage storage = new LocalPrivateAttachmentStorage(tempDir.toString(), 1_000_000);
        AttachmentStorageKey missing = new AttachmentStorageKey(UUID.randomUUID());
        PreparationAttachmentRepository attachments = mock(PreparationAttachmentRepository.class);
        when(attachments.findAllStorageKeys()).thenReturn(List.of(missing));
        var recovery = new LocalAttachmentStorageRecovery(
                storage,
                attachments,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofHours(1));

        assertThatThrownBy(recovery::reconcile)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Preparation attachment storage recovery failed closed");
    }

    @Test
    void missingFinalContentDoesNotPreventMetadataRepair() {
        LocalPrivateAttachmentStorage storage = new LocalPrivateAttachmentStorage(tempDir.toString(), 1_000_000);
        AttachmentStorageKey missing = new AttachmentStorageKey(UUID.randomUUID());

        assertThat(storage.stageDeletion(missing).existed()).isFalse();
    }

    @Test
    void refusesAnUnsafeEmptyPersistentRootBeforeServingRequests() throws Exception {
        Path actual = Files.createDirectory(tempDir.resolve("actual"));
        Path symlink = Files.createSymbolicLink(tempDir.resolve("unsafe-root"), actual);
        LocalPrivateAttachmentStorage storage = new LocalPrivateAttachmentStorage(
                symlink.toString(), 1_000_000);
        PreparationAttachmentRepository attachments = mock(PreparationAttachmentRepository.class);
        var recovery = new LocalAttachmentStorageRecovery(
                storage,
                attachments,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofHours(1));

        assertThatThrownBy(recovery::afterSingletonsInstantiated)
                .isInstanceOf(PatientAttachmentStorageUnavailableException.class);
    }

    private StagedAttachment promote(LocalPrivateAttachmentStorage storage, String filename) {
        StagedAttachment staged = storage.stage(upload(filename));
        storage.promote(staged);
        return staged;
    }

    private void makeOld(Path path) throws Exception {
        Files.setLastModifiedTime(path, FileTime.from(NOW.minus(Duration.ofHours(2))));
    }

    private PatientAttachmentUpload upload(String filename) {
        byte[] bytes = ("%PDF-1.7\n" + filename + "\n%%EOF").getBytes();
        return new PatientAttachmentUpload() {
            @Override
            public String originalFilename() {
                return filename;
            }

            @Override
            public String declaredContentType() {
                return "application/pdf";
            }

            @Override
            public long declaredSizeBytes() {
                return bytes.length;
            }

            @Override
            public InputStream openStream() {
                return new ByteArrayInputStream(bytes);
            }
        };
    }
}
