package backend.preparation.application.port.in;

public interface ListPreparationAuditEventsUseCase {

    PreparationAuditEventPageResult list(ListPreparationAuditEventsCommand command);
}
