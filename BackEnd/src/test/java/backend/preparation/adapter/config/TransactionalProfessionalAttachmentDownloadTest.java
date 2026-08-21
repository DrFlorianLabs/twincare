package backend.preparation.adapter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import backend.preparation.application.port.in.DownloadProfessionalAttachmentCommand;
import backend.preparation.application.port.in.DownloadProfessionalAttachmentUseCase;
import backend.preparation.application.port.in.ProfessionalAttachmentDownloadResult;
import backend.preparation.application.port.out.PreparedAttachmentContent;
import backend.preparation.domain.AttachmentMediaType;

class TransactionalProfessionalAttachmentDownloadTest {

    @Test
    void rollbackClosesThePreparedHandleBeforeItCanEscape() {
        var fixture = fixture(Outcome.ROLLBACK);

        assertThatThrownBy(() -> fixture.wrapper().download(fixture.command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not commit");

        verify(fixture.content()).close();
    }

    @Test
    void committedTransactionLeavesTheHandleForTheControllerToStream() {
        var fixture = fixture(Outcome.COMMIT);

        assertThat(fixture.wrapper().download(fixture.command())).isSameAs(fixture.result());

        verify(fixture.content(), never()).close();
    }

    @Test
    void exceptionFromAnotherAfterCommitCallbackClosesThePreparedHandle() {
        var fixture = fixture(Outcome.FAIL_AFTER_COMMIT);

        assertThatThrownBy(() -> fixture.wrapper().download(fixture.command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("synthetic after-commit failure");

        verify(fixture.content()).close();
    }

    @Test
    void missingSynchronizationFailsBeforePreparingAHandle() {
        var fixture = fixture(Outcome.NO_SYNCHRONIZATION);

        assertThatThrownBy(() -> fixture.wrapper().download(fixture.command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transaction synchronization");
        verify(fixture.delegate(), never()).download(fixture.command());
        verify(fixture.content(), never()).close();
    }

    private Fixture fixture(Outcome outcome) {
        DownloadProfessionalAttachmentUseCase delegate = mock(DownloadProfessionalAttachmentUseCase.class);
        DownloadProfessionalAttachmentCommand command = mock(DownloadProfessionalAttachmentCommand.class);
        PreparedAttachmentContent content = mock(PreparedAttachmentContent.class);
        when(content.sizeBytes()).thenReturn(42L);
        ProfessionalAttachmentDownloadResult result = new ProfessionalAttachmentDownloadResult(
                "synthetic-result.pdf", AttachmentMediaType.PDF, 42L, content);
        when(delegate.download(command)).thenReturn(result);
        return new Fixture(
                new TransactionalProfessionalAttachmentDownload(delegate, new SynchronousTransactions(outcome)),
                delegate,
                command,
                result,
                content);
    }

    private record Fixture(
            TransactionalProfessionalAttachmentDownload wrapper,
            DownloadProfessionalAttachmentUseCase delegate,
            DownloadProfessionalAttachmentCommand command,
            ProfessionalAttachmentDownloadResult result,
            PreparedAttachmentContent content) {
    }

    private enum Outcome {
        COMMIT,
        ROLLBACK,
        FAIL_AFTER_COMMIT,
        NO_SYNCHRONIZATION
    }

    private static final class SynchronousTransactions implements TransactionOperations {

        private final Outcome outcome;

        private SynchronousTransactions(Outcome outcome) {
            this.outcome = outcome;
        }

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            if (outcome == Outcome.NO_SYNCHRONIZATION) {
                return action.doInTransaction(mock(TransactionStatus.class));
            }

            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.initSynchronization();
            if (outcome == Outcome.FAIL_AFTER_COMMIT) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        throw new IllegalStateException("synthetic after-commit failure");
                    }
                });
            }

            try {
                T result = action.doInTransaction(mock(TransactionStatus.class));
                var synchronizations = TransactionSynchronizationManager.getSynchronizations();
                if (outcome == Outcome.ROLLBACK) {
                    synchronizations.forEach(synchronization -> synchronization.afterCompletion(
                            TransactionSynchronization.STATUS_ROLLED_BACK));
                    return result;
                }

                RuntimeException afterCommitFailure = null;
                for (TransactionSynchronization synchronization : synchronizations) {
                    try {
                        synchronization.afterCommit();
                    } catch (RuntimeException exception) {
                        afterCommitFailure = exception;
                        break;
                    }
                }
                synchronizations.forEach(synchronization -> synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_COMMITTED));
                if (afterCommitFailure != null) {
                    throw afterCommitFailure;
                }
                return result;
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
        }
    }
}
