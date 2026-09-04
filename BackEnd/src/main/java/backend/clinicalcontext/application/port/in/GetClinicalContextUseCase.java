package backend.clinicalcontext.application.port.in;

public interface GetClinicalContextUseCase {

    ClinicalContextView get(GetClinicalContextQuery query);
}
