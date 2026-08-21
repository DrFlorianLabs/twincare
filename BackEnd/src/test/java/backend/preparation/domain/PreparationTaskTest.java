package backend.preparation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PreparationTaskTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-21T10:00:00Z");

    @Test
    void createsHumanOwnedTaskAndCompletesItWithServerActors() {
        PreparationTask task = task();

        boolean changed = task.close(PreparationTaskStatus.COMPLETED, 43L, CREATED_AT.plusSeconds(60));

        assertThat(changed).isTrue();
        assertThat(task.status()).isEqualTo(PreparationTaskStatus.COMPLETED);
        assertThat(task.createdByUserId()).isEqualTo(42L);
        assertThat(task.assigneeUserId()).isEqualTo(44L);
        assertThat(task.closedByUserId()).contains(43L);
        assertThat(task.closedAt()).contains(CREATED_AT.plusSeconds(60));
    }

    @Test
    void identicalTerminalReplayIsANoOpButAConflictingOutcomeIsRejected() {
        PreparationTask task = task();
        task.close(PreparationTaskStatus.CANCELLED, 43L, CREATED_AT.plusSeconds(60));

        assertThat(task.close(PreparationTaskStatus.CANCELLED, 99L, CREATED_AT.plusSeconds(120)))
                .isFalse();
        assertThat(task.closedByUserId()).contains(43L);
        assertThatThrownBy(() -> task.close(
                PreparationTaskStatus.COMPLETED, 43L, CREATED_AT.plusSeconds(120)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void descriptionIsUnicodeBoundedSingleLineAndRedacted() {
        var description = new PreparationTaskDescription("  Vérifier le résultat synthétique  ");

        assertThat(description.value()).isEqualTo("Vérifier le résultat synthétique");
        assertThat(description.toString()).doesNotContain(description.value());
        assertThatThrownBy(() -> new PreparationTaskDescription("line one\nline two"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PreparationTaskDescription("x".repeat(241)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOpenTaskWithClosureEvidence() {
        assertThatThrownBy(() -> PreparationTask.restore(
                new PreparationTaskId(UUID.randomUUID()),
                new OrganizationId("synthetic-org"),
                new PreparationCaseId(UUID.randomUUID()),
                UUID.randomUUID(),
                new PreparationTaskDescription("Synthetic task"),
                44L,
                42L,
                CREATED_AT,
                PreparationTaskStatus.OPEN,
                Optional.of(43L),
                Optional.of(CREATED_AT),
                CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void taskRepresentationsNeverContainTheDescription() {
        PreparationTask task = task();

        assertThat(task.toString()).doesNotContain(task.description().value());
    }

    private PreparationTask task() {
        return PreparationTask.create(
                new PreparationTaskId(UUID.randomUUID()),
                new OrganizationId("synthetic-org"),
                new PreparationCaseId(UUID.randomUUID()),
                UUID.randomUUID(),
                new PreparationTaskDescription("Synthetic preparation task"),
                44L,
                42L,
                CREATED_AT);
    }
}
