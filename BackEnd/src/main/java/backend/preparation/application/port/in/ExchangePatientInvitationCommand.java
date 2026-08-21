package backend.preparation.application.port.in;

public record ExchangePatientInvitationCommand(String token) {

    @Override
    public String toString() {
        return "ExchangePatientInvitationCommand[token=<redacted>]";
    }
}
