package backend.clinicalcontext.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import backend.clinicalcontext.domain.ClinicalContextId;
import backend.clinicalcontext.domain.ClinicalFactId;
import backend.clinicalcontext.domain.ClinicalFactKind;
import backend.clinicalcontext.domain.FactCertainty;
import backend.clinicalcontext.domain.FactLifecycleStatus;
import backend.clinicalcontext.domain.FactReviewStatus;
import backend.clinicalcontext.domain.ProvenanceType;
import backend.preparation.domain.AttachmentId;
import backend.preparation.domain.AttachmentMediaType;
import backend.preparation.domain.AttachmentProvenance;
import backend.preparation.domain.PreparationCaseStatus;

public record ClinicalContextView(
        String schemaVersion,
        ClinicalContextId contextId,
        PreparationCaseStatus preparationStatus,
        Optional<Instant> scheduledAt,
        ContextReadiness readiness,
        List<PreparationItem> preparationItems,
        List<Fact> facts,
        Optional<Document> document,
        Instant generatedAt) {

    public static final String SCHEMA_VERSION = "twincare.clinical-context.v1";

    public ClinicalContextView {
        Objects.requireNonNull(schemaVersion, "Clinical context schema version is required");
        Objects.requireNonNull(contextId, "Clinical context identifier is required");
        Objects.requireNonNull(preparationStatus, "Preparation status is required");
        Objects.requireNonNull(scheduledAt, "Scheduled-at container is required");
        Objects.requireNonNull(readiness, "Context readiness is required");
        preparationItems = List.copyOf(preparationItems);
        facts = List.copyOf(facts);
        Objects.requireNonNull(document, "Document container is required");
        Objects.requireNonNull(generatedAt, "Context generation timestamp is required");
    }

    public enum ContextReadiness {
        COMPLETE,
        INCOMPLETE,
        NEEDS_CORRECTION
    }

    public enum PreparationItemCode {
        CONSULTATION_REASON,
        PATIENT_ATTACHMENT
    }

    public enum PreparationItemRequirement {
        REQUIRED,
        OPTIONAL
    }

    public enum PreparationItemStatus {
        COMPLETED,
        MISSING,
        NEEDS_CORRECTION,
        AVAILABLE,
        NOT_PROVIDED
    }

    public record PreparationItem(
            PreparationItemCode code,
            PreparationItemRequirement requirement,
            PreparationItemStatus status) {
    }

    public record Fact(
            ClinicalFactId id,
            ClinicalFactKind kind,
            String text,
            ProvenanceType provenance,
            FactCertainty certainty,
            Instant recordedAt,
            Optional<Instant> effectiveFrom,
            Optional<Instant> effectiveTo,
            FactLifecycleStatus lifecycleStatus,
            Optional<Instant> supersededAt,
            FactReviewStatus reviewStatus,
            Optional<Instant> reviewedAt) {

        @Override
        public String toString() {
            return "Fact[id=" + id + ", kind=" + kind + ", text=<redacted>, provenance="
                    + provenance + ", lifecycleStatus=" + lifecycleStatus
                    + ", reviewStatus=" + reviewStatus + "]";
        }
    }

    public record Document(
            AttachmentId id,
            String displayFilename,
            AttachmentMediaType mediaType,
            long sizeBytes,
            AttachmentProvenance provenance,
            Instant uploadedAt,
            Instant updatedAt) {

        @Override
        public String toString() {
            return "Document[id=" + id + ", displayFilename=<redacted>, mediaType="
                    + mediaType + ", sizeBytes=" + sizeBytes + ", provenance=" + provenance + "]";
        }
    }
}
