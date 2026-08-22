package backend.preparation.adapter.in.web;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backend.preparation.application.port.in.ExchangePatientInvitationCommand;
import backend.preparation.application.port.in.ExchangePatientInvitationUseCase;
import backend.preparation.application.port.in.EndPatientAccessSessionCommand;
import backend.preparation.application.port.in.EndPatientAccessSessionUseCase;
import backend.preparation.application.port.in.GetPatientReasonCommand;
import backend.preparation.application.port.in.GetPatientReasonUseCase;
import backend.preparation.application.port.in.RecordPatientReasonCommand;
import backend.preparation.application.port.in.RecordPatientReasonUseCase;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/patient-access")
public class PatientAccessController {

    static final String SESSION_COOKIE = "tc_preparation_session";

    private final ExchangePatientInvitationUseCase exchangeInvitation;
    private final EndPatientAccessSessionUseCase endSession;
    private final GetPatientReasonUseCase getPatientReason;
    private final RecordPatientReasonUseCase recordPatientReason;
    private final PatientSecurityResponses securityResponses;
    private final boolean secureCookie;

    public PatientAccessController(
            ExchangePatientInvitationUseCase exchangeInvitation,
            EndPatientAccessSessionUseCase endSession,
            GetPatientReasonUseCase getPatientReason,
            RecordPatientReasonUseCase recordPatientReason,
            PatientSecurityResponses securityResponses,
            @Value("${app.preparation.patient-session-cookie-secure:true}") boolean secureCookie) {
        this.exchangeInvitation = exchangeInvitation;
        this.endSession = endSession;
        this.getPatientReason = getPatientReason;
        this.recordPatientReason = recordPatientReason;
        this.securityResponses = securityResponses;
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

    @GetMapping("/session")
    public ResponseEntity<PatientSessionResponse> session(
            @AuthenticationPrincipal PatientPreparationPrincipal principal,
            CsrfToken csrfToken) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .header(PatientCsrfTokenRepository.HEADER_NAME, csrfToken.getToken())
                .body(new PatientSessionResponse("ACTIVE", principal.grant().expiresAt()));
    }

    @GetMapping("/reason")
    public ResponseEntity<PatientReasonResponse> getReason(
            @AuthenticationPrincipal PatientPreparationPrincipal principal) {
        var result = getPatientReason.get(new GetPatientReasonCommand(principal.grant()));
        PatientReasonResponse response = result.reason()
                .map(reason -> PatientReasonResponse.present(reason.text(), reason.recordedAt()))
                .orElseGet(PatientReasonResponse::absent);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .body(response);
    }

    @PutMapping("/reason")
    public ResponseEntity<Void> recordReason(
            @AuthenticationPrincipal PatientPreparationPrincipal principal,
            @Valid @RequestBody PatientReasonRequest request) {
        recordPatientReason.record(new RecordPatientReasonCommand(principal.grant(), request.reason()));
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .build();
    }

    @DeleteMapping("/session")
    public ResponseEntity<Void> endSession(@AuthenticationPrincipal PatientPreparationPrincipal principal) {
        endSession.end(new EndPatientAccessSessionCommand(principal.grant()));
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, securityResponses.expiredSessionCookie().toString())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .build();
    }
}
