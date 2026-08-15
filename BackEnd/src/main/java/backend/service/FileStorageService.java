package backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import backend.exception.BadRequestException;
import backend.exception.ResourceNotFoundException;

@Service
public class FileStorageService {

    private static final Map<String, String> CONTENT_TYPE_EXTENSIONS = Map.of(
            "application/pdf", ".pdf",
            "image/jpeg", ".jpg",
            "image/png", ".png");

    private static final Pattern STORAGE_KEY_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(pdf|jpg|png)$");

    private final Path uploadPath;
    private final long maxFileSizeBytes;

    public FileStorageService(
            @Value("${file.upload-dir}") String uploadDir,
            @Value("${file.max-size-bytes}") long maxFileSizeBytes) {
        this.uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public String storeFile(MultipartFile file) {
        validateUpload(file);

        String contentType = normalizeContentType(file.getContentType());
        String storageKey = UUID.randomUUID() + CONTENT_TYPE_EXTENSIONS.get(contentType);
        Path target = resolveStoragePath(storageKey);

        try {
            Files.createDirectories(uploadPath);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target);
            }
            return storageKey;
        } catch (IOException e) {
            throw new IllegalStateException("Impossible d'enregistrer le document", e);
        }
    }

    public Resource loadFile(String storageKey) {
        Path path = resolveStoragePath(storageKey);

        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
                throw new ResourceNotFoundException("Document introuvable");
            }

            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("Document introuvable");
            }
            return resource;
        } catch (IOException e) {
            throw new ResourceNotFoundException("Document introuvable");
        }
    }

    public String contentTypeFor(String storageKey) {
        validateStorageKey(storageKey);
        if (storageKey.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (storageKey.endsWith(".png")) {
            return "image/png";
        }
        return "image/jpeg";
    }

    public void deleteFile(String storageKey) {
        Path path = resolveStoragePath(storageKey);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de supprimer le document stocké", e);
        }
    }

    public String sanitizeDisplayFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "document";
        }

        String normalized = originalFilename.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String baseName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        baseName = baseName.replaceAll("[\\p{Cntrl}]", "").trim();

        if (baseName.isBlank()) {
            return "document";
        }
        return baseName.length() > 255 ? baseName.substring(baseName.length() - 255) : baseName;
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Aucun fichier reçu");
        }
        if (file.getSize() <= 0 || file.getSize() > maxFileSizeBytes) {
            throw new BadRequestException("Taille de fichier non autorisée");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (!CONTENT_TYPE_EXTENSIONS.containsKey(contentType)) {
            throw new BadRequestException("Type de fichier non autorisé");
        }

        try {
            verifySignature(file, contentType);
        } catch (IOException e) {
            throw new BadRequestException("Impossible de vérifier le fichier");
        }
    }

    private void verifySignature(MultipartFile file, String contentType) throws IOException {
        byte[] header;
        try (InputStream inputStream = file.getInputStream()) {
            header = inputStream.readNBytes(8);
        }

        boolean valid = switch (contentType) {
            case "application/pdf" -> startsWith(header, new byte[] { '%', 'P', 'D', 'F', '-' });
            case "image/jpeg" -> header.length >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF;
            case "image/png" -> startsWith(header, new byte[] {
                    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A });
            default -> false;
        };

        if (!valid) {
            throw new BadRequestException("Le contenu du fichier ne correspond pas au type annoncé");
        }
    }

    private boolean startsWith(byte[] source, byte[] prefix) {
        if (source.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (source[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int separator = contentType.indexOf(';');
        String baseType = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return baseType.trim().toLowerCase(Locale.ROOT);
    }

    private Path resolveStoragePath(String storageKey) {
        validateStorageKey(storageKey);
        Path resolved = uploadPath.resolve(storageKey).normalize();
        if (!resolved.getParent().equals(uploadPath)) {
            throw new BadRequestException("Référence de stockage invalide");
        }
        return resolved;
    }

    private void validateStorageKey(String storageKey) {
        if (storageKey == null || !STORAGE_KEY_PATTERN.matcher(storageKey).matches()) {
            throw new BadRequestException("Référence de stockage invalide");
        }
    }
}
