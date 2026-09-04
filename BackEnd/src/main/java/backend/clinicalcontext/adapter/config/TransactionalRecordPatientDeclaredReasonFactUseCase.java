package backend.clinicalcontext.adapter.config;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import backend.clinicalcontext.application.port.in.RecordPatientDeclaredReasonFactCommand;
import backend.clinicalcontext.application.port.in.RecordPatientDeclaredReasonFactUseCase;

class TransactionalRecordPatientDeclaredReasonFactUseCase
        implements RecordPatientDeclaredReasonFactUseCase {

    private final RecordPatientDeclaredReasonFactUseCase delegate;

    TransactionalRecordPatientDeclaredReasonFactUseCase(
            RecordPatientDeclaredReasonFactUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(RecordPatientDeclaredReasonFactCommand command) {
        delegate.record(command);
    }
}
