package backend.preparation.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PreparationCase {

    private final PreparationCaseId id;
    private final OrganizationId organizationId;
    private final PatientReference patientReference;
    private final Optional<AppointmentReference> appointmentReference;
    private final Optional<Instant> scheduledAt;
    private final Instant createdAt;
    private final List<PreparationCaseEvent> domainEvents = new ArrayList<>();

    private PreparationCaseStatus status;
    private Optional<PatientStatement> patientReason;
    private Instant updatedAt;

    private PreparationCase(
            PreparationCaseId id,
            OrganizationId organizationId,
            PatientReference patientReference,
            Optional<AppointmentReference> appointmentReference,
            Optional<Instant> scheduledAt,
            Instant createdAt,
            PreparationCaseStatus status,
            Optional<PatientStatement> patientReason,
            Instant updatedAt,
            boolean emitCreatedEvent) {
        this.id = Objects.requireNonNull(id, "Preparation case identifier is required");
        this.organizationId = Objects.requireNonNull(organizationId, "Organization identifier is required");
        this.patientReference = Objects.requireNonNull(patientReference, "Patient reference is required");
        this.appointmentReference = Objects.requireNonNull(
                appointmentReference, "Appointment reference container is required");
        this.scheduledAt = Objects.requireNonNull(scheduledAt, "Scheduled time container is required");
        this.createdAt = Objects.requireNonNull(createdAt, "Creation timestamp is required");
        this.status = Objects.requireNonNull(status, "Preparation case status is required");
        this.patientReason = Objects.requireNonNull(patientReason, "Patient reason container is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Update timestamp is required");
        validateState();
        if (emitCreatedEvent) {
            domainEvents.add(new PreparationCaseCreated(id, organizationId, createdAt));
        }
    }

    public static PreparationCase create(
            PreparationCaseId id,
            OrganizationId organizationId,
            PatientReference patientReference,
            Optional<AppointmentReference> appointmentReference,
            Optional<Instant> scheduledAt,
            Instant createdAt) {
        return new PreparationCase(
                id,
                organizationId,
                patientReference,
                appointmentReference,
                scheduledAt,
                createdAt,
                PreparationCaseStatus.CREATED,
                Optional.empty(),
                createdAt,
                true);
    }

    public static PreparationCase restore(
            PreparationCaseId id,
            OrganizationId organizationId,
            PatientReference patientReference,
            Optional<AppointmentReference> appointmentReference,
            Optional<Instant> scheduledAt,
            Instant createdAt,
            PreparationCaseStatus status,
            Optional<PatientStatement> patientReason,
            Instant updatedAt) {
        return new PreparationCase(
                id,
                organizationId,
                patientReference,
                appointmentReference,
                scheduledAt,
                createdAt,
                status,
                patientReason,
                updatedAt,
                false);
    }

    private void validateState() {
        if (appointmentReference.isEmpty() && scheduledAt.isEmpty()) {
            throw new IllegalArgumentException(
                    "A preparation case requires an appointment reference or consultation deadline");
        }
        if (scheduledAt.filter(scheduled -> scheduled.isBefore(createdAt)).isPresent()) {
            throw new IllegalArgumentException("Consultation deadline cannot precede case creation");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Update timestamp cannot precede creation timestamp");
        }
        patientReason.ifPresent(statement -> {
            if (statement.recordedAt().isBefore(createdAt) || statement.recordedAt().isAfter(updatedAt)) {
                throw new IllegalArgumentException("Patient statement timestamp is outside the case timeline");
            }
        });
        boolean reasonRequired = status == PreparationCaseStatus.COLLECTING
                || status == PreparationCaseStatus.READY_FOR_REVIEW
                || status == PreparationCaseStatus.NEEDS_ACTION
                || status == PreparationCaseStatus.READY_FOR_CONSULTATION
                || status == PreparationCaseStatus.CLOSED;
        if (reasonRequired && patientReason.isEmpty()) {
            throw new IllegalArgumentException("The restored preparation case status requires a patient reason");
        }
        if ((status == PreparationCaseStatus.CREATED || status == PreparationCaseStatus.PATIENT_STARTED)
                && patientReason.isPresent()) {
            throw new IllegalArgumentException("The restored preparation case status cannot contain a patient reason");
        }
    }

    public void startPatientPreparation(Instant occurredAt) {
        transitionTo(PreparationCaseStatus.PATIENT_STARTED, occurredAt);
    }

    public boolean recordPatientReason(String reason, Instant occurredAt) {
        assertChronological(occurredAt);
        if (status != PreparationCaseStatus.PATIENT_STARTED
                && status != PreparationCaseStatus.COLLECTING
                && status != PreparationCaseStatus.NEEDS_ACTION) {
            throw new InvalidPreparationCaseTransitionException(status, PreparationCaseStatus.COLLECTING);
        }

        PatientStatement statement = PatientStatement.declaredByPatient(reason, occurredAt);
        if (patientReason.map(existing -> existing.text().equals(statement.text())).orElse(false)) {
            return false;
        }
        if (status != PreparationCaseStatus.COLLECTING) {
            transitionTo(PreparationCaseStatus.COLLECTING, occurredAt);
        }
        patientReason = Optional.of(statement);
        updatedAt = occurredAt;
        domainEvents.add(new PatientReasonRecorded(id, organizationId, statement.provenance(), occurredAt));
        return true;
    }

    public void markReadyForReview(Instant occurredAt) {
        if (patientReason.isEmpty()) {
            throw new IllegalStateException("A patient reason is required before review");
        }
        transitionTo(PreparationCaseStatus.READY_FOR_REVIEW, occurredAt);
    }

    public void markNeedsAction(Instant occurredAt) {
        transitionTo(PreparationCaseStatus.NEEDS_ACTION, occurredAt);
    }

    public void markReadyForConsultation(Instant occurredAt) {
        transitionTo(PreparationCaseStatus.READY_FOR_CONSULTATION, occurredAt);
    }

    public void close(Instant occurredAt) {
        transitionTo(PreparationCaseStatus.CLOSED, occurredAt);
    }

    public void expire(Instant occurredAt) {
        transitionTo(PreparationCaseStatus.EXPIRED, occurredAt);
    }

    public void cancel(Instant occurredAt) {
        transitionTo(PreparationCaseStatus.CANCELLED, occurredAt);
    }

    private void transitionTo(PreparationCaseStatus target, Instant occurredAt) {
        Objects.requireNonNull(target, "Target status is required");
        assertChronological(occurredAt);
        if (!status.canTransitionTo(target)) {
            throw new InvalidPreparationCaseTransitionException(status, target);
        }

        PreparationCaseStatus previous = status;
        status = target;
        updatedAt = occurredAt;
        domainEvents.add(new PreparationCaseStatusChanged(
                id, organizationId, previous, target, occurredAt));
    }

    private void assertChronological(Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "Operation timestamp is required");
        if (occurredAt.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Operation timestamp cannot precede the previous case event");
        }
    }

    public List<PreparationCaseEvent> releaseDomainEvents() {
        List<PreparationCaseEvent> released = List.copyOf(domainEvents);
        domainEvents.clear();
        return released;
    }

    public PreparationCaseId id() {
        return id;
    }

    public OrganizationId organizationId() {
        return organizationId;
    }

    public PatientReference patientReference() {
        return patientReference;
    }

    public Optional<AppointmentReference> appointmentReference() {
        return appointmentReference;
    }

    public Optional<Instant> scheduledAt() {
        return scheduledAt;
    }

    public PreparationCaseStatus status() {
        return status;
    }

    public Optional<PatientStatement> patientReason() {
        return patientReason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
