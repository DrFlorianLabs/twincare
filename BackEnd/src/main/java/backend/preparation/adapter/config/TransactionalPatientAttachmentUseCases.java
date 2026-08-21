package backend.preparation.adapter.config;

import org.springframework.transaction.annotation.Transactional;

import backend.preparation.application.port.in.CommitStagedPatientAttachmentCommand;
import backend.preparation.application.port.in.CommitStagedPatientAttachmentUseCase;
import backend.preparation.application.port.in.DeletePatientAttachmentCommand;
import backend.preparation.application.port.in.DeletePatientAttachmentUseCase;
import backend.preparation.application.port.in.GetPatientAttachmentCommand;
import backend.preparation.application.port.in.GetPatientAttachmentUseCase;
import backend.preparation.application.port.in.PatientAttachmentMetadataResult;
import backend.preparation.application.port.in.PutPatientAttachmentResult;

public class TransactionalPatientAttachmentUseCases implements
        CommitStagedPatientAttachmentUseCase,
        GetPatientAttachmentUseCase,
        DeletePatientAttachmentUseCase {

    private final CommitStagedPatientAttachmentUseCase commitDelegate;
    private final GetPatientAttachmentUseCase getDelegate;
    private final DeletePatientAttachmentUseCase deleteDelegate;

    public TransactionalPatientAttachmentUseCases(
            CommitStagedPatientAttachmentUseCase commitDelegate,
            GetPatientAttachmentUseCase getDelegate,
            DeletePatientAttachmentUseCase deleteDelegate) {
        this.commitDelegate = commitDelegate;
        this.getDelegate = getDelegate;
        this.deleteDelegate = deleteDelegate;
    }

    @Override
    @Transactional
    public PutPatientAttachmentResult commit(CommitStagedPatientAttachmentCommand command) {
        return commitDelegate.commit(command);
    }

    @Override
    @Transactional
    public PatientAttachmentMetadataResult get(GetPatientAttachmentCommand command) {
        return getDelegate.get(command);
    }

    @Override
    @Transactional
    public void delete(DeletePatientAttachmentCommand command) {
        deleteDelegate.delete(command);
    }
}
