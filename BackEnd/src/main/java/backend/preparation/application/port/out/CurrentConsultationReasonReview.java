package backend.preparation.application.port.out;

import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;

/** Reads the current reason's review while the calling use case holds the case lock. */
public interface CurrentConsultationReasonReview {
    boolean isRejected(OrganizationId organizationId, PreparationCaseId caseId);
}
