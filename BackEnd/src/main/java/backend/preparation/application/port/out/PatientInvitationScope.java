package backend.preparation.application.port.out;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientInvitationId;
import backend.preparation.domain.PreparationCaseId;

public record PatientInvitationScope(
        PatientInvitationId invitationId,
        OrganizationId organizationId,
        PreparationCaseId preparationCaseId) {
}
