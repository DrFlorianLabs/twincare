package backend.preparation.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class CreatePreparationTaskRequestTest {

    @Test
    void technicalRepresentationRedactsThePotentiallyClinicalLabel() {
        var label = "Retrieve synthetic clinical result";
        var request = new CreatePreparationTaskRequest(UUID.randomUUID(), label, 42L);

        assertThat(request.toString())
                .doesNotContain(label)
                .contains("label redacted");
    }
}
