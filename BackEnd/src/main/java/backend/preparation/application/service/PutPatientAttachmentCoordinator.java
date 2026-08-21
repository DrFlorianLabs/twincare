package backend.preparation.application.service;

import java.util.Objects;

import backend.preparation.application.PatientSessionUnavailableException;
import backend.preparation.application.port.in.CommitStagedPatientAttachmentCommand;
import backend.preparation.application.port.in.CommitStagedPatientAttachmentUseCase;
import backend.preparation.application.port.in.PutPatientAttachmentCommand;
import backend.preparation.application.port.in.PutPatientAttachmentResult;
import backend.preparation.application.port.in.PutPatientAttachmentUseCase;
import backend.preparation.application.port.out.PrivateAttachmentStorage;
import backend.preparation.application.port.out.StagedAttachment;

public final class PutPatientAttachmentCoordinator implements PutPatientAttachmentUseCase {

    private final PrivateAttachmentStorage storage;
    private final CommitStagedPatientAttachmentUseCase commitAttachment;

    public PutPatientAttachmentCoordinator(
            PrivateAttachmentStorage storage,
            CommitStagedPatientAttachmentUseCase commitAttachment) {
        this.storage = Objects.requireNonNull(storage);
        this.commitAttachment = Objects.requireNonNull(commitAttachment);
    }

    @Override
    public PutPatientAttachmentResult put(PutPatientAttachmentCommand command) {
        if (command == null || command.grant() == null) {
            throw new PatientSessionUnavailableException();
        }
        StagedAttachment stagedAttachment = storage.stage(command.upload());
        try {
            return commitAttachment.commit(new CommitStagedPatientAttachmentCommand(
                    command.grant(), stagedAttachment));
        } finally {
            storage.discardStaged(stagedAttachment);
        }
    }
}
