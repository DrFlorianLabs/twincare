package backend.clinicalcontext.application.port.out;

import backend.clinicalcontext.domain.ClinicalContextOwner;

public interface ClinicalFactReviewAuthorization {

    AuthorizedClinicalReviewer requireClinicalReviewerAccess(ClinicalContextOwner owner);
}
