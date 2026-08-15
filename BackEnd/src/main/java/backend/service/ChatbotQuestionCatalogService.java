package backend.service;

import java.util.List;

import org.springframework.stereotype.Service;

import backend.dao.QuestionRepository;
import backend.entity.Question;
import backend.entity.QuestionDefinition;
import backend.exception.ResourceNotFoundException;

@Service
public class ChatbotQuestionCatalogService {

    private final QuestionRepository questionRepository;

    public ChatbotQuestionCatalogService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    public List<QuestionDefinition> getAllQuestions() {
        return questionRepository.findAllByOrderByPrioriteAsc().stream()
                .map(this::toDefinition)
                .toList();
    }

    public QuestionDefinition getQuestionById(Integer questionId) {
        if (questionId == null) {
            throw new ResourceNotFoundException("Question introuvable");
        }
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question introuvable"));
        return toDefinition(question);
    }

    private QuestionDefinition toDefinition(Question question) {
        return new QuestionDefinition(
                question.getId(),
                question.getCategorie(),
                question.getTexte(),
                question.getTypeReponse(),
                question.getDeclencheur(),
                question.getPriorite());
    }
}
