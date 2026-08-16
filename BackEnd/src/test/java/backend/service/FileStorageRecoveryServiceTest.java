package backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import backend.dao.DocumentRepository;

class FileStorageRecoveryServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void restoresFileStillReferencedByCommittedMetadata() throws Exception {
        FileStorageService storage = new FileStorageService(tempDir.toString(), 1_000_000);
        DocumentRepository documents = mock(DocumentRepository.class);
        String storageKey = storeSyntheticPdf(storage);
        storage.stageDeletion(storageKey);
        when(documents.existsByCheminFichier(storageKey)).thenReturn(true);

        new FileStorageRecoveryService(storage, documents).reconcilePendingDeletions();

        assertThat(Files.exists(tempDir.resolve(storageKey))).isTrue();
        assertThat(storage.listStagedDeletionKeys()).isEmpty();
    }

    @Test
    void finalizesFileNoLongerReferencedByMetadata() throws Exception {
        FileStorageService storage = new FileStorageService(tempDir.toString(), 1_000_000);
        DocumentRepository documents = mock(DocumentRepository.class);
        String storageKey = storeSyntheticPdf(storage);
        storage.stageDeletion(storageKey);
        when(documents.existsByCheminFichier(storageKey)).thenReturn(false);

        new FileStorageRecoveryService(storage, documents).reconcilePendingDeletions();

        assertThat(Files.exists(tempDir.resolve(storageKey))).isFalse();
        assertThat(storage.listStagedDeletionKeys()).isEmpty();
    }

    private String storeSyntheticPdf(FileStorageService storage) {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "synthetic.pdf",
                "application/pdf",
                "%PDF-1.7\nsynthetic test content".getBytes());
        return storage.storeFile(file);
    }
}
