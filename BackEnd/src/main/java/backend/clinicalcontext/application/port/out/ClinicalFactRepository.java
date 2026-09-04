package backend.clinicalcontext.application.port.out;

import java.util.List;
import java.util.Optional;

import backend.clinicalcontext.domain.ClinicalContextId;
import backend.clinicalcontext.domain.ClinicalContextOwner;
import backend.clinicalcontext.domain.ClinicalFactId;
import backend.clinicalcontext.domain.ConsultationReasonFact;

public interface ClinicalFactRepository {

    void add(ConsultationReasonFact fact);

    void update(ConsultationReasonFact fact);

    Optional<ConsultationReasonFact> findCurrentConsultationReason(
            ClinicalContextId contextId,
            ClinicalContextOwner owner);

    Optional<ConsultationReasonFact> findByIdForUpdate(
            ClinicalFactId factId,
            ClinicalContextId contextId,
            ClinicalContextOwner owner);

    List<ConsultationReasonFact> findAllByContext(
            ClinicalContextId contextId,
            ClinicalContextOwner owner);
}
