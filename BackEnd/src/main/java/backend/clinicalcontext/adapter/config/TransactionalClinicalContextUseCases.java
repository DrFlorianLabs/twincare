package backend.clinicalcontext.adapter.config;

import org.springframework.transaction.annotation.Transactional;

import backend.clinicalcontext.application.port.in.ClinicalContextView;
import backend.clinicalcontext.application.port.in.GetClinicalContextQuery;
import backend.clinicalcontext.application.port.in.GetClinicalContextUseCase;
import backend.clinicalcontext.application.port.in.ReviewClinicalFactCommand;
import backend.clinicalcontext.application.port.in.ReviewClinicalFactResult;
import backend.clinicalcontext.application.port.in.ReviewClinicalFactUseCase;

class TransactionalClinicalContextUseCases
        implements GetClinicalContextUseCase, ReviewClinicalFactUseCase {

    private final GetClinicalContextUseCase getDelegate;
    private final ReviewClinicalFactUseCase reviewDelegate;

    TransactionalClinicalContextUseCases(
            GetClinicalContextUseCase getDelegate,
            ReviewClinicalFactUseCase reviewDelegate) {
        this.getDelegate = getDelegate;
        this.reviewDelegate = reviewDelegate;
    }

    @Override
    @Transactional(readOnly = true)
    public ClinicalContextView get(GetClinicalContextQuery query) {
        return getDelegate.get(query);
    }

    @Override
    @Transactional
    public ReviewClinicalFactResult review(ReviewClinicalFactCommand command) {
        return reviewDelegate.review(command);
    }
}
