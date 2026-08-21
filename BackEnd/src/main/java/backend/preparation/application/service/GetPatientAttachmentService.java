package backend.preparation.application.service;

import java.util.Objects;

import backend.preparation.application.PatientAttachmentNotFoundException;
import backend.preparation.application.port.in.GetPatientAttachmentCommand;
import backend.preparation.application.port.in.GetPatientAttachmentUseCase;
import backend.preparation.application.port.in.PatientAttachmentMetadataResult;

public final class GetPatientAttachmentService implements GetPatientAttachmentUseCase {

    private final PatientAttachmentScopeLock scopeLock;

    public GetPatientAttachmentService(PatientAttachmentScopeLock scopeLock) {
        this.scopeLock = Objects.requireNonNull(scopeLock);
    }

    @Override
    public PatientAttachmentMetadataResult get(GetPatientAttachmentCommand command) {
        var lockedScope = scopeLock.lock(command == null ? null : command.grant());
        var attachment = lockedScope.attachment()
                .orElseThrow(PatientAttachmentNotFoundException::new);
        return new PatientAttachmentMetadataResult(
                attachment.displayFilename(),
                attachment.mediaType(),
                attachment.sizeBytes(),
                attachment.uploadedAt(),
                attachment.updatedAt());
    }
}
