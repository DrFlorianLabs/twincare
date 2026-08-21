package backend.preparation.adapter.in.web;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.stereotype.Component;

import backend.preparation.application.port.out.PatientCsrfTokenIssuer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public final class PatientCsrfTokenRepository implements CsrfTokenRepository {

    public static final String HEADER_NAME = "X-TC-CSRF";
    private static final String PARAMETER_NAME = "_csrf";
    private static final String UNAUTHENTICATED_TOKEN = "A".repeat(43);

    private final PatientCsrfTokenIssuer tokenIssuer;

    public PatientCsrfTokenRepository(PatientCsrfTokenIssuer tokenIssuer) {
        this.tokenIssuer = tokenIssuer;
    }

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof PatientPreparationPrincipal principal)) {
            return new DefaultCsrfToken(HEADER_NAME, PARAMETER_NAME, UNAUTHENTICATED_TOKEN);
        }
        String token = tokenIssuer.issueFor(principal.grant()).value();
        return new DefaultCsrfToken(HEADER_NAME, PARAMETER_NAME, token);
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        // The token is a deterministic HMAC bound to the authenticated session grant.
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        return generateToken(request);
    }
}
