package backend.clinicalcontext.adapter.out.id;

import java.util.UUID;

import org.springframework.stereotype.Component;

import backend.clinicalcontext.application.port.out.ClinicalFactIdGenerator;
import backend.clinicalcontext.domain.ClinicalFactId;

@Component
public final class UuidClinicalFactIdGenerator implements ClinicalFactIdGenerator {

    @Override
    public ClinicalFactId nextId() {
        return new ClinicalFactId(UUID.randomUUID());
    }
}
