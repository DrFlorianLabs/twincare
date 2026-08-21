package backend.preparation.adapter.config;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import backend.preparation.application.port.in.DownloadProfessionalAttachmentCommand;
import backend.preparation.application.port.in.DownloadProfessionalAttachmentUseCase;
import backend.preparation.application.port.in.ProfessionalAttachmentDownloadResult;

public class TransactionalProfessionalAttachmentDownload implements DownloadProfessionalAttachmentUseCase {

    private static final int COMPLETION_UNKNOWN = Integer.MIN_VALUE;

    private final DownloadProfessionalAttachmentUseCase delegate;
    private final TransactionOperations transactions;

    public TransactionalProfessionalAttachmentDownload(
            DownloadProfessionalAttachmentUseCase delegate,
            TransactionOperations transactions) {
        this.delegate = delegate;
        this.transactions = transactions;
    }

    @Override
    public ProfessionalAttachmentDownloadResult download(DownloadProfessionalAttachmentCommand command) {
        var prepared = new AtomicReference<ProfessionalAttachmentDownloadResult>();
        var completionStatus = new AtomicInteger(COMPLETION_UNKNOWN);
        try {
            ProfessionalAttachmentDownloadResult result = transactions.execute(status -> {
                if (!TransactionSynchronizationManager.isActualTransactionActive()
                        || !TransactionSynchronizationManager.isSynchronizationActive()) {
                    throw new IllegalStateException(
                            "Professional attachment download requires transaction synchronization");
                }
                ProfessionalAttachmentDownloadResult opened = delegate.download(command);
                prepared.set(opened);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        completionStatus.set(status);
                    }
                });
                return opened;
            });

            if (result == null || completionStatus.get() != TransactionSynchronization.STATUS_COMMITTED) {
                throw new IllegalStateException("Professional attachment download transaction did not commit");
            }
            return result;
        } catch (RuntimeException exception) {
            closeAfterFailure(prepared.get(), exception);
            throw exception;
        }
    }

    private void closeAfterFailure(
            ProfessionalAttachmentDownloadResult result,
            RuntimeException transactionFailure) {
        if (result == null) {
            return;
        }
        try {
            result.content().close();
        } catch (RuntimeException cleanupFailure) {
            transactionFailure.addSuppressed(cleanupFailure);
        }
    }
}
