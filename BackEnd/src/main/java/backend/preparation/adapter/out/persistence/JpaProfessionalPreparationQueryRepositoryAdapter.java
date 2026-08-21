package backend.preparation.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import backend.preparation.application.port.out.ProfessionalAttachmentSnapshot;
import backend.preparation.application.port.out.ProfessionalPreparationListPageSnapshot;
import backend.preparation.application.port.out.ProfessionalPreparationListSnapshot;
import backend.preparation.application.port.out.ProfessionalPreparationQueryRepository;
import backend.preparation.application.port.out.ProfessionalPreparationViewSnapshot;
import backend.preparation.domain.AppointmentReference;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientReference;
import backend.preparation.domain.PatientStatement;
import backend.preparation.domain.PreparationCaseId;

@Repository
public class JpaProfessionalPreparationQueryRepositoryAdapter
        implements ProfessionalPreparationQueryRepository {

    private final SpringDataProfessionalPreparationQueryRepository repository;

    public JpaProfessionalPreparationQueryRepositoryAdapter(
            SpringDataProfessionalPreparationQueryRepository repository) {
        this.repository = repository;
    }

    @Override
    public ProfessionalPreparationListPageSnapshot findPage(
            OrganizationId organizationId,
            int page,
            int size) {
        var result = repository.findPageByOrganizationId(
                organizationId.value(),
                PageRequest.of(page, size));
        var content = result.getContent().stream()
                .map(this::toListSnapshot)
                .toList();
        return new ProfessionalPreparationListPageSnapshot(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public Optional<ProfessionalPreparationViewSnapshot> findView(
            OrganizationId organizationId,
            PreparationCaseId caseId) {
        return repository.findView(organizationId.value(), caseId.value())
                .map(this::toViewSnapshot);
    }

    private ProfessionalPreparationListSnapshot toListSnapshot(
            ProfessionalPreparationListProjection projection) {
        return new ProfessionalPreparationListSnapshot(
                new PreparationCaseId(projection.getCaseId()),
                new PatientReference(projection.getPatientReference()),
                optionalAppointmentReference(projection.getAppointmentReference()),
                Optional.ofNullable(projection.getScheduledAt()),
                projection.getCaseStatus(),
                Boolean.TRUE.equals(projection.getPatientReasonPresent()),
                Boolean.TRUE.equals(projection.getAttachmentPresent()),
                projection.getUpdatedAt());
    }

    private ProfessionalPreparationViewSnapshot toViewSnapshot(
            ProfessionalPreparationViewProjection projection) {
        Optional<PatientStatement> reason = Optional.ofNullable(projection.getPatientReason())
                .map(value -> new PatientStatement(
                        value,
                        projection.getPatientReasonProvenance(),
                        projection.getPatientReasonRecordedAt()));
        Optional<ProfessionalAttachmentSnapshot> attachment = Optional.ofNullable(projection.getAttachmentId())
                .map(ignored -> new ProfessionalAttachmentSnapshot(
                        projection.getAttachmentDisplayFilename(),
                        projection.getAttachmentMediaType(),
                        projection.getAttachmentSizeBytes(),
                        projection.getAttachmentUploadedAt(),
                        projection.getAttachmentUpdatedAt()));
        return new ProfessionalPreparationViewSnapshot(
                new PreparationCaseId(projection.getCaseId()),
                new PatientReference(projection.getPatientReference()),
                optionalAppointmentReference(projection.getAppointmentReference()),
                Optional.ofNullable(projection.getScheduledAt()),
                projection.getCaseStatus(),
                reason,
                attachment,
                projection.getCreatedAt(),
                projection.getUpdatedAt());
    }

    private Optional<AppointmentReference> optionalAppointmentReference(String value) {
        return Optional.ofNullable(value).map(AppointmentReference::new);
    }
}
