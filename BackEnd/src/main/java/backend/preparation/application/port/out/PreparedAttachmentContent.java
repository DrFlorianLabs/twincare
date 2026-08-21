package backend.preparation.application.port.out;

import java.io.IOException;
import java.io.OutputStream;

public interface PreparedAttachmentContent extends AutoCloseable {

    long sizeBytes();

    void transferTo(OutputStream outputStream) throws IOException;

    @Override
    void close();
}
