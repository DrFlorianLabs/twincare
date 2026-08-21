package backend.preparation.application.port.out;

import backend.preparation.domain.PreparationTaskId;

public interface PreparationTaskIdGenerator {

    PreparationTaskId nextId();
}
