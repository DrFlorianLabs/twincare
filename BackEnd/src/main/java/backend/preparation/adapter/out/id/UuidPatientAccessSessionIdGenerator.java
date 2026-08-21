package backend.preparation.adapter.out.id;

import java.util.UUID;

import org.springframework.stereotype.Component;

import backend.preparation.application.port.out.PatientAccessSessionIdGenerator;

@Component
public class UuidPatientAccessSessionIdGenerator implements PatientAccessSessionIdGenerator {

    @Override
    public UUID nextId() {
        return UUID.randomUUID();
    }
}
