package backend.preparation.application.port.in;

import backend.preparation.application.port.out.StagedAttachment;

public record CommitStagedPatientAttachmentCommand(
        PatientAccessGrant grant,
        StagedAttachment stagedAttachment) {
}
