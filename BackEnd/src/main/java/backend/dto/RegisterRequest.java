package backend.dto;

import backend.entity.Role;
import backend.entity.Sexe;
import java.time.LocalDate;

import jakarta.validation.constraints.*;

public class RegisterRequest {
    @NotBlank
    @Size(max = 100)
    private String nom;
    @NotBlank
    @Size(max = 100)
    private String prenom;
    @NotNull
    @Past
    private LocalDate dateNaissance;
    @NotBlank
    @Email
    @Size(max = 150)
    private String email;
    @NotBlank
    @Size(min = 12, max = 128)
    private String motDePasse;
    @NotNull
    private Role role;
    @NotNull
    private Sexe sexe;

     public RegisterRequest() {
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMotDePasse() {
        return motDePasse;
    }

    public void setMotDePasse(String motDePasse) {
        this.motDePasse = motDePasse;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
    public LocalDate getDateNaissance() {
        return dateNaissance;
    }
    public void setDateNaissance(LocalDate dateNaissance) {
        this.dateNaissance = dateNaissance;
    }
    public Sexe getSexe() {
        return sexe;
    }   
    public void setSexe(Sexe sexe) {
        this.sexe = sexe;
    }
}
