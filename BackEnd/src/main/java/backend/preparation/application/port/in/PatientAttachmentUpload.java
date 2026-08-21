package backend.preparation.application.port.in;

import java.io.IOException;
import java.io.InputStream;

public interface PatientAttachmentUpload {

    String originalFilename();

    String declaredContentType();

    long declaredSizeBytes();

    InputStream openStream() throws IOException;
}
