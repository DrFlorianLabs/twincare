package backend.clinicalcontext.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class ConsultationReasonFact {

    public static final int MAX_TEXT_LENGTH = 4_000;

    private final ClinicalFactId id;
    private final ClinicalContextId contextId;
    private final ClinicalContextOwner owner;
    private final String text;
    private final ProvenanceType provenance;
    private final FactCertainty certainty;
    private final FactTemporalMetadata temporal;

    private FactLifecycleStatus lifecycleStatus;
    private Optional<Instant> supersededAt;
    private FactReview review;

    private ConsultationReasonFact(
            ClinicalFactId id,
            ClinicalContextId contextId,
            ClinicalContextOwner owner,
            String text,
            ProvenanceType provenance,
            FactCertainty certainty,
            FactTemporalMetadata temporal,
            FactLifecycleStatus lifecycleStatus,
            Optional<Instant> supersededAt,
            FactReview review) {
        this.id = Objects.requireNonNull(id, "Clinical fact identifier is required");
        this.contextId = Objects.requireNonNull(contextId, "Clinical context identifier is required");
        this.owner = Objects.requireNonNull(owner, "Clinical context owner is required");
        this.text = requireText(text);
        this.provenance = Objects.requireNonNull(provenance, "Fact provenance is required");
        this.certainty = Objects.requireNonNull(certainty, "Fact certainty is required");
        this.temporal = Objects.requireNonNull(temporal, "Fact temporal metadata is required");
        this.lifecycleStatus = Objects.requireNonNull(lifecycleStatus, "Fact lifecycle status is required");
        this.supersededAt = Objects.requireNonNull(supersededAt, "Superseded timestamp container is required");
        this.review = Objects.requireNonNull(review, "Fact review is required");
        validateState();
    }

    public static ConsultationReasonFact declaredByPatient(
            ClinicalFactId id,
            ClinicalContextId contextId,
            ClinicalContextOwner owner,
            String text,
            Instant recordedAt) {
        return new ConsultationReasonFact(
                id,
                contextId,
                owner,
                text,
                ProvenanceType.PATIENT_DECLARED,
                FactCertainty.NOT_ASSESSED,
                FactTemporalMetadata.recordedAtOnly(recordedAt),
                FactLifecycleStatus.CURRENT,
                Optional.empty(),
                FactReview.unreviewed());
    }

    public static ConsultationReasonFact restore(
            ClinicalFactId id,
            ClinicalContextId contextId,
            ClinicalContextOwner owner,
            String text,
            ProvenanceType provenance,
            FactCertainty certainty,
            FactTemporalMetadata temporal,
            FactLifecycleStatus lifecycleStatus,
            Optional<Instant> supersededAt,
            FactReview review) {
        return new ConsultationReasonFact(
                id,
                contextId,
                owner,
                text,
                provenance,
                certainty,
                temporal,
                lifecycleStatus,
                supersededAt,
                review);
    }

    public boolean supersede(Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "Fact supersession timestamp is required");
        if (lifecycleStatus == FactLifecycleStatus.SUPERSEDED) {
            return false;
        }
        if (occurredAt.isBefore(temporal.recordedAt())) {
            throw new InvalidClinicalFactTransitionException(
                    "Fact supersession cannot precede fact recording");
        }
        lifecycleStatus = FactLifecycleStatus.SUPERSEDED;
        supersededAt = Optional.of(occurredAt);
        return true;
    }

    public boolean review(
            FactReviewDecision decision,
            ClinicalReviewerReference reviewer,
            Instant occurredAt) {
        Objects.requireNonNull(decision, "Fact review decision is required");
        Objects.requireNonNull(reviewer, "Clinical reviewer reference is required");
        Objects.requireNonNull(occurredAt, "Fact review timestamp is required");
        if (lifecycleStatus != FactLifecycleStatus.CURRENT) {
            throw new InvalidClinicalFactTransitionException("A superseded fact cannot be reviewed");
        }
        if (occurredAt.isBefore(temporal.recordedAt())) {
            throw new InvalidClinicalFactTransitionException("Fact review cannot precede fact recording");
        }
        FactReviewStatus target = switch (decision) {
            case CONFIRM -> FactReviewStatus.CONFIRMED;
            case REJECT -> FactReviewStatus.REJECTED;
        };
        if (review.status() == target) {
            return false;
        }
        if (review.status() != FactReviewStatus.UNREVIEWED) {
            throw new InvalidClinicalFactTransitionException(
                    "A final fact review cannot be silently replaced");
        }
        review = FactReview.reviewed(target, occurredAt, reviewer);
        return true;
    }

    private void validateState() {
        boolean current = lifecycleStatus == FactLifecycleStatus.CURRENT;
        if (current != supersededAt.isEmpty()) {
            throw new IllegalArgumentException("Fact lifecycle metadata is inconsistent");
        }
        supersededAt.ifPresent(timestamp -> {
            if (timestamp.isBefore(temporal.recordedAt())) {
                throw new IllegalArgumentException("Fact supersession cannot precede fact recording");
            }
        });
        review.reviewedAt().ifPresent(timestamp -> {
            if (timestamp.isBefore(temporal.recordedAt())) {
                throw new IllegalArgumentException("Fact review cannot precede fact recording");
            }
        });
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Consultation reason fact text is required");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()
                || normalized.indexOf('\0') >= 0
                || normalized.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Consultation reason fact text is invalid");
        }
        return normalized;
    }

    public ClinicalFactId id() {
        return id;
    }

    public ClinicalContextId contextId() {
        return contextId;
    }

    public ClinicalContextOwner owner() {
        return owner;
    }

    public ClinicalFactKind kind() {
        return ClinicalFactKind.CONSULTATION_REASON;
    }

    public String text() {
        return text;
    }

    public ProvenanceType provenance() {
        return provenance;
    }

    public FactCertainty certainty() {
        return certainty;
    }

    public FactTemporalMetadata temporal() {
        return temporal;
    }

    public FactLifecycleStatus lifecycleStatus() {
        return lifecycleStatus;
    }

    public Optional<Instant> supersededAt() {
        return supersededAt;
    }

    public FactReview review() {
        return review;
    }

    @Override
    public String toString() {
        return "ConsultationReasonFact[id=" + id
                + ", contextId=" + contextId
                + ", owner=" + owner
                + ", text=<redacted>, provenance=" + provenance
                + ", lifecycleStatus=" + lifecycleStatus
                + ", review=" + review + "]";
    }
}
