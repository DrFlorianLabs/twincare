package backend.clinicalcontext.domain;

public record ClinicalReviewerReference(String value) {

    private static final int MAX_LENGTH = 128;

    public ClinicalReviewerReference {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Clinical reviewer reference is required");
        }
        value = value.trim();
        if (value.length() > MAX_LENGTH || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Clinical reviewer reference has an invalid format");
        }
    }

    @Override
    public String toString() {
        return "ClinicalReviewerReference[redacted]";
    }
}
