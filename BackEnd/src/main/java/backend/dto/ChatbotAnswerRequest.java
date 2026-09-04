package backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ChatbotAnswerRequest {

    @NotNull
    private Integer questionId;

    @Size(max = 255)
    private String reponseTexte;

    private Integer reponseNumerique;

    public Integer getQuestionId() { return questionId; }
    public void setQuestionId(Integer questionId) { this.questionId = questionId; }

    public String getReponseTexte() { return reponseTexte; }
    public void setReponseTexte(String reponseTexte) { this.reponseTexte = reponseTexte; }

    public Integer getReponseNumerique() { return reponseNumerique; }
    public void setReponseNumerique(Integer reponseNumerique) { this.reponseNumerique = reponseNumerique; }
}
