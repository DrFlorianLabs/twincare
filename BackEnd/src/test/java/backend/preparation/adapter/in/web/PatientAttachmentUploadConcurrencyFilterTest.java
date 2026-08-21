package backend.preparation.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import backend.preparation.application.port.in.PatientAccessGrant;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientInvitationId;
import backend.preparation.domain.PreparationCaseId;

class PatientAttachmentUploadConcurrencyFilterTest {

    private static final PatientAccessGrant GRANT = new PatientAccessGrant(
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            new PatientInvitationId(UUID.fromString("20000000-0000-0000-0000-000000000001")),
            new OrganizationId("synthetic-org"),
            new PreparationCaseId(UUID.fromString("30000000-0000-0000-0000-000000000001")),
            Instant.parse("2026-08-21T13:00:00Z"));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsASecondUploadForTheSameSessionBeforeItsBodyIsProcessed() throws Exception {
        var filter = new PatientAttachmentUploadConcurrencyFilter(new PatientSecurityResponses(false));
        var firstEntered = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();

        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> {
                authenticate();
                filter.doFilter(
                        uploadRequest(),
                        firstResponse,
                        (request, response) -> {
                            firstEntered.countDown();
                            try {
                                if (!releaseFirst.await(5, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("Synthetic upload release timed out");
                                }
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(exception);
                            }
                        });
                SecurityContextHolder.clearContext();
                return firstResponse.getStatus();
            });
            assertThat(firstEntered.await(5, TimeUnit.SECONDS)).isTrue();

            authenticate();
            filter.doFilter(uploadRequest(), secondResponse, (request, response) -> {
                throw new AssertionError("Rejected upload body must not be processed");
            });
            SecurityContextHolder.clearContext();
            releaseFirst.countDown();

            assertThat(first.get()).isEqualTo(200);
        }
        assertThat(secondResponse.getStatus()).isEqualTo(429);
        assertThat(secondResponse.getHeader("Cache-Control")).contains("no-store");
        assertThat(secondResponse.getHeader("Retry-After")).isEqualTo("1");
    }

    private MockHttpServletRequest uploadRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/patient-access/attachment");
        request.setRequestURI("/api/v1/patient-access/attachment");
        return request;
    }

    private void authenticate() {
        var principal = new PatientPreparationPrincipal(GRANT);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority(PatientPreparationPrincipal.AUTHORITY))));
    }
}
