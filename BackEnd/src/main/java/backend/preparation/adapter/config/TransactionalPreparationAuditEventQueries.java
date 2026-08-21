package backend.preparation.adapter.config;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import backend.preparation.application.port.in.ListPreparationAuditEventsCommand;
import backend.preparation.application.port.in.ListPreparationAuditEventsUseCase;
import backend.preparation.application.port.in.PreparationAuditEventPageResult;

public class TransactionalPreparationAuditEventQueries implements ListPreparationAuditEventsUseCase {

    private final ListPreparationAuditEventsUseCase delegate;

    public TransactionalPreparationAuditEventQueries(ListPreparationAuditEventsUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PreparationAuditEventPageResult list(ListPreparationAuditEventsCommand command) {
        return delegate.list(command);
    }
}
