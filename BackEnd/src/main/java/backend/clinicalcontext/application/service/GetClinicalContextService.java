package backend.clinicalcontext.application.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import backend.clinicalcontext.application.ClinicalContextNotFoundException;
import backend.clinicalcontext.application.port.in.ClinicalContextView;
import backend.clinicalcontext.application.port.in.ClinicalContextView.ContextReadiness;
import backend.clinicalcontext.application.port.in.ClinicalContextView.PreparationItem;
import backend.clinicalcontext.application.port.in.ClinicalContextView.PreparationItemCode;
import backend.clinicalcontext.application.port.in.ClinicalContextView.PreparationItemRequirement;
import backend.clinicalcontext.application.port.in.ClinicalContextView.PreparationItemStatus;
import backend.clinicalcontext.application.port.in.GetClinicalContextQuery;
import backend.clinicalcontext.application.port.in.GetClinicalContextUseCase;
import backend.clinicalcontext.application.port.out.ClinicalContextReadAuthorization;
import backend.clinicalcontext.application.port.out.ClinicalFactRepository;
import backend.clinicalcontext.domain.ClinicalContextId;
import backend.clinicalcontext.domain.ClinicalContextOwner;
import backend.clinicalcontext.domain.ConsultationReasonFact;
import backend.clinicalcontext.domain.FactReviewStatus;
import backend.preparation.application.port.out.PreparationAttachmentRepository;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.domain.PreparationAttachment;
import backend.preparation.domain.PreparationCase;

public final class GetClinicalContextService implements GetClinicalContextUseCase {

    private final ClinicalContextReadAuthorization authorization;
    private final PreparationCaseRepository preparationCases;
    private final ClinicalFactRepository facts;
    private final PreparationAttachmentRepository attachments;
    private final Clock clock;

    public GetClinicalContextService(
            ClinicalContextReadAuthorization authorization,
            PreparationCaseRepository preparationCases,
            ClinicalFactRepository facts,
            PreparationAttachmentRepository attachments,
            Clock clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.preparationCases = Objects.requireNonNull(preparationCases);
        this.facts = Objects.requireNonNull(facts);
        this.attachments = Objects.requireNonNull(attachments);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public ClinicalContextView get(GetClinicalContextQuery query) {
        Objects.requireNonNull(query, "Clinical context query is required");
        authorization.requireClinicalContextAccess(
                new ClinicalContextOwner(query.organizationId().value()));
        // Every visible-content writer locks this aggregate first. Hold that same
        // lock until all context parts are loaded, including on the H2 demo database.
        PreparationCase preparationCase = preparationCases
                .findByIdForUpdate(query.preparationCaseId(), query.organizationId())
                .orElseThrow(ClinicalContextNotFoundException::new);

        ClinicalContextId contextId = new ClinicalContextId(preparationCase.id().value());
        ClinicalContextOwner owner = new ClinicalContextOwner(preparationCase.organizationId().value());
        List<ConsultationReasonFact> clinicalFacts = facts.findAllByContext(contextId, owner);
        Optional<ConsultationReasonFact> currentReason = clinicalFacts.stream()
                .filter(fact -> fact.lifecycleStatus()
                        == backend.clinicalcontext.domain.FactLifecycleStatus.CURRENT)
                .findFirst();
        Optional<PreparationAttachment> attachment = attachments.findByCase(
                preparationCase.id(), preparationCase.organizationId());

        return new ClinicalContextView(
                ClinicalContextView.SCHEMA_VERSION,
                contextId,
                preparationCase.status(),
                preparationCase.revision(),
                preparationCase.scheduledAt(),
                readiness(currentReason),
                preparationItems(currentReason, attachment),
                clinicalFacts.stream().map(this::factView).toList(),
                attachment.map(this::documentView),
                clock.instant());
    }

    private ContextReadiness readiness(Optional<ConsultationReasonFact> currentReason) {
        if (currentReason.isEmpty()) {
            return ContextReadiness.INCOMPLETE;
        }
        if (currentReason.orElseThrow().review().status() == FactReviewStatus.REJECTED) {
            return ContextReadiness.NEEDS_CORRECTION;
        }
        return ContextReadiness.COMPLETE;
    }

    private List<PreparationItem> preparationItems(
            Optional<ConsultationReasonFact> currentReason,
            Optional<PreparationAttachment> attachment) {
        var items = new ArrayList<PreparationItem>();
        PreparationItemStatus reasonStatus;
        if (currentReason.isEmpty()) {
            reasonStatus = PreparationItemStatus.MISSING;
        } else if (currentReason.orElseThrow().review().status() == FactReviewStatus.REJECTED) {
            reasonStatus = PreparationItemStatus.NEEDS_CORRECTION;
        } else {
            reasonStatus = PreparationItemStatus.COMPLETED;
        }
        items.add(new PreparationItem(
                PreparationItemCode.CONSULTATION_REASON,
                PreparationItemRequirement.REQUIRED,
                reasonStatus));
        items.add(new PreparationItem(
                PreparationItemCode.PATIENT_ATTACHMENT,
                PreparationItemRequirement.OPTIONAL,
                attachment.isPresent()
                        ? PreparationItemStatus.AVAILABLE
                        : PreparationItemStatus.NOT_PROVIDED));
        return List.copyOf(items);
    }

    private ClinicalContextView.Fact factView(ConsultationReasonFact fact) {
        return new ClinicalContextView.Fact(
                fact.id(),
                fact.kind(),
                fact.text(),
                fact.provenance(),
                fact.certainty(),
                fact.temporal().recordedAt(),
                fact.temporal().effectiveFrom(),
                fact.temporal().effectiveTo(),
                fact.lifecycleStatus(),
                fact.supersededAt(),
                fact.review().status(),
                fact.review().reviewedAt());
    }

    private ClinicalContextView.Document documentView(PreparationAttachment attachment) {
        return new ClinicalContextView.Document(
                attachment.id(),
                attachment.displayFilename(),
                attachment.mediaType(),
                attachment.sizeBytes(),
                attachment.provenance(),
                attachment.uploadedAt(),
                attachment.updatedAt());
    }
}
