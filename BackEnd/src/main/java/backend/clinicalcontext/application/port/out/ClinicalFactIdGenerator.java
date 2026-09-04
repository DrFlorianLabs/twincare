package backend.clinicalcontext.application.port.out;

import backend.clinicalcontext.domain.ClinicalFactId;

public interface ClinicalFactIdGenerator {

    ClinicalFactId nextId();
}
