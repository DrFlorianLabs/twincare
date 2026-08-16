package backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import backend.service.FileStorageService.StagedFileDeletion;

@Service
public class FileTransactionService {

    private static final Logger log = LoggerFactory.getLogger(FileTransactionService.class);

    private final FileStorageService fileStorageService;

    public FileTransactionService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public void stageDeletionAfterCommit(String storageKey) {
        requireTransactionSynchronization();
        StagedFileDeletion stagedDeletion = fileStorageService.stageDeletion(storageKey);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    fileStorageService.finalizeStagedDeletion(stagedDeletion);
                } catch (RuntimeException e) {
                    log.error("Stored document deletion remains pending");
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                try {
                    fileStorageService.restoreStagedDeletion(stagedDeletion);
                } catch (RuntimeException e) {
                    log.error("Stored document rollback recovery remains pending");
                }
            }
        });
    }

    public void deleteStoredFileOnRollback(String storageKey) {
        requireTransactionSynchronization();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                try {
                    StagedFileDeletion stagedDeletion = fileStorageService.stageDeletion(storageKey);
                    fileStorageService.finalizeStagedDeletion(stagedDeletion);
                } catch (RuntimeException e) {
                    log.error("Stored document upload rollback cleanup failed");
                }
            }
        });
    }

    private void requireTransactionSynchronization() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Une transaction active est requise pour gérer un document");
        }
    }
}
