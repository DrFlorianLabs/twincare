package backend.preparation.application.port.out;

import backend.preparation.domain.SecureToken;

public interface SecureTokenGenerator {

    SecureToken nextToken();
}
