package backend.preparation.application.port.out;

import java.util.OptionalLong;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;

public interface PreparationAuditEventReader {

    PreparationAuditEventPageSnapshot readPage(
            OrganizationId organizationId,
            PreparationCaseId preparationCaseId,
            OptionalLong beforeSequence,
            int limit);
}
