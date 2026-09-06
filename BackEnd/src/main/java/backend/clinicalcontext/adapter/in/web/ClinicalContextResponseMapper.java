package backend.clinicalcontext.adapter.in.web;

import java.util.List;

import backend.clinicalcontext.application.port.in.ClinicalContextView;

public final class ClinicalContextResponseMapper {

    private ClinicalContextResponseMapper() {
    }

    public static ClinicalContextResponse from(ClinicalContextView view) {
        List<ClinicalContextResponse.PreparationItemResponse> items = view.preparationItems().stream()
                .map(item -> new ClinicalContextResponse.PreparationItemResponse(
                        item.code().name(), item.requirement().name(), item.status().name()))
                .toList();
        List<ClinicalContextResponse.FactResponse> facts = view.facts().stream()
                .map(fact -> new ClinicalContextResponse.FactResponse(
                        fact.id().value(),
                        fact.kind().name(),
                        new ClinicalContextResponse.TextValueResponse(fact.text()),
                        new ClinicalContextResponse.ProvenanceResponse(fact.provenance().name()),
                        fact.certainty().name(),
                        new ClinicalContextResponse.TemporalResponse(
                                fact.recordedAt(),
                                fact.effectiveFrom().orElse(null),
                                fact.effectiveTo().orElse(null)),
                        new ClinicalContextResponse.LifecycleResponse(
                                fact.lifecycleStatus().name(),
                                fact.supersededAt().orElse(null)),
                        new ClinicalContextResponse.ReviewResponse(
                                fact.reviewStatus().name(),
                                fact.reviewedAt().orElse(null))))
                .toList();
        List<ClinicalContextResponse.DocumentResponse> documents = view.document().stream()
                .map(document -> new ClinicalContextResponse.DocumentResponse(
                        document.id().value(),
                        document.displayFilename(),
                        document.mediaType().name(),
                        document.sizeBytes(),
                        document.provenance().name(),
                        document.uploadedAt(),
                        document.updatedAt()))
                .toList();
        return new ClinicalContextResponse(
                view.schemaVersion(),
                view.contextId().value(),
                view.preparationStatus().name(),
                view.preparationRevision(),
                view.scheduledAt().orElse(null),
                view.readiness().name(),
                items,
                facts,
                documents,
                view.generatedAt());
    }
}
