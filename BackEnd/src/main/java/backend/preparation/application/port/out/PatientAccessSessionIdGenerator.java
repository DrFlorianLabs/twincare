package backend.preparation.application.port.out;

import java.util.UUID;

public interface PatientAccessSessionIdGenerator {

    UUID nextId();
}
