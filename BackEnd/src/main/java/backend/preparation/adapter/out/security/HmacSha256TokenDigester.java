package backend.preparation.adapter.out.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import backend.preparation.application.port.out.SecureTokenDigester;
import backend.preparation.domain.SecureToken;
import backend.preparation.domain.TokenDigest;

@Component
public class HmacSha256TokenDigester implements SecureTokenDigester {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_PEPPER_BYTES = 32;

    private final byte[] pepper;
    private final String keyId;

    public HmacSha256TokenDigester(
            @Value("${app.preparation.token-pepper}") String pepper,
            @Value("${app.preparation.token-key-id:v1}") String keyId) {
        if (pepper == null || pepper.getBytes(StandardCharsets.UTF_8).length < MINIMUM_PEPPER_BYTES) {
            throw new IllegalArgumentException("Patient token pepper must contain at least 32 bytes");
        }
        if (keyId == null || keyId.isBlank() || keyId.length() > 64
                || keyId.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Patient token key identifier has an invalid format");
        }
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
        this.keyId = keyId.trim();
    }

    @Override
    public TokenDigest digestInvitation(SecureToken token) {
        return digest("patient-invitation:", token);
    }

    @Override
    public TokenDigest digestSession(SecureToken token) {
        return digest("patient-access-session:", token);
    }

    private TokenDigest digest(String domain, SecureToken token) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(pepper, ALGORITHM));
            byte[] digest = mac.doFinal((domain + token.value()).getBytes(StandardCharsets.UTF_8));
            return new TokenDigest(keyId, HexFormat.of().formatHex(digest));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Required patient token cryptography is unavailable", exception);
        }
    }
}
