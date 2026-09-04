package backend.clinicalcontext.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record FactReview(
        FactReviewStatus status,
        Optional<Instant> reviewedAt,
        Optional<ClinicalReviewerReference> reviewer) {

    public FactReview {
        Objects.requireNonNull(status, "Fact review status is required");
        Objects.requireNonNull(reviewedAt, "Review timestamp container is required");
        Objects.requireNonNull(reviewer, "Reviewer container is required");
        boolean unreviewed = status == FactReviewStatus.UNREVIEWED;
        if (unreviewed != (reviewedAt.isEmpty() && reviewer.isEmpty())) {
            throw new IllegalArgumentException("Fact review metadata is inconsistent");
        }
        if (!unreviewed && (reviewedAt.isEmpty() || reviewer.isEmpty())) {
            throw new IllegalArgumentException("Reviewed fact metadata is incomplete");
        }
    }

    public static FactReview unreviewed() {
        return new FactReview(FactReviewStatus.UNREVIEWED, Optional.empty(), Optional.empty());
    }

    public static FactReview reviewed(
            FactReviewStatus status,
            Instant reviewedAt,
            ClinicalReviewerReference reviewer) {
        if (status == FactReviewStatus.UNREVIEWED) {
            throw new IllegalArgumentException("Reviewed fact requires a final review status");
        }
        return new FactReview(status, Optional.of(reviewedAt), Optional.of(reviewer));
    }

    @Override
    public String toString() {
        return "FactReview[status=" + status + ", reviewedAt=" + reviewedAt + ", reviewer=<redacted>]";
    }
}
