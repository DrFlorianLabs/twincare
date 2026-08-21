package backend.preparation.application.port.out;

import backend.preparation.domain.AttachmentId;

public interface AttachmentIdGenerator {

    AttachmentId nextId();
}
