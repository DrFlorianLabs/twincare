package backend.preparation.adapter.out.security;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

import backend.preparation.application.port.out.SecureTokenGenerator;
import backend.preparation.domain.SecureToken;

@Component
public class SecureRandomTokenGenerator implements SecureTokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom;

    public SecureRandomTokenGenerator() {
        this(new SecureRandom());
    }

    SecureRandomTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    @Override
    public SecureToken nextToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return new SecureToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }
}
