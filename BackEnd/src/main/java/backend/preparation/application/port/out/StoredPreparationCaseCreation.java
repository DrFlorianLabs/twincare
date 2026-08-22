package backend.preparation.application.port.out;

import java.util.Objects;

import backend.preparation.domain.PreparationCase;

public record StoredPreparationCaseCreation(
        PreparationCase preparationCase,
        PreparationCaseCreationFingerprint payloadFingerprint) {

    public StoredPreparationCaseCreation {
        Objects.requireNonNull(preparationCase, "Preparation case is required");
        Objects.requireNonNull(payloadFingerprint, "Creation request fingerprint is required");
    }
}
