package backend.preparation.application.port.out;

import backend.preparation.domain.SecureToken;
import backend.preparation.domain.TokenDigest;

public interface SecureTokenDigester {

    TokenDigest digestInvitation(SecureToken token);

    TokenDigest digestSession(SecureToken token);
}
