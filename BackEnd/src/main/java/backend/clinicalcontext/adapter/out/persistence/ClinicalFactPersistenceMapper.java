package backend.clinicalcontext.adapter.out.persistence;

import java.util.Optional;

import backend.clinicalcontext.domain.ClinicalContextId;
import backend.clinicalcontext.domain.ClinicalContextOwner;
import backend.clinicalcontext.domain.ClinicalFactId;
import backend.clinicalcontext.domain.ClinicalReviewerReference;
import backend.clinicalcontext.domain.ConsultationReasonFact;
import backend.clinicalcontext.domain.FactLifecycleStatus;
import backend.clinicalcontext.domain.FactReview;
import backend.clinicalcontext.domain.FactReviewStatus;
import backend.clinicalcontext.domain.FactTemporalMetadata;

final class ClinicalFactPersistenceMapper {

    ClinicalFactJpaEntity toEntity(ConsultationReasonFact fact) {
        return new ClinicalFactJpaEntity(
                fact.id().value(),
                fact.contextId().value(),
                fact.owner().value(),
                fact.kind(),
                fact.text(),
                fact.provenance(),
                fact.certainty(),
                fact.temporal().recordedAt(),
                fact.temporal().effectiveFrom().orElse(null),
                fact.temporal().effectiveTo().orElse(null),
                fact.lifecycleStatus(),
                fact.lifecycleStatus() == FactLifecycleStatus.CURRENT ? (short) 1 : null,
                fact.supersededAt().orElse(null),
                fact.review().status(),
                fact.review().reviewedAt().orElse(null),
                fact.review().reviewer().map(ClinicalReviewerReference::value).orElse(null));
    }

    ConsultationReasonFact toDomain(ClinicalFactJpaEntity entity) {
        if (entity.kind() != backend.clinicalcontext.domain.ClinicalFactKind.CONSULTATION_REASON) {
            throw new IllegalStateException("Unknown clinical fact kind cannot be restored");
        }
        FactReview review = entity.reviewStatus() == FactReviewStatus.UNREVIEWED
                ? FactReview.unreviewed()
                : FactReview.reviewed(
                        entity.reviewStatus(),
                        entity.reviewedAt(),
                        new ClinicalReviewerReference(entity.reviewerReference()));
        return ConsultationReasonFact.restore(
                new ClinicalFactId(entity.id()),
                new ClinicalContextId(entity.contextId()),
                new ClinicalContextOwner(entity.ownerId()),
                entity.textValue(),
                entity.provenance(),
                entity.certainty(),
                new FactTemporalMetadata(
                        entity.recordedAt(),
                        Optional.ofNullable(entity.effectiveFrom()),
                        Optional.ofNullable(entity.effectiveTo())),
                entity.lifecycleStatus(),
                Optional.ofNullable(entity.supersededAt()),
                review);
    }
}
