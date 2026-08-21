package backend.preparation.adapter.in.web;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public final class PatientAttachmentUploadConcurrencyFilter extends OncePerRequestFilter {

    private static final String ATTACHMENT_PATH = "/api/v1/patient-access/attachment";

    private final ConcurrentHashMap<UUID, AtomicBoolean> uploadsBySession = new ConcurrentHashMap<>();
    private final PatientSecurityResponses securityResponses;

    public PatientAttachmentUploadConcurrencyFilter(PatientSecurityResponses securityResponses) {
        this.securityResponses = securityResponses;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.PUT.matches(request.getMethod())
                || !ATTACHMENT_PATH.equals(applicationPath(request));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof PatientPreparationPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID sessionId = principal.grant().sessionId();
        AtomicBoolean uploadSlot = uploadsBySession.computeIfAbsent(sessionId, ignored -> new AtomicBoolean());
        if (!uploadSlot.compareAndSet(false, true)) {
            securityResponses.tooManyRequests(response);
            return;
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            uploadsBySession.remove(sessionId, uploadSlot);
        }
    }

    private String applicationPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty() ? requestUri : requestUri.substring(contextPath.length());
    }
}
