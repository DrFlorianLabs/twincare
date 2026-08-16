package backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import backend.exception.BadRequestException;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesValidPdfUsingOpaqueServerGeneratedKey() throws Exception {
        FileStorageService storage = new FileStorageService(tempDir.toString(), 1_000_000);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "compte-rendu.pdf",
                "application/pdf",
                "%PDF-1.7\nsynthetic test content".getBytes());

        String storageKey = storage.storeFile(file);

        assertThat(storageKey).matches("[0-9a-f-]{36}\\.pdf");
        assertThat(storageKey).doesNotContain("compte-rendu");
        assertThat(Files.exists(tempDir.resolve(storageKey))).isTrue();
    }

    @Test
    void rejectsFileWhoseSignatureDoesNotMatchDeclaredMimeType() {
        FileStorageService storage = new FileStorageService(tempDir.toString(), 1_000_000);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.pdf",
                "application/pdf",
                "not a pdf".getBytes());

        assertThatThrownBy(() -> storage.storeFile(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("contenu");
    }

    @Test
    void rejectsUnsupportedContentType() {
        FileStorageService storage = new FileStorageService(tempDir.toString(), 1_000_000);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "payload.txt",
                "text/plain",
                "hello".getBytes());

        assertThatThrownBy(() -> storage.storeFile(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Type de fichier");
    }

    @Test
    void rejectsOversizedFile() {
        FileStorageService storage = new FileStorageService(tempDir.toString(), 4);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                "%PDF-".getBytes());

        assertThatThrownBy(() -> storage.storeFile(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Taille");
    }

    @Test
    void removesPartiallyWrittenFileWhenStorageFails() throws Exception {
        FileStorageService storage = new FileStorageService(tempDir.toString(), 1_000_000);
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(20L);
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getInputStream()).thenReturn(
                new ByteArrayInputStream("%PDF-1.7".getBytes()),
                new FailingInputStream());

        assertThatThrownBy(() -> storage.storeFile(file))
                .isInstanceOf(IllegalStateException.class);

        try (var files = Files.list(tempDir)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void rejectsTraversalInsteadOfResolvingArbitraryPaths() {
        FileStorageService storage = new FileStorageService(tempDir.toString(), 1_000_000);

        assertThatThrownBy(() -> storage.loadFile("../secret.pdf"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Référence de stockage");
    }

    @Test
    void sanitizesClientFilenameForDisplayOnly() {
        FileStorageService storage = new FileStorageService(tempDir.toString(), 1_000_000);

        assertThat(storage.sanitizeDisplayFilename("../../patient/document.pdf"))
                .isEqualTo("document.pdf");
    }

    @Test
    void stagesRestoresAndFinalizesDeletion() throws Exception {
        FileStorageService storage = new FileStorageService(tempDir.toString(), 1_000_000);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "synthetic.pdf",
                "application/pdf",
                "%PDF-1.7\nsynthetic test content".getBytes());

        String storageKey = storage.storeFile(file);
        var stagedDeletion = storage.stageDeletion(storageKey);

        assertThat(stagedDeletion.existed()).isTrue();
        assertThat(storage.listStagedDeletionKeys()).containsExactly(storageKey);
        assertThat(Files.exists(tempDir.resolve(storageKey))).isFalse();

        storage.restoreStagedDeletion(stagedDeletion);
        assertThat(Files.exists(tempDir.resolve(storageKey))).isTrue();

        var stagedAgain = storage.stageDeletion(storageKey);
        storage.finalizeStagedDeletion(stagedAgain);
        assertThat(storage.listStagedDeletionKeys()).isEmpty();
    }

    private static final class FailingInputStream extends InputStream {

        private final byte[] prefix = "%PDF-".getBytes();
        private int index;

        @Override
        public int read() throws IOException {
            if (index < prefix.length) {
                return prefix[index++];
            }
            throw new IOException("Synthetic storage failure");
        }
    }
}
