package backend.preparation.adapter.out.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import backend.preparation.application.InvalidPatientAttachmentException;
import backend.preparation.application.PatientAttachmentStorageUnavailableException;
import backend.preparation.application.ProfessionalAttachmentStorageUnavailableException;
import backend.preparation.application.port.in.PatientAttachmentUpload;
import backend.preparation.domain.AttachmentMediaType;

class LocalPrivateAttachmentStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void stagesPromotesAndDeletesSupportedSyntheticFiles() throws Exception {
        LocalPrivateAttachmentStorage storage = storage(1_000_000);
        List<Fixture> fixtures = List.of(
                new Fixture("report.pdf", "application/pdf", pdf("synthetic-pdf"), AttachmentMediaType.PDF),
                new Fixture("photo.jpeg", "image/jpeg", jpeg(), AttachmentMediaType.JPEG),
                new Fixture("scan.png", "image/png", png(), AttachmentMediaType.PNG));

        for (Fixture fixture : fixtures) {
            var staged = storage.stage(upload(
                    fixture.filename(), fixture.contentType(), fixture.bytes(), fixture.bytes().length));

            assertThat(staged.mediaType()).isEqualTo(fixture.mediaType());
            assertThat(staged.sizeBytes()).isEqualTo(fixture.bytes().length);
            assertThat(staged.digest().value()).matches("[0-9a-f]{64}");
            assertThat(Files.exists(tempDir.resolve(".staging").resolve(staged.storageKey().value().toString())))
                    .isTrue();

            storage.promote(staged);
            assertThat(Files.exists(tempDir.resolve(staged.storageKey().value().toString()))).isTrue();
            storage.deleteFinal(staged.storageKey());
            assertThat(Files.exists(tempDir.resolve(staged.storageKey().value().toString()))).isFalse();
        }
    }

    @Test
    void filenameIsBasenameNormalizedControlFreeAndCanonical() {
        LocalPrivateAttachmentStorage storage = storage(1_000_000);
        byte[] content = pdf("synthetic");

        var staged = storage.stage(upload(
                "..\\patient\\\u202Ereport.pdf.exe\r\n",
                "application/pdf",
                content,
                content.length));

        assertThat(staged.displayFilename())
                .doesNotContain("patient", "exe", "\r", "\n", "\u202E")
                .endsWith(".pdf")
                .hasSizeLessThanOrEqualTo(200);
    }

    @Test
    void rejectsDeclaredMimeMismatchAndTruncatedFiles() {
        LocalPrivateAttachmentStorage storage = storage(1_000_000);
        byte[] validPdf = pdf("synthetic");

        assertThatThrownBy(() -> storage.stage(upload(
                "fake.png", "image/png", validPdf, validPdf.length)))
                .isInstanceOf(InvalidPatientAttachmentException.class)
                .extracting(exception -> ((InvalidPatientAttachmentException) exception).reason())
                .isEqualTo(InvalidPatientAttachmentException.Reason.UNSUPPORTED_MEDIA_TYPE);
        assertThatThrownBy(() -> storage.stage(upload(
                "truncated.pdf", "application/pdf", "%PDF-1.7".getBytes(), 8)))
                .isInstanceOf(InvalidPatientAttachmentException.class);
        assertThatThrownBy(() -> storage.stage(upload(
                "truncated.jpg", "image/jpeg", new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF }, 3)))
                .isInstanceOf(InvalidPatientAttachmentException.class);
    }

    @Test
    void verifiesActualStreamSizeAndConfiguredLimit() {
        LocalPrivateAttachmentStorage storage = storage(32);
        byte[] content = pdf("synthetic-content-that-exceeds-limit");

        assertThatThrownBy(() -> storage.stage(upload(
                "oversized.pdf", "application/pdf", content, 16)))
                .isInstanceOf(InvalidPatientAttachmentException.class)
                .extracting(exception -> ((InvalidPatientAttachmentException) exception).reason())
                .isEqualTo(InvalidPatientAttachmentException.Reason.TOO_LARGE);

        byte[] shortContent = pdf("short");
        assertThatThrownBy(() -> storage.stage(upload(
                "size-mismatch.pdf", "application/pdf", shortContent, shortContent.length + 1L)))
                .isInstanceOf(InvalidPatientAttachmentException.class)
                .extracting(exception -> ((InvalidPatientAttachmentException) exception).reason())
                .isEqualTo(InvalidPatientAttachmentException.Reason.BAD_REQUEST);
    }

    @Test
    void refusesSymlinkStorageRoot() throws Exception {
        Path actualRoot = Files.createDirectory(tempDir.resolve("actual"));
        Path symlinkRoot = tempDir.resolve("link");
        Files.createSymbolicLink(symlinkRoot, actualRoot);
        LocalPrivateAttachmentStorage storage = new LocalPrivateAttachmentStorage(
                symlinkRoot.toString(), 1_000_000);
        byte[] content = pdf("synthetic");

        assertThatThrownBy(() -> storage.stage(upload(
                "document.pdf", "application/pdf", content, content.length)))
                .isInstanceOf(PatientAttachmentStorageUnavailableException.class);
    }

    @Test
    void verifiedOpenHandleStreamsThePinnedVersionAfterConcurrentDeletionMove() throws Exception {
        LocalPrivateAttachmentStorage storage = storage(1_000_000);
        byte[] bytes = pdf("pinned-synthetic-version");
        var staged = storage.stage(upload(
                "result.pdf", "application/pdf", bytes, bytes.length));
        storage.promote(staged);

        var content = storage.openVerified(
                staged.storageKey(), staged.sizeBytes(), staged.digest());
        var deletion = storage.stageDeletion(staged.storageKey());
        var output = new ByteArrayOutputStream();

        content.transferTo(output);

        assertThat(output.toByteArray()).isEqualTo(bytes);
        assertThat(deletion.existed()).isTrue();
        storage.finalizeStagedDeletion(deletion);
    }

    @Test
    void downloadFailsClosedBeforeStreamingWhenStoredBytesDoNotMatchMetadata() throws Exception {
        LocalPrivateAttachmentStorage storage = storage(1_000_000);
        byte[] bytes = pdf("original-synthetic-version");
        var staged = storage.stage(upload(
                "result.pdf", "application/pdf", bytes, bytes.length));
        storage.promote(staged);
        Files.write(
                tempDir.resolve(staged.storageKey().value().toString()),
                pdf("corrupt-synthetic-version"));

        assertThatThrownBy(() -> storage.openVerified(
                staged.storageKey(), staged.sizeBytes(), staged.digest()))
                .isInstanceOf(ProfessionalAttachmentStorageUnavailableException.class);
    }

    @Test
    void downloadNeverFollowsASymlinkReplacingTheOpaqueFinalObject() throws Exception {
        LocalPrivateAttachmentStorage storage = storage(1_000_000);
        byte[] bytes = pdf("original-synthetic-version");
        var staged = storage.stage(upload(
                "result.pdf", "application/pdf", bytes, bytes.length));
        storage.promote(staged);
        Path finalPath = tempDir.resolve(staged.storageKey().value().toString());
        Files.delete(finalPath);
        Path other = tempDir.resolve("synthetic-other-file");
        Files.write(other, bytes);
        Files.createSymbolicLink(finalPath, other);

        assertThatThrownBy(() -> storage.openVerified(
                staged.storageKey(), staged.sizeBytes(), staged.digest()))
                .isInstanceOf(ProfessionalAttachmentStorageUnavailableException.class);
    }

    @Test
    void clientWriteFailureStillClosesThePinnedContent() throws Exception {
        LocalPrivateAttachmentStorage storage = storage(1_000_000);
        byte[] bytes = pdf("synthetic-client-abort");
        var staged = storage.stage(upload(
                "result.pdf", "application/pdf", bytes, bytes.length));
        storage.promote(staged);
        var content = storage.openVerified(
                staged.storageKey(), staged.sizeBytes(), staged.digest());

        assertThatThrownBy(() -> content.transferTo(new java.io.OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("synthetic client abort");
            }

            @Override
            public void write(byte[] values, int offset, int length) throws IOException {
                throw new IOException("synthetic client abort");
            }
        })).isInstanceOf(IOException.class);
        content.close();
    }

    private LocalPrivateAttachmentStorage storage(long maxSize) {
        return new LocalPrivateAttachmentStorage(tempDir.toString(), maxSize);
    }

    private PatientAttachmentUpload upload(
            String filename,
            String contentType,
            byte[] bytes,
            long declaredSize) {
        return new PatientAttachmentUpload() {
            @Override
            public String originalFilename() {
                return filename;
            }

            @Override
            public String declaredContentType() {
                return contentType;
            }

            @Override
            public long declaredSizeBytes() {
                return declaredSize;
            }

            @Override
            public InputStream openStream() {
                return new ByteArrayInputStream(bytes);
            }
        };
    }

    private byte[] pdf(String body) {
        return ("%PDF-1.7\n" + body + "\n%%EOF").getBytes();
    }

    private byte[] jpeg() {
        return new byte[] {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF,
                0x00, 0x01, 0x02,
                (byte) 0xFF, (byte) 0xD9 };
    }

    private byte[] png() {
        return new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
                (byte) 0xAE, 0x42, 0x60, (byte) 0x82 };
    }

    private record Fixture(
            String filename,
            String contentType,
            byte[] bytes,
            AttachmentMediaType mediaType) {
    }
}
