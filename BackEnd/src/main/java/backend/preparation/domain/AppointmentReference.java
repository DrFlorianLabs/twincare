package backend.preparation.domain;

public record AppointmentReference(String value) {

    private static final int MAX_LENGTH = 128;

    public AppointmentReference {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Appointment reference is required");
        }
        value = value.trim();
        if (value.length() > MAX_LENGTH || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Appointment reference has an invalid format");
        }
    }

    @Override
    public String toString() {
        return "AppointmentReference[value=<redacted>]";
    }
}
