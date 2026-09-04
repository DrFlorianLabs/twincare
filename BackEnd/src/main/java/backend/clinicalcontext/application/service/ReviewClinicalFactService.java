package backend.clinicalcontext.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import backend.clinicalcontext.application.ClinicalContextNotFoundException;
import backend.clinicalcontext.application.ClinicalFactConflictException;
import backend.clinicalcontext.application.ClinicalFactNotFoundException;
import backend.clinicalcontext.application.port.in.ReviewClinicalFactCommand;
import backend.clinicalcontext.application.port.in.ReviewClinicalFactResult;
import backend.clinicalcontext.application.port.in.ReviewClinicalFactUseCase;
import backend.clinicalcontext.application.port.out.ClinicalFactRepository;
import backend.clinicalcontext.application.port.out.ClinicalFactReviewAuthorization;
import backend.clinicalcontext.domain.ClinicalContextId;
import backend.clinicalcontext.domain.ClinicalContextOwner;
import backend.clinicalcontext.domain.ConsultationReasonFact;
import backend.clinicalcontext.domain.InvalidClinicalFactTransitionException;
import backend.preparation.application.port.out.PreparationCaseRepository;

public final class ReviewClinicalFactService implements ReviewClinicalFactUseCase {

    private final ClinicalFactReviewAuthorization authorization;
    private final PreparationCaseRepository preparationCases;
    private final ClinicalFactRepository facts;
    private final Clock clock;

    public ReviewClinicalFactService(
            ClinicalFactReviewAuthorization authorization,
            PreparationCaseRepository preparationCases,
            ClinicalFactRepository facts,
            Clock clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.preparationCases = Objects.requireNonNull(preparationCases);
        this.facts = Objects.requireNonNull(facts);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public ReviewClinicalFactResult review(ReviewClinicalFactCommand command) {
        Objects.requireNonNull(command, "Clinical fact review command is required");
        ClinicalContextOwner owner = new ClinicalContextOwner(command.organizationId().value());
        var reviewer = authorization.requireClinicalReviewerAccess(owner);
        var preparationCase = preparationCases
                .findByIdForUpdate(command.preparationCaseId(), command.organizationId())
                .orElseThrow(ClinicalContextNotFoundException::new);
        if (preparationCase.status().isTerminal()) {
            throw new ClinicalFactConflictException();
        }

        ConsultationReasonFact fact = facts.findByIdForUpdate(
                        command.factId(),
                        new ClinicalContextId(command.preparationCaseId().value()),
                        owner)
                .orElseThrow(ClinicalFactNotFoundException::new);
        // Match TIMESTAMP(6) so the initial response and a persisted retry agree,
        // including on platforms whose system clock exposes nanoseconds.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        try {
            if (fact.review(command.decision(), reviewer, now)) {
                facts.update(fact);
                preparationCase.touchVisibleContent(now);
                preparationCases.update(preparationCase);
            }
        } catch (InvalidClinicalFactTransitionException exception) {
            throw new ClinicalFactConflictException();
        }
        return new ReviewClinicalFactResult(
                fact.id(),
                fact.review().status(),
                fact.review().reviewedAt().orElseThrow());
    }
}
