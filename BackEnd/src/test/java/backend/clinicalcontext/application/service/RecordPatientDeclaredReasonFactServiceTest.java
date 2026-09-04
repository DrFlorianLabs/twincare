package backend.clinicalcontext.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import backend.clinicalcontext.application.port.in.RecordPatientDeclaredReasonFactCommand;
import backend.clinicalcontext.application.port.out.ClinicalFactRepository;
import backend.clinicalcontext.domain.ClinicalContextId;
import backend.clinicalcontext.domain.ClinicalContextOwner;
import backend.clinicalcontext.domain.ClinicalFactId;
import backend.clinicalcontext.domain.ConsultationReasonFact;
import backend.clinicalcontext.domain.FactLifecycleStatus;

class RecordPatientDeclaredReasonFactServiceTest {

    private static final ClinicalContextId CONTEXT_ID = new ClinicalContextId(
            UUID.fromString("74e7a334-f71a-4418-a094-893a29cae8f1"));
    private static final ClinicalContextOwner OWNER = new ClinicalContextOwner("synthetic-clinic");
    private static final Instant FIRST_AT = Instant.parse("2026-08-28T08:00:00Z");

    @Test
    void appendsANewAtomicFactAndSupersedesThePreviousAssertion() {
        var repository = new InMemoryClinicalFactRepository();
        var ids = List.of(
                new ClinicalFactId(UUID.fromString("a691f74a-da31-41b5-b9b0-9674493c6bba")),
                new ClinicalFactId(UUID.fromString("2d8e0b91-0874-4031-9d3b-2106db63923d")));
        var service = new RecordPatientDeclaredReasonFactService(
                repository,
                new backend.clinicalcontext.application.port.out.ClinicalFactIdGenerator() {
                    private int index;

                    @Override
                    public ClinicalFactId nextId() {
                        return ids.get(index++);
                    }
                });

        service.record(command("Premier motif synthétique", FIRST_AT));
        service.record(command("Motif synthétique corrigé", FIRST_AT.plusSeconds(60)));

        assertThat(repository.facts).hasSize(2);
        assertThat(repository.facts.get(0).text()).isEqualTo("Premier motif synthétique");
        assertThat(repository.facts.get(0).lifecycleStatus()).isEqualTo(FactLifecycleStatus.SUPERSEDED);
        assertThat(repository.facts.get(0).supersededAt()).contains(FIRST_AT.plusSeconds(60));
        assertThat(repository.facts.get(1).text()).isEqualTo("Motif synthétique corrigé");
        assertThat(repository.facts.get(1).lifecycleStatus()).isEqualTo(FactLifecycleStatus.CURRENT);
    }

    private RecordPatientDeclaredReasonFactCommand command(String text, Instant recordedAt) {
        return new RecordPatientDeclaredReasonFactCommand(CONTEXT_ID, OWNER, text, recordedAt);
    }

    private static final class InMemoryClinicalFactRepository implements ClinicalFactRepository {
        private final List<ConsultationReasonFact> facts = new ArrayList<>();

        @Override
        public void add(ConsultationReasonFact fact) {
            facts.add(fact);
        }

        @Override
        public void update(ConsultationReasonFact fact) {
        }

        @Override
        public Optional<ConsultationReasonFact> findCurrentConsultationReason(
                ClinicalContextId contextId,
                ClinicalContextOwner owner) {
            return facts.stream()
                    .filter(fact -> fact.contextId().equals(contextId)
                            && fact.owner().equals(owner)
                            && fact.lifecycleStatus() == FactLifecycleStatus.CURRENT)
                    .findFirst();
        }

        @Override
        public Optional<ConsultationReasonFact> findByIdForUpdate(
                ClinicalFactId factId,
                ClinicalContextId contextId,
                ClinicalContextOwner owner) {
            return Optional.empty();
        }

        @Override
        public List<ConsultationReasonFact> findAllByContext(
                ClinicalContextId contextId,
                ClinicalContextOwner owner) {
            return List.copyOf(facts);
        }
    }
}
