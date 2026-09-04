package backend.rest;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backend.dto.PatientProvisioningResponse;
import backend.dto.RegisterRequest;
import backend.dto.ResetPasswordRequest;
import backend.service.AuthService;
import jakarta.validation.Valid;

@Profile("legacy-prototype")
@RestController
@RequestMapping("/api/auth")
public class LegacyAccountAdministrationController {

    private final AuthService authService;

    public LegacyAccountAdministrationController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public PatientProvisioningResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Mot de passe mis à jour avec succès"));
    }
}
