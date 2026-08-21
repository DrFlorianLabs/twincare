package backend.preparation.application.port.in;

public interface IssuePatientInvitationUseCase {

    IssuePatientInvitationResult issue(IssuePatientInvitationCommand command);
}
