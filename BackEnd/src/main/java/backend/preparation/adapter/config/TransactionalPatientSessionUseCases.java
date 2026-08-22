package backend.preparation.adapter.config;

import org.springframework.transaction.annotation.Transactional;

import backend.preparation.application.port.in.EndPatientAccessSessionCommand;
import backend.preparation.application.port.in.EndPatientAccessSessionUseCase;
import backend.preparation.application.port.in.GetPatientReasonCommand;
import backend.preparation.application.port.in.GetPatientReasonUseCase;
import backend.preparation.application.port.in.PatientAccessGrant;
import backend.preparation.application.port.in.PatientReasonResult;
import backend.preparation.application.port.in.RecordPatientReasonCommand;
import backend.preparation.application.port.in.RecordPatientReasonUseCase;
import backend.preparation.application.port.in.ResolvePatientAccessSessionCommand;
import backend.preparation.application.port.in.ResolvePatientAccessSessionUseCase;

public class TransactionalPatientSessionUseCases implements
        ResolvePatientAccessSessionUseCase,
        EndPatientAccessSessionUseCase,
        GetPatientReasonUseCase,
        RecordPatientReasonUseCase {

    private final ResolvePatientAccessSessionUseCase resolveDelegate;
    private final EndPatientAccessSessionUseCase endDelegate;
    private final GetPatientReasonUseCase getReasonDelegate;
    private final RecordPatientReasonUseCase recordReasonDelegate;

    public TransactionalPatientSessionUseCases(
            ResolvePatientAccessSessionUseCase resolveDelegate,
            EndPatientAccessSessionUseCase endDelegate,
            GetPatientReasonUseCase getReasonDelegate,
            RecordPatientReasonUseCase recordReasonDelegate) {
        this.resolveDelegate = resolveDelegate;
        this.endDelegate = endDelegate;
        this.getReasonDelegate = getReasonDelegate;
        this.recordReasonDelegate = recordReasonDelegate;
    }

    @Override
    @Transactional(readOnly = true)
    public PatientReasonResult get(GetPatientReasonCommand command) {
        return getReasonDelegate.get(command);
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
