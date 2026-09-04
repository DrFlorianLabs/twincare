package backend.clinicalcontext.adapter.in.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClinicalContextResponse(
        String schemaVersion,
        UUID contextId,
        String preparationStatus,
        Instant scheduledAt,
        String readiness,
        List<PreparationItemResponse> preparationItems,
        List<FactResponse> facts,
        List<DocumentResponse> documents,
        Instant generatedAt) {

    public record PreparationItemResponse(
            String code,
            String requirement,
            String status) {
    }

    public record FactResponse(
            UUID id,
            String kind,
            TextValueResponse value,
            ProvenanceResponse provenance,
            String certainty,
            TemporalResponse temporal,
            LifecycleResponse lifecycle,
            ReviewResponse review) {
    }

    public record TextValueResponse(String text) {

        @Override
        public String toString() {
            return "TextValueResponse[text=<redacted>]";
        }
    }

    public record ProvenanceResponse(String type) {
    }

    public record TemporalResponse(
            Instant recordedAt,
            Instant effectiveFrom,
            Instant effectiveTo) {
    }

    public record LifecycleResponse(
            String status,
            Instant supersededAt) {
    }

    public record ReviewResponse(
            String status,
            Instant reviewedAt) {
    }

    public record DocumentResponse(
            UUID id,
            String displayFilename,
            String mediaType,
            long sizeBytes,
            String provenance,
            Instant uploadedAt,
            Instant updatedAt) {

        @Override
        public String toString() {
            return "DocumentResponse[id=" + id + ", displayFilename=<redacted>, mediaType="
                    + mediaType + ", sizeBytes=" + sizeBytes + ", provenance=" + provenance + "]";
        }
    }
}
