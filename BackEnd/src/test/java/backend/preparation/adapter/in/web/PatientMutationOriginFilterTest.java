package backend.preparation.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PatientMutationOriginFilterTest {

    private final PatientSecurityResponses responses = new PatientSecurityResponses(true);

    @Test
    void acceptsOnlyExactHttpOrigins() {
        assertThatCode(() -> new PatientMutationOriginFilter(
                "https://patient.synthetic.invalid,http://localhost:3000", responses))
                .doesNotThrowAnyException();

        for (String invalid : new String[] {
                "*",
                "https://patient.synthetic.invalid/",
                "https://user@patient.synthetic.invalid",
                "javascript:alert(1)"
        }) {
            assertThatThrownBy(() -> new PatientMutationOriginFilter(invalid, responses))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining(invalid);
        }
    }
}
