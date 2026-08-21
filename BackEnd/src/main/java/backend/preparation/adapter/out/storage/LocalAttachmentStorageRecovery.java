package backend.preparation.adapter.out.storage;

import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import backend.preparation.application.port.out.PreparationAttachmentRepository;
import backend.preparation.application.port.out.StagedAttachmentDeletion;
import backend.preparation.domain.AttachmentStorageKey;

@Component
public final class LocalAttachmentStorageRecovery implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(LocalAttachmentStorageRecovery.class);

    private final LocalPrivateAttachmentStorage storage;
    private final PreparationAttachmentRepository attachments;
    private final Clock clock;
    private final Duration orphanGrace;

    public LocalAttachmentStorageRecovery(
            LocalPrivateAttachmentStorage storage,
            PreparationAttachmentRepository attachments,
            Clock preparationClock,
            @Value("${app.preparation.attachment-orphan-grace:PT1H}") Duration orphanGrace) {
        if (orphanGrace == null || orphanGrace.isNegative() || orphanGrace.isZero()) {
            throw new IllegalArgumentException("Preparation attachment orphan grace must be positive");
        }
        this.storage = storage;
        this.attachments = attachments;
        this.clock = preparationClock;
        this.orphanGrace = orphanGrace;
    }

    @Override
    public void afterSingletonsInstantiated() {
        reconcile();
    }

    public void reconcile() {
        storage.initializeAndValidate();
        int abandonedStaging = 0;
        int orphanedFinal = 0;
        int restoredDeletion = 0;
        int finalizedDeletion = 0;
        int missingContent = 0;
        int failed = 0;

        var cutoff = clock.instant().minus(orphanGrace);
        for (AttachmentStorageKey key : storage.stagedKeysOlderThan(cutoff)) {
            try {
                storage.discardStagedKey(key);
                abandonedStaging++;
            } catch (RuntimeException exception) {
                failed++;
            }
        }

        for (AttachmentStorageKey key : storage.pendingDeletionKeys()) {
            try {
                var deletion = new StagedAttachmentDeletion(key, true);
                if (attachments.existsByStorageKey(key)) {
                    storage.restoreStagedDeletion(deletion);
                    restoredDeletion++;
                } else {
                    storage.finalizeStagedDeletion(deletion);
                    finalizedDeletion++;
                }
            } catch (RuntimeException exception) {
                failed++;
            }
        }

        Set<AttachmentStorageKey> referenced = new HashSet<>(attachments.findAllStorageKeys());
        for (AttachmentStorageKey key : storage.finalKeysOlderThan(cutoff)) {
            if (referenced.contains(key)) {
                continue;
            }
            try {
                storage.deleteFinal(key);
                orphanedFinal++;
            } catch (RuntimeException exception) {
                failed++;
            }
        }
        for (AttachmentStorageKey key : referenced) {
            if (!storage.finalFileExists(key)) {
                missingContent++;
            }
        }

        if (abandonedStaging > 0
                || orphanedFinal > 0
                || restoredDeletion > 0
                || finalizedDeletion > 0
                || missingContent > 0
                || failed > 0) {
            log.info(
                    "Preparation attachment recovery staging={}, orphaned={}, restored={}, finalized={}, missing={}, failed={}",
                    abandonedStaging,
                    orphanedFinal,
                    restoredDeletion,
                    finalizedDeletion,
                    missingContent,
                    failed);
        }
        if (missingContent > 0 || failed > 0) {
            throw new IllegalStateException("Preparation attachment storage recovery failed closed");
        }
    }
}
