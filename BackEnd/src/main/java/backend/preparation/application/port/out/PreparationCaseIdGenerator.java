package backend.preparation.application.port.out;

import backend.preparation.domain.PreparationCaseId;

public interface PreparationCaseIdGenerator {

    PreparationCaseId nextId();
}
