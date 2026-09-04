package backend.clinicalcontext.domain;

/**
 * Origin of a clinically relevant fact. Provenance describes where the fact
 * came from; it does not imply that the fact has been clinically reviewed.
 */
public enum ProvenanceType {
    PATIENT_DECLARED,
    DOCUMENT_EXTRACTED,
    DOCUMENT_VERIFIED,
    HUMAN_ENTERED,
    IMPORTED_CONTEXT,
    DERIVED_DETERMINISTIC,
    AI_PROPOSED
}
