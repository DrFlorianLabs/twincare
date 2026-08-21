package backend.preparation.adapter.out.id;

import org.springframework.stereotype.Component;

import backend.preparation.application.port.out.PreparationCaseIdGenerator;
import backend.preparation.domain.PreparationCaseId;

@Component
public class UuidPreparationCaseIdGenerator implements PreparationCaseIdGenerator {

    @Override
    public PreparationCaseId nextId() {
        return PreparationCaseId.newId();
    }
}
