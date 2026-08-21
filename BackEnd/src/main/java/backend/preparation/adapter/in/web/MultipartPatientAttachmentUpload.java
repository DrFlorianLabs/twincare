package backend.preparation.adapter.in.web;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.web.multipart.MultipartFile;

import backend.preparation.application.port.in.PatientAttachmentUpload;

final class MultipartPatientAttachmentUpload implements PatientAttachmentUpload {

    private final MultipartFile file;

    MultipartPatientAttachmentUpload(MultipartFile file) {
        this.file = file;
    }

    @Override
    public String originalFilename() {
        return file.getOriginalFilename();
    }

    @Override
    public String declaredContentType() {
        return file.getContentType();
    }

    @Override
    public long declaredSizeBytes() {
        return file.getSize();
    }

    @Override
    public InputStream openStream() throws IOException {
        return file.getInputStream();
    }

    @Override
    public String toString() {
        return "MultipartPatientAttachmentUpload[content and filename redacted]";
    }
}
