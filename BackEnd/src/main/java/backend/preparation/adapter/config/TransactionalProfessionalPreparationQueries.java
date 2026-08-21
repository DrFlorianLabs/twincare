package backend.preparation.adapter.config;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import backend.preparation.application.port.in.GetProfessionalPreparationViewCommand;
import backend.preparation.application.port.in.GetProfessionalPreparationViewUseCase;
import backend.preparation.application.port.in.ListProfessionalPreparationsCommand;
import backend.preparation.application.port.in.ListProfessionalPreparationsUseCase;
import backend.preparation.application.port.in.ProfessionalPreparationPageResult;
import backend.preparation.application.port.in.ProfessionalPreparationViewResult;

public class TransactionalProfessionalPreparationQueries implements
        ListProfessionalPreparationsUseCase,
        GetProfessionalPreparationViewUseCase {

    private final ListProfessionalPreparationsUseCase listDelegate;
    private final GetProfessionalPreparationViewUseCase viewDelegate;

    public TransactionalProfessionalPreparationQueries(
            ListProfessionalPreparationsUseCase listDelegate,
            GetProfessionalPreparationViewUseCase viewDelegate) {
        this.listDelegate = listDelegate;
        this.viewDelegate = viewDelegate;
    }

    @Override
    @Transactional(readOnly = true)
    public ProfessionalPreparationPageResult list(ListProfessionalPreparationsCommand command) {
        return listDelegate.list(command);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProfessionalPreparationViewResult get(GetProfessionalPreparationViewCommand command) {
        return viewDelegate.get(command);
    }
}
