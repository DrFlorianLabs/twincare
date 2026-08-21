package backend.preparation.adapter.config;

import org.springframework.transaction.annotation.Transactional;

import backend.preparation.application.port.in.EndPatientAccessSessionCommand;
import backend.preparation.application.port.in.EndPatientAccessSessionUseCase;
import backend.preparation.application.port.in.PatientAccessGrant;
import backend.preparation.application.port.in.RecordPatientReasonCommand;
import backend.preparation.application.port.in.RecordPatientReasonUseCase;
import backend.preparation.application.port.in.ResolvePatientAccessSessionCommand;
import backend.preparation.application.port.in.ResolvePatientAccessSessionUseCase;

public class TransactionalPatientSessionUseCases implements
        ResolvePatientAccessSessionUseCase,
        EndPatientAccessSessionUseCase,
        RecordPatientReasonUseCase {

    private final ResolvePatientAccessSessionUseCase resolveDelegate;
    private final EndPatientAccessSessionUseCase endDelegate;
    private final RecordPatientReasonUseCase recordReasonDelegate;

    public TransactionalPatientSessionUseCases(
            ResolvePatientAccessSessionUseCase resolveDelegate,
            EndPatientAccessSessionUseCase endDelegate,
            RecordPatientReasonUseCase recordReasonDelegate) {
        this.resolveDelegate = resolveDelegate;
        this.endDelegate = endDelegate;
        this.recordReasonDelegate = recordReasonDelegate;
    }

    @Override
    @Transactional(readOnly = true)
    public PatientAccessGrant resolve(ResolvePatientAccessSessionCommand command) {
        return resolveDelegate.resolve(command);
    }

    @Override
    @Transactional
    public void end(EndPatientAccessSessionCommand command) {
        endDelegate.end(command);
    }

    @Override
    @Transactional
    public void record(RecordPatientReasonCommand command) {
        recordReasonDelegate.record(command);
    }
}
