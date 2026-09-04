package backend.clinicalcontext.application.port.out;

import backend.clinicalcontext.domain.ClinicalContextOwner;

@FunctionalInterface
public interface ClinicalContextReadAuthorization {

    void requireClinicalContextAccess(ClinicalContextOwner owner);
}
