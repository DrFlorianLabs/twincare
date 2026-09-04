package backend.clinicalcontext.domain;

public final class InvalidClinicalFactTransitionException extends RuntimeException {

    public InvalidClinicalFactTransitionException(String message) {
        super(message);
    }
}
