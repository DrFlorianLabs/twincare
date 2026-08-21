package backend.preparation.domain;

public record TokenDigest(String keyId, String value) {

    private static final int SHA_256_HEX_LENGTH = 64;

    public TokenDigest {
        if (keyId == null || keyId.isBlank() || keyId.length() > 64
                || keyId.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Token key identifier has an invalid format");
        }
        keyId = keyId.trim();
        if (value == null
                || value.length() != SHA_256_HEX_LENGTH
                || !value.chars().allMatch(TokenDigest::isLowercaseHex)) {
            throw new IllegalArgumentException("Token digest has an invalid format");
        }
    }

    private static boolean isLowercaseHex(int character) {
        return (character >= '0' && character <= '9') || (character >= 'a' && character <= 'f');
    }

    @Override
    public String toString() {
        return "TokenDigest[keyId=" + keyId + ", value=<redacted>]";
    }
}
