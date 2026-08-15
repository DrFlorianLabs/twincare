package backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dao.AppUtilisateurRepository;
import backend.dao.DossierPatientRepository;
import backend.dto.AuthResponse;
import backend.dto.LoginRequest;
import backend.dto.RegisterRequest;
import backend.dto.ResetPasswordRequest;
import backend.entity.AppUtilisateur;
import backend.entity.DossierPatient;
import backend.entity.Role;
import backend.exception.BadRequestException;
import backend.security.JwtService;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppUtilisateurRepository repo;
    private final DossierPatientRepository dossierRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public AuthService(
            AppUtilisateurRepository repo,
            DossierPatientRepository dossierRepo,
            PasswordEncoder encoder,
            JwtService jwtService,
            CustomUserDetailsService userDetailsService) {
        this.repo = repo;
        this.dossierRepo = dossierRepo;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Current prototype provisioning endpoint. It is intentionally limited to
     * patient accounts and is protected at the HTTP security layer.
     * Staff/admin provisioning must use a separate privileged workflow later.
     */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (req.getEmail() == null || req.getEmail().isBlank()) {
            throw new BadRequestException("Email obligatoire");
        }
        if (req.getMotDePasse() == null || req.getMotDePasse().isBlank()) {
            throw new BadRequestException("Mot de passe obligatoire");
        }
        if (req.getRole() != Role.PATIENT) {
            throw new BadRequestException("Seule la création d'un compte patient est autorisée par cet endpoint");
        }
        if (repo.findByEmail(req.getEmail().trim()).isPresent()) {
            throw new BadRequestException("Compte déjà existant");
        }

        AppUtilisateur user = new AppUtilisateur(
                req.getNom(),
                req.getPrenom(),
                req.getDateNaissance(),
                req.getEmail().trim(),
                encoder.encode(req.getMotDePasse()),
                Role.PATIENT,
                req.getSexe());

        AppUtilisateur saved = repo.save(user);

        DossierPatient dossier = new DossierPatient();
        dossier.setPatient(saved);
        DossierPatient savedDossier = dossierRepo.save(dossier);
        saved.setDossierPatient(savedDossier);

        var userDetails = userDetailsService.loadUserByUsername(saved.getEmail());
        String token = jwtService.generateToken(userDetails);

        log.info("Patient account provisioned");
        return new AuthResponse(
                token,
                saved.getEmail(),
                saved.getRole().name(),
                saved.getId(),
                savedDossier.getId(),
                saved.getPrenom(),
                saved.getNom());
    }

    public AuthResponse login(LoginRequest req) {
        if (req.getEmail() == null || req.getMotDePasse() == null) {
            throw new BadRequestException("Identifiants invalides");
        }

        AppUtilisateur user = repo.findByEmail(req.getEmail().trim())
                .orElseThrow(() -> new BadRequestException("Identifiants invalides"));

        if (!encoder.matches(req.getMotDePasse(), user.getMotDePasse())) {
            throw new BadRequestException("Identifiants invalides");
        }

        var userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtService.generateToken(userDetails);

        Long dossierId = null;
        if (user.getRole() == Role.PATIENT) {
            DossierPatient dossier = dossierRepo.findByPatientId(user.getId()).orElse(null);
            if (dossier != null) {
                dossierId = dossier.getId();
            }
        }

        log.info("Authentication succeeded");
        return new AuthResponse(
                token,
                user.getEmail(),
                user.getRole().name(),
                user.getId(),
                dossierId,
                user.getPrenom(),
                user.getNom());
    }

    /**
     * Administrative reset only. HTTP authorization currently restricts this
     * endpoint to ADMIN until a secure patient recovery flow is designed.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        if (req.getEmail() == null || req.getEmail().isBlank()
                || req.getNouveauMotDePasse() == null || req.getNouveauMotDePasse().isBlank()) {
            throw new BadRequestException("Données de réinitialisation invalides");
        }

        AppUtilisateur user = repo.findByEmail(req.getEmail().trim())
                .orElseThrow(() -> new BadRequestException("Compte introuvable"));

        user.setMotDePasse(encoder.encode(req.getNouveauMotDePasse()));
        repo.save(user);
        log.info("Administrative password reset completed");
    }
}
