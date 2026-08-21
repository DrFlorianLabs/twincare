package backend.preparation.adapter.in.web;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backend.preparation.application.port.in.ExchangePatientInvitationCommand;
import backend.preparation.application.port.in.ExchangePatientInvitationUseCase;

@RestController
@RequestMapping("/api/v1/patient-access")
public class PatientAccessController {

    static final String SESSION_COOKIE = "tc_preparation_session";

    private final ExchangePatientInvitationUseCase exchangeInvitation;
    private final boolean secureCookie;

    public PatientAccessController(
            ExchangePatientInvitationUseCase exchangeInvitation,
            @Value("${app.preparation.patient-session-cookie-secure:true}") boolean secureCookie) {
        this.exchangeInvitation = exchangeInvitation;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/exchange")
    public ResponseEntity<ExchangePatientInvitationResponse> exchange(
            @RequestBody ExchangePatientInvitationRequest request) {
        var result = exchangeInvitation.exchange(new ExchangePatientInvitationCommand(request.token()));
        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE, result.sessionToken().value())
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/api/v1/patient-access")
                .maxAge(Duration.between(result.issuedAt(), result.expiresAt()))
                .build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .body(new ExchangePatientInvitationResponse("PATIENT_STARTED", result.expiresAt()));
    }
}
