package backend.clinicalcontext.domain;

/**
 * Certainty explicitly expressed by the source. NOT_ASSESSED must be used when
 * the collection flow did not ask for this information.
 */
public enum FactCertainty {
    NOT_ASSESSED,
    ASSERTED,
    EXPRESSED_AS_UNCERTAIN
}
