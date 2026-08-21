package backend.preparation.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public record AttachmentDigest(String value) {

    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");

    public AttachmentDigest {
        Objects.requireNonNull(value, "Attachment digest is required");
        if (!SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException("Attachment digest must be a lowercase SHA-256 value");
        }
    }

    @Override
    public String toString() {
        return "AttachmentDigest[redacted]";
    }
}
