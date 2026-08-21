package backend.preparation.application.port.in;

public interface ExchangePatientInvitationUseCase {

    ExchangePatientInvitationResult exchange(ExchangePatientInvitationCommand command);
}
