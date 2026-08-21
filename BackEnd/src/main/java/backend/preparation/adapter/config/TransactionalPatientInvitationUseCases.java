package backend.preparation.adapter.config;

import org.springframework.transaction.annotation.Transactional;

import backend.preparation.application.PatientInvitationUnavailableException;
import backend.preparation.application.port.in.ExchangePatientInvitationCommand;
import backend.preparation.application.port.in.ExchangePatientInvitationResult;
import backend.preparation.application.port.in.ExchangePatientInvitationUseCase;
import backend.preparation.application.port.in.IssuePatientInvitationCommand;
import backend.preparation.application.port.in.IssuePatientInvitationResult;
import backend.preparation.application.port.in.IssuePatientInvitationUseCase;
import backend.preparation.application.port.in.RevokePatientInvitationCommand;
import backend.preparation.application.port.in.RevokePatientInvitationUseCase;

public class TransactionalPatientInvitationUseCases implements
        IssuePatientInvitationUseCase,
        RevokePatientInvitationUseCase,
        ExchangePatientInvitationUseCase {

    private final IssuePatientInvitationUseCase issueDelegate;
    private final RevokePatientInvitationUseCase revokeDelegate;
    private final ExchangePatientInvitationUseCase exchangeDelegate;

    public TransactionalPatientInvitationUseCases(
            IssuePatientInvitationUseCase issueDelegate,
            RevokePatientInvitationUseCase revokeDelegate,
            ExchangePatientInvitationUseCase exchangeDelegate) {
        this.issueDelegate = issueDelegate;
        this.revokeDelegate = revokeDelegate;
        this.exchangeDelegate = exchangeDelegate;
    }

    @Override
    @Transactional
    public IssuePatientInvitationResult issue(IssuePatientInvitationCommand command) {
        return issueDelegate.issue(command);
    }

    @Override
    @Transactional
    public void revoke(RevokePatientInvitationCommand command) {
        revokeDelegate.revoke(command);
    }

    @Override
    @Transactional(noRollbackFor = PatientInvitationUnavailableException.class)
    public ExchangePatientInvitationResult exchange(ExchangePatientInvitationCommand command) {
        return exchangeDelegate.exchange(command);
    }
}
