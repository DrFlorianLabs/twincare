package backend.preparation.application.port.in;

public interface RevokePatientInvitationUseCase {

    void revoke(RevokePatientInvitationCommand command);
}
