package backend.preparation.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import backend.preparation.application.port.out.PreparationTaskIdGenerator;
import backend.preparation.domain.PreparationTaskId;

@Component
public final class UuidPreparationTaskIdGenerator implements PreparationTaskIdGenerator {

    @Override
    public PreparationTaskId nextId() {
        return new PreparationTaskId(UUID.randomUUID());
    }
}
