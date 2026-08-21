package backend.preparation.adapter.in.web;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import backend.preparation.application.PatientSessionUnavailableException;
import backend.preparation.application.port.in.ResolvePatientAccessSessionCommand;
import backend.preparation.application.port.in.ResolvePatientAccessSessionUseCase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public final class PatientSessionAuthenticationFilter extends OncePerRequestFilter {

    private static final String EXCHANGE_PATH = "/api/v1/patient-access/exchange";
    private static final String PATIENT_ACCESS_PATH = "/api/v1/patient-access/";

    private final ResolvePatientAccessSessionUseCase resolveSession;

    public PatientSessionAuthenticationFilter(ResolvePatientAccessSessionUseCase resolveSession) {
        this.resolveSession = resolveSession;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = applicationPath(request);
        return !path.startsWith(PATIENT_ACCESS_PATH) || EXCHANGE_PATH.equals(path);
    }

    private String applicationPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty() ? requestUri : requestUri.substring(contextPath.length());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                var grant = resolveSession.resolve(new ResolvePatientAccessSessionCommand(
                        uniqueCookieValue(request, PatientAccessController.SESSION_COOKIE)));
                var principal = new PatientPreparationPrincipal(grant);
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority(PatientPreparationPrincipal.AUTHORITY)));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (PatientSessionUnavailableException exception) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private String uniqueCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        List<String> values = Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .toList();
        return values.size() == 1 ? values.getFirst() : null;
    }
}
