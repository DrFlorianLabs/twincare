package backend.preparation.adapter.out.id;

import java.util.UUID;

import org.springframework.stereotype.Component;

import backend.preparation.application.port.out.PatientInvitationIdGenerator;
import backend.preparation.domain.PatientInvitationId;

@Component
public class UuidPatientInvitationIdGenerator implements PatientInvitationIdGenerator {

    @Override
    public PatientInvitationId nextId() {
        return new PatientInvitationId(UUID.randomUUID());
    }
}
