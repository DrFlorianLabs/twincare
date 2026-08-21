package backend.preparation.adapter.in.web;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public final class PatientMutationOriginFilter extends OncePerRequestFilter {

    private static final String EXCHANGE_PATH = "/api/v1/patient-access/exchange";
    private static final String PATIENT_ACCESS_PATH = "/api/v1/patient-access/";
    private static final Set<String> SAFE_METHODS = Set.of(
            HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name());

    private final Set<String> allowedOrigins;
    private final PatientSecurityResponses securityResponses;

    public PatientMutationOriginFilter(
            @Value("${app.preparation.patient-cors.allowed-origins}") String allowedOrigins,
            PatientSecurityResponses securityResponses) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        if (this.allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("At least one patient frontend origin is required");
        }
        if (this.allowedOrigins.stream().anyMatch(origin -> !isExactHttpOrigin(origin))) {
            throw new IllegalArgumentException("Patient frontend origins must be exact HTTP(S) origins");
        }
        this.securityResponses = securityResponses;
    }

    private boolean isExactHttpOrigin(String origin) {
        try {
            URI uri = URI.create(origin);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && (uri.getPath() == null || uri.getPath().isEmpty())
                    && uri.getPort() <= 65535;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath.isEmpty() ? requestUri : requestUri.substring(contextPath.length());
        return !path.startsWith(PATIENT_ACCESS_PATH)
                || EXCHANGE_PATH.equals(path)
                || SAFE_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof PatientPreparationPrincipal)) {
            filterChain.doFilter(request, response);
            return;
        }
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || !allowedOrigins.contains(origin)) {
            securityResponses.forbidden(response);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
