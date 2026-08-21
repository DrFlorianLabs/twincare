package backend.preparation.application.port.in;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import backend.preparation.domain.AppointmentReference;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientReference;

public record CreatePreparationCaseCommand(
        OrganizationId organizationId,
        PatientReference patientReference,
        Optional<AppointmentReference> appointmentReference,
        Optional<Instant> scheduledAt) {

    public CreatePreparationCaseCommand {
        Objects.requireNonNull(organizationId, "Organization identifier is required");
        Objects.requireNonNull(patientReference, "Patient reference is required");
        Objects.requireNonNull(appointmentReference, "Appointment reference container is required");
        Objects.requireNonNull(scheduledAt, "Scheduled time container is required");
    }
}
