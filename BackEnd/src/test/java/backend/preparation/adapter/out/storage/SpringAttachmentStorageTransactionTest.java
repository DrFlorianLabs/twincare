package backend.preparation.adapter.out.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import backend.preparation.application.port.in.PatientAttachmentUpload;

class SpringAttachmentStorageTransactionTest {

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
    void promotedFileIsRemovedWhenDatabaseTransactionRollsBack() {
        LocalPrivateAttachmentStorage storage = storage();
        SpringAttachmentStorageTransaction transaction = new SpringAttachmentStorageTransaction(storage);
        var staged = storage.stage(upload("first.pdf", pdf("first")));
        begin();

        transaction.promoteForCommit(staged);
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(storage.finalFileExists(staged.storageKey())).isFalse();
    }

    @Test
    void replacedFileIsRestoredOnRollbackAndFinalizedOnCommit() {
        LocalPrivateAttachmentStorage storage = storage();
        SpringAttachmentStorageTransaction transaction = new SpringAttachmentStorageTransaction(storage);
        var first = storage.stage(upload("first.pdf", pdf("first")));
        storage.promote(first);
        begin();

        transaction.deleteAfterCommit(first.storageKey());
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(storage.finalFileExists(first.storageKey())).isTrue();

        begin();
        transaction.deleteAfterCommit(first.storageKey());
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        assertThat(storage.finalFileExists(first.storageKey())).isFalse();
    }

    @Test
    void refusesFileLifecycleOutsideDatabaseTransaction() {
        LocalPrivateAttachmentStorage storage = storage();
        SpringAttachmentStorageTransaction transaction = new SpringAttachmentStorageTransaction(storage);
        var staged = storage.stage(upload("first.pdf", pdf("first")));

        assertThatThrownBy(() -> transaction.promoteForCommit(staged))
                .isInstanceOf(IllegalStateException.class);
        storage.discardStaged(staged);
    }

    private void begin() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(status));
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private LocalPrivateAttachmentStorage storage() {
        return new LocalPrivateAttachmentStorage(tempDir.toString(), 1_000_000);
    }

    private PatientAttachmentUpload upload(String filename, byte[] bytes) {
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

    private byte[] pdf(String body) {
        return ("%PDF-1.7\n" + body + "\n%%EOF").getBytes();
    }
}
