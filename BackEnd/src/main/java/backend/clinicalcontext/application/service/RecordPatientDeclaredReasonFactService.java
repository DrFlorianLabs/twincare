package backend.clinicalcontext.application.service;

import java.util.Objects;

import backend.clinicalcontext.application.port.in.RecordPatientDeclaredReasonFactCommand;
import backend.clinicalcontext.application.port.in.RecordPatientDeclaredReasonFactUseCase;
import backend.clinicalcontext.application.port.out.ClinicalFactIdGenerator;
import backend.clinicalcontext.application.port.out.ClinicalFactRepository;
import backend.clinicalcontext.domain.ConsultationReasonFact;

public final class RecordPatientDeclaredReasonFactService
        implements RecordPatientDeclaredReasonFactUseCase {

    private final ClinicalFactRepository facts;
    private final ClinicalFactIdGenerator idGenerator;

    public RecordPatientDeclaredReasonFactService(
            ClinicalFactRepository facts,
            ClinicalFactIdGenerator idGenerator) {
        this.facts = Objects.requireNonNull(facts);
        this.idGenerator = Objects.requireNonNull(idGenerator);
    }

    @Override
    public void record(RecordPatientDeclaredReasonFactCommand command) {
        Objects.requireNonNull(command, "Patient-declared reason fact command is required");
        facts.findCurrentConsultationReason(command.contextId(), command.owner())
                .ifPresent(current -> {
                    if (current.supersede(command.recordedAt())) {
                        facts.update(current);
                    }
                });
        facts.add(ConsultationReasonFact.declaredByPatient(
                idGenerator.nextId(),
                command.contextId(),
                command.owner(),
                command.text(),
                command.recordedAt()));
    }
}
