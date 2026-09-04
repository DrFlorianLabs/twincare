package backend.clinicalcontext.application.port.out;

import backend.clinicalcontext.domain.ClinicalContextOwner;
import backend.clinicalcontext.domain.ClinicalReviewerReference;

public interface ClinicalFactReviewAuthorization {

    ClinicalReviewerReference requireClinicalReviewerAccess(ClinicalContextOwner owner);
}
