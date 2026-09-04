package backend.clinicalcontext.domain;

public record ClinicalContextOwner(String value) {

    private static final int MAX_LENGTH = 128;

    public ClinicalContextOwner {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Clinical context owner is required");
        }
        value = value.trim();
        if (value.length() > MAX_LENGTH || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Clinical context owner has an invalid format");
        }
    }
}
