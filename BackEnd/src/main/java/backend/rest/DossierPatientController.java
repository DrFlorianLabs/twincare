package backend.rest;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backend.entity.DossierPatient;
import backend.service.DossierPatientService;

@Profile("legacy-prototype")
@RestController
@RequestMapping("/api/dossiers")
public class DossierPatientController {

    private final DossierPatientService dossierPatientService;

    public DossierPatientController(DossierPatientService dossierPatientService) {
        this.dossierPatientService = dossierPatientService;
    }

    @GetMapping("/patient/{patientId}")
    public DossierPatient getByPatientId(@PathVariable Long patientId) {
        return dossierPatientService.getByPatientId(patientId);
    }

    @GetMapping("/{dossierId}")
    public DossierPatient getById(@PathVariable Long dossierId) {
        return dossierPatientService.getById(dossierId);
    }

    @GetMapping
    public List<DossierPatient> getAll() {
        return dossierPatientService.getAll();
    }
}
