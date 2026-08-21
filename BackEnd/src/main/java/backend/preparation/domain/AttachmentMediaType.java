package backend.preparation.domain;

public enum AttachmentMediaType {
    PDF("application/pdf", ".pdf"),
    JPEG("image/jpeg", ".jpg"),
    PNG("image/png", ".png");

    private final String contentType;
    private final String canonicalExtension;

    AttachmentMediaType(String contentType, String canonicalExtension) {
        this.contentType = contentType;
        this.canonicalExtension = canonicalExtension;
    }

    public String contentType() {
        return contentType;
    }

    public String canonicalExtension() {
        return canonicalExtension;
    }
}
