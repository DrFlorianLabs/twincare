package backend.preparation.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import backend.preparation.application.port.out.AttachmentIdGenerator;
import backend.preparation.domain.AttachmentId;

@Component
public final class UuidAttachmentIdGenerator implements AttachmentIdGenerator {

    @Override
    public AttachmentId nextId() {
        return new AttachmentId(UUID.randomUUID());
    }
}
