package backend.preparation.domain;

public record SecureToken(String value) {

    public static final int ENCODED_LENGTH = 43;

    public SecureToken {
        if (value == null
                || value.length() != ENCODED_LENGTH
                || !value.chars().allMatch(SecureToken::isUrlSafeCharacter)) {
            throw new IllegalArgumentException("Secure token has an invalid format");
        }
    }

    private static boolean isUrlSafeCharacter(int character) {
        return Character.isLetterOrDigit(character) || character == '-' || character == '_';
    }

    @Override
    public String toString() {
        return "SecureToken[value=<redacted>]";
    }
}
