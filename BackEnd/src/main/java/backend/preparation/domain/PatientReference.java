package backend.preparation.domain;

public record PatientReference(String value) {

    private static final int MAX_LENGTH = 128;

    public PatientReference {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Patient reference is required");
        }
        value = value.trim();
        if (value.length() > MAX_LENGTH || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Patient reference has an invalid format");
        }
    }

    @Override
    public String toString() {
        return "PatientReference[value=<redacted>]";
    }
}
