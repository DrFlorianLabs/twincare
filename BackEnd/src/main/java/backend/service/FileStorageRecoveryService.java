package backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import backend.dao.DocumentRepository;
import backend.service.FileStorageService.StagedFileDeletion;

@Profile("legacy-prototype")
@Service
public class FileStorageRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageRecoveryService.class);

    private final FileStorageService fileStorageService;
    private final DocumentRepository documentRepository;

    public FileStorageRecoveryService(
            FileStorageService fileStorageService,
            DocumentRepository documentRepository) {
        this.fileStorageService = fileStorageService;
        this.documentRepository = documentRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcilePendingDeletions() {
        int recovered = 0;
        int finalized = 0;
        int failed = 0;

        for (String storageKey : fileStorageService.listStagedDeletionKeys()) {
            StagedFileDeletion stagedDeletion = new StagedFileDeletion(storageKey, true);
            try {
                if (documentRepository.existsByCheminFichier(storageKey)) {
                    fileStorageService.restoreStagedDeletion(stagedDeletion);
                    recovered++;
                } else {
                    fileStorageService.finalizeStagedDeletion(stagedDeletion);
                    finalized++;
                }
            } catch (RuntimeException e) {
                failed++;
            }
        }

        if (recovered > 0 || finalized > 0 || failed > 0) {
            log.info("Stored document cleanup reconciliation recovered={}, finalized={}, failed={}",
                    recovered, finalized, failed);
        }
    }
}
