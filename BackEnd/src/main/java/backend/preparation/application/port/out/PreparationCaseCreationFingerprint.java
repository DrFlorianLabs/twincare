package backend.preparation.application.port.out;

public record PreparationCaseCreationFingerprint(String value) {

    private static final int SHA_256_HEX_LENGTH = 64;

    public PreparationCaseCreationFingerprint {
        if (value == null
                || value.length() != SHA_256_HEX_LENGTH
                || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Creation request fingerprint is invalid");
        }
    }

    @Override
    public String toString() {
        return "PreparationCaseCreationFingerprint[value=<redacted>]";
    }
}
