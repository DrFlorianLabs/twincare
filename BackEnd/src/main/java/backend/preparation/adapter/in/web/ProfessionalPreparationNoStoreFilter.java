package backend.preparation.adapter.in.web;

import java.io.IOException;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public final class ProfessionalPreparationNoStoreFilter extends OncePerRequestFilter {

    private static final Pattern PROFESSIONAL_PREPARATION_PATH = Pattern.compile(
            "^(?:/api/v1/professional-context|/api/v1/organizations/[^/]+/preparation-cases(?:/.*)?)$");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PROFESSIONAL_PREPARATION_PATH.matcher(request.getRequestURI()).matches();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        filterChain.doFilter(request, response);
    }
}
