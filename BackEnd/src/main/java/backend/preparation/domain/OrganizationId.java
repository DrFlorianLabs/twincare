package backend.preparation.domain;

public record OrganizationId(String value) {

    private static final int MAX_LENGTH = 128;

    public OrganizationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Organization identifier is required");
        }
        value = value.trim();
        if (value.length() > MAX_LENGTH || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Organization identifier has an invalid format");
        }
    }
}
