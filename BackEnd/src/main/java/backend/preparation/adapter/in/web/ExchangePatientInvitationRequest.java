package backend.preparation.adapter.in.web;

public record ExchangePatientInvitationRequest(String token) {

    @Override
    public String toString() {
        return "ExchangePatientInvitationRequest[token=<redacted>]";
    }
}
