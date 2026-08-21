package backend.preparation.adapter.in.web;

import java.io.IOException;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

@Component
public final class PatientSecurityResponses {

    private final boolean secureCookie;

    public PatientSecurityResponses(
            @Value("${app.preparation.patient-session-cookie-secure:true}") boolean secureCookie) {
        this.secureCookie = secureCookie;
    }

    public void unauthorized(HttpServletResponse response) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "Session patient indisponible", true);
    }

    public void forbidden(HttpServletResponse response) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Requête patient refusée", false);
    }

    public ResponseCookie expiredSessionCookie() {
        return ResponseCookie.from(PatientAccessController.SESSION_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/api/v1/patient-access")
                .maxAge(Duration.ZERO)
                .build();
    }

    private void write(
            HttpServletResponse response,
            HttpStatus status,
            String body,
            boolean clearSession) throws IOException {
        response.setStatus(status.value());
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        response.setHeader("Referrer-Policy", "no-referrer");
        if (clearSession) {
            response.addHeader(HttpHeaders.SET_COOKIE, expiredSessionCookie().toString());
        }
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.getWriter().write(body);
    }
}
