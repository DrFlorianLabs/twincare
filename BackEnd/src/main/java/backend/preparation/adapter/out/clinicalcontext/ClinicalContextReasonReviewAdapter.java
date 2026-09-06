package backend.preparation.adapter.out.clinicalcontext;

import java.util.Objects;
import org.springframework.stereotype.Component;

import backend.clinicalcontext.application.port.out.ClinicalFactRepository;
import backend.clinicalcontext.domain.ClinicalContextId;
import backend.clinicalcontext.domain.ClinicalContextOwner;
import backend.clinicalcontext.domain.FactReviewStatus;
import backend.preparation.application.port.out.CurrentConsultationReasonReview;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PreparationCaseId;

@Component
public final class ClinicalContextReasonReviewAdapter implements CurrentConsultationReasonReview {
    private final ClinicalFactRepository facts;

    public ClinicalContextReasonReviewAdapter(ClinicalFactRepository facts) {
        this.facts = Objects.requireNonNull(facts);
    }

    @Override
    public boolean isRejected(OrganizationId organizationId, PreparationCaseId caseId) {
        return facts.findCurrentConsultationReason(
                        new ClinicalContextId(caseId.value()),
                        new ClinicalContextOwner(organizationId.value()))
                .filter(fact -> fact.review().status() == FactReviewStatus.REJECTED)
                .isPresent();
    }
}
