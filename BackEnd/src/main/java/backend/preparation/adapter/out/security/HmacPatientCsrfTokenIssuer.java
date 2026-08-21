package backend.preparation.adapter.out.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import backend.preparation.application.port.in.PatientAccessGrant;
import backend.preparation.application.port.out.PatientCsrfTokenIssuer;
import backend.preparation.domain.SecureToken;

@Component
public final class HmacPatientCsrfTokenIssuer implements PatientCsrfTokenIssuer {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_PEPPER_BYTES = 32;
    private static final String DOMAIN = "patient-access-csrf:v1:";

    private final byte[] pepper;

    public HmacPatientCsrfTokenIssuer(@Value("${app.preparation.token-pepper}") String pepper) {
        if (pepper == null || pepper.getBytes(StandardCharsets.UTF_8).length < MINIMUM_PEPPER_BYTES) {
            throw new IllegalArgumentException("Patient CSRF token pepper must contain at least 32 bytes");
        }
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public SecureToken issueFor(PatientAccessGrant grant) {
        String value = DOMAIN
                + grant.sessionId() + ':'
                + grant.organizationId().value() + ':'
                + grant.preparationCaseId().value() + ':'
                + grant.expiresAt();
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(pepper, ALGORITHM));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return new SecureToken(Base64.getUrlEncoder().withoutPadding().encodeToString(digest));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Required patient CSRF cryptography is unavailable", exception);
        }
    }
}
