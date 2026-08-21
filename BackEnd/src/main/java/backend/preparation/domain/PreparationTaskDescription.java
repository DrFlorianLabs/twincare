package backend.preparation.domain;

import java.text.Normalizer;

public record PreparationTaskDescription(String value) {

    public static final int MAX_CODE_POINTS = 240;

    public PreparationTaskDescription {
        if (value == null) {
            throw new IllegalArgumentException("Preparation task description is required");
        }
        value = Normalizer.normalize(value, Normalizer.Form.NFC).trim();
        if (value.isEmpty()
                || value.codePointCount(0, value.length()) > MAX_CODE_POINTS
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Preparation task description is invalid");
        }
    }

    @Override
    public String toString() {
        return "PreparationTaskDescription[value redacted]";
    }
}
