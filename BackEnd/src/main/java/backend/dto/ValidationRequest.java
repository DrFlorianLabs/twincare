package backend.dto;

import backend.entity.DecisionValidation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ValidationRequest {

    @NotNull
    private DecisionValidation decision;

    @Size(max = 1000)
    private String commentaire;

    /**
     * Deprecated compatibility field for the historical frontend. The backend
     * deliberately ignores it and derives the validation actor exclusively
     * from the authenticated security context.
     */
    @Deprecated
    private Long assistantMedicalId;

    public DecisionValidation getDecision() {
        return decision;
    }

    public void setDecision(DecisionValidation decision) {
        this.decision = decision;
    }

    public String getCommentaire() {
        return commentaire;
    }

    public void setCommentaire(String commentaire) {
        this.commentaire = commentaire;
    }

    @Deprecated
    public Long getAssistantMedicalId() {
        return assistantMedicalId;
    }

    @Deprecated
    public void setAssistantMedicalId(Long assistantMedicalId) {
        this.assistantMedicalId = assistantMedicalId;
    }
}
