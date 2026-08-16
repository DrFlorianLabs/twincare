package backend.dao;

import backend.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByDossierPatientId(Long dossierId);

    boolean existsByCheminFichier(String cheminFichier);
}
