package backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class FileTransactionServiceTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void restoresStagedFileWhenDatabaseTransactionRollsBack() throws Exception {
        FileStorageService storage = new FileStorageService(tempDir.toString(), 1_000_000);
        FileTransactionService transactions = new FileTransactionService(storage);
        String storageKey = storeSyntheticPdf(storage);
        beginTransactionSynchronization();

        transactions.stageDeletionAfterCommit(storageKey);
        assertThat(Files.exists(tempDir.resolve(storageKey))).isFalse();

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(Files.exists(tempDir.resolve(storageKey))).isTrue();
        assertThat(storage.listStagedDeletionKeys()).isEmpty();
    }

    @Test
    void finalizesStagedFileOnlyAfterDatabaseCommit() throws Exception {
        FileStorageService storage = new FileStorageService(tempDir.toString(), 1_000_000);
        FileTransactionService transactions = new FileTransactionService(storage);
        String storageKey = storeSyntheticPdf(storage);
        beginTransactionSynchronization();

        transactions.stageDeletionAfterCommit(storageKey);
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        assertThat(Files.exists(tempDir.resolve(storageKey))).isFalse();
        assertThat(storage.listStagedDeletionKeys()).isEmpty();
    }

    @Test
    void removesNewlyStoredFileWhenDatabaseTransactionRollsBack() throws Exception {
        FileStorageService storage = new FileStorageService(tempDir.toString(), 1_000_000);
        FileTransactionService transactions = new FileTransactionService(storage);
        String storageKey = storeSyntheticPdf(storage);
        beginTransactionSynchronization();

        transactions.deleteStoredFileOnRollback(storageKey);
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(Files.exists(tempDir.resolve(storageKey))).isFalse();
        assertThat(storage.listStagedDeletionKeys()).isEmpty();
    }

    @Test
    void refusesToStageADeletionOutsideADatabaseTransaction() throws Exception {
        FileStorageService storage = new FileStorageService(tempDir.toString(), 1_000_000);
        FileTransactionService transactions = new FileTransactionService(storage);
        String storageKey = storeSyntheticPdf(storage);

        assertThatThrownBy(() -> transactions.stageDeletionAfterCommit(storageKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transaction");

        assertThat(Files.exists(tempDir.resolve(storageKey))).isTrue();
    }

    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(status));
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
