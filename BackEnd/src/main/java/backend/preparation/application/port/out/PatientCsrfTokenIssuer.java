package backend.preparation.application.port.out;

import backend.preparation.application.port.in.PatientAccessGrant;
import backend.preparation.domain.SecureToken;

public interface PatientCsrfTokenIssuer {

    SecureToken issueFor(PatientAccessGrant grant);
}
