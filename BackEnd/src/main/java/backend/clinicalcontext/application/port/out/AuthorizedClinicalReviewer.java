package backend.clinicalcontext.application.port.out;

import java.util.Objects;

import backend.clinicalcontext.domain.ClinicalReviewerReference;

/** Server-authorized identity shared by clinical provenance and audit evidence. */
public record AuthorizedClinicalReviewer(
        ClinicalReviewerReference reference,
        long actorUserId) {

    public AuthorizedClinicalReviewer {
        Objects.requireNonNull(reference, "Clinical reviewer reference is required");
        if (actorUserId <= 0) {
            throw new IllegalArgumentException("Clinical reviewer audit identity must be positive");
        }
    }

    @Override
    public String toString() {
        return "AuthorizedClinicalReviewer[identity redacted]";
    }
}
