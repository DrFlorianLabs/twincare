package backend.dto;

import org.springframework.core.io.Resource;

public record DocumentContent(
        Resource resource,
        String contentType,
        String originalFilename) {
}
