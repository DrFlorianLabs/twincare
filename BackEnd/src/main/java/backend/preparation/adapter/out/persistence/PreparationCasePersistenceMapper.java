package backend.preparation.adapter.out.persistence;

import java.util.Optional;

import backend.preparation.domain.AppointmentReference;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientReference;
import backend.preparation.domain.PatientStatement;
import backend.preparation.domain.PreparationCase;
import backend.preparation.domain.PreparationCaseId;

final class PreparationCasePersistenceMapper {

    PreparationCaseJpaEntity toEntity(PreparationCase preparationCase) {
        Optional<PatientStatement> reason = preparationCase.patientReason();
        return new PreparationCaseJpaEntity(
                preparationCase.id().value(),
                preparationCase.organizationId().value(),
                preparationCase.patientReference().value(),
                preparationCase.appointmentReference().map(AppointmentReference::value).orElse(null),
                preparationCase.scheduledAt().orElse(null),
                preparationCase.status(),
                reason.map(PatientStatement::text).orElse(null),
                reason.map(PatientStatement::provenance).orElse(null),
                reason.map(PatientStatement::recordedAt).orElse(null),
                preparationCase.createdAt(),
                preparationCase.updatedAt());
    }

    PreparationCase toDomain(PreparationCaseJpaEntity entity) {
        Optional<PatientStatement> patientReason = Optional.ofNullable(entity.patientReason())
                .map(reason -> new PatientStatement(
                        reason,
                        entity.patientReasonProvenance(),
                        entity.patientReasonRecordedAt()));
        return PreparationCase.restore(
                new PreparationCaseId(entity.id()),
                new OrganizationId(entity.organizationId()),
                new PatientReference(entity.patientReference()),
                Optional.ofNullable(entity.appointmentReference()).map(AppointmentReference::new),
                Optional.ofNullable(entity.scheduledAt()),
                entity.createdAt(),
                entity.status(),
                patientReason,
                entity.updatedAt());
    }
}
