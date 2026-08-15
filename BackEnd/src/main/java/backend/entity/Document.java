package backend.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "document")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TypeDocument type;

    @Column(nullable = false)
    private LocalDate dateDocument;

    @Column(nullable = false)
    private LocalDateTime dateDepot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatutDocument statut;

    @Column(nullable = false, length = 255)
    private String nomFichier;

    @JsonIgnore
    @Column(nullable = false, length = 500)
    private String cheminFichier;

    @Column(length = 255)
    private String prescripteur;

    @Column(length = 1000)
    private String commentairePatient;

    @ManyToOne
    @JoinColumn(name = "dossier_patient_id", nullable = false)
    @JsonIgnore
    private DossierPatient dossierPatient;

    @OneToOne(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Validation validation;

    public Document() {
        this.dateDepot = LocalDateTime.now();
        this.statut = StatutDocument.EN_ATTENTE;
    }

    public Long getId() {
        return id;
    }

    public TypeDocument getType() {
        return type;
    }

    public void setType(TypeDocument type) {
        this.type = type;
    }

    public LocalDate getDateDocument() {
        return dateDocument;
    }

    public void setDateDocument(LocalDate dateDocument) {
        this.dateDocument = dateDocument;
    }

    public LocalDateTime getDateDepot() {
        return dateDepot;
    }

    public void setDateDepot(LocalDateTime dateDepot) {
        this.dateDepot = dateDepot;
    }

    public StatutDocument getStatut() {
        return statut;
    }

    public void setStatut(StatutDocument statut) {
        this.statut = statut;
    }

    public String getNomFichier() {
        return nomFichier;
    }

    public void setNomFichier(String nomFichier) {
        this.nomFichier = nomFichier;
    }

    public String getCheminFichier() {
        return cheminFichier;
    }

    public void setCheminFichier(String cheminFichier) {
        this.cheminFichier = cheminFichier;
    }

    public String getPrescripteur() {
        return prescripteur;
    }

    public void setPrescripteur(String prescripteur) {
        this.prescripteur = prescripteur;
    }

    public String getCommentairePatient() {
        return commentairePatient;
    }

    public void setCommentairePatient(String commentairePatient) {
        this.commentairePatient = commentairePatient;
    }

    public DossierPatient getDossierPatient() {
        return dossierPatient;
    }

    public void setDossierPatient(DossierPatient dossierPatient) {
        this.dossierPatient = dossierPatient;
    }

    public Validation getValidation() {
        return validation;
    }

    public void setValidation(Validation validation) {
        this.validation = validation;
    }
}
