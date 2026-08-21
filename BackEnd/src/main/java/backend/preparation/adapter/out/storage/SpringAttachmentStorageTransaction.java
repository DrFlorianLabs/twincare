package backend.preparation.adapter.out.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import backend.preparation.application.port.out.AttachmentStorageTransaction;
import backend.preparation.application.port.out.PrivateAttachmentStorage;
import backend.preparation.application.port.out.StagedAttachment;
import backend.preparation.application.port.out.StagedAttachmentDeletion;
import backend.preparation.domain.AttachmentStorageKey;

@Component
public final class SpringAttachmentStorageTransaction implements AttachmentStorageTransaction {

    private static final Logger log = LoggerFactory.getLogger(SpringAttachmentStorageTransaction.class);

    private final PrivateAttachmentStorage storage;

    public SpringAttachmentStorageTransaction(PrivateAttachmentStorage storage) {
        this.storage = storage;
    }

    @Override
    public void promoteForCommit(StagedAttachment stagedAttachment) {
        requireTransactionSynchronization();
        storage.promote(stagedAttachment);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                try {
                    storage.deleteFinal(stagedAttachment.storageKey());
                } catch (RuntimeException exception) {
                    log.error("Preparation attachment rollback cleanup remains pending");
                }
            }
        });
    }

    @Override
    public void deleteAfterCommit(AttachmentStorageKey storageKey) {
        requireTransactionSynchronization();
        StagedAttachmentDeletion deletion = storage.stageDeletion(storageKey);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storage.finalizeStagedDeletion(deletion);
                } catch (RuntimeException exception) {
                    log.error("Preparation attachment deletion remains pending");
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                try {
                    storage.restoreStagedDeletion(deletion);
                } catch (RuntimeException exception) {
                    log.error("Preparation attachment rollback recovery remains pending");
                }
            }
        });
    }

    private void requireTransactionSynchronization() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("An active transaction is required for attachment storage changes");
        }
    }
}
