package backend.preparation.adapter.config;

import org.springframework.transaction.annotation.Transactional;

import backend.preparation.application.port.in.EndPatientAccessSessionCommand;
import backend.preparation.application.port.in.EndPatientAccessSessionUseCase;
import backend.preparation.application.port.in.PatientAccessGrant;
import backend.preparation.application.port.in.ResolvePatientAccessSessionCommand;
import backend.preparation.application.port.in.ResolvePatientAccessSessionUseCase;

public class TransactionalPatientSessionUseCases implements
        ResolvePatientAccessSessionUseCase,
        EndPatientAccessSessionUseCase {

    private final ResolvePatientAccessSessionUseCase resolveDelegate;
    private final EndPatientAccessSessionUseCase endDelegate;

    public TransactionalPatientSessionUseCases(
            ResolvePatientAccessSessionUseCase resolveDelegate,
            EndPatientAccessSessionUseCase endDelegate) {
        this.resolveDelegate = resolveDelegate;
        this.endDelegate = endDelegate;
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
}
