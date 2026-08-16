package backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
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
    private final Path stagedDeletionPath;
    private final long maxFileSizeBytes;

    public FileStorageService(
            @Value("${file.upload-dir}") String uploadDir,
            @Value("${file.max-size-bytes}") long maxFileSizeBytes) {
        this.uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.stagedDeletionPath = this.uploadPath.resolve(".pending-deletions");
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public String storeFile(MultipartFile file) {
        validateUpload(file);

        String contentType = normalizeContentType(file.getContentType());
        String storageKey = UUID.randomUUID() + CONTENT_TYPE_EXTENSIONS.get(contentType);
        Path target = resolveStoragePath(storageKey);

        try {
            ensureDirectory(uploadPath);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target);
            }
            return storageKey;
        } catch (IOException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
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

    public StagedFileDeletion stageDeletion(String storageKey) {
        Path source = resolveStoragePath(storageKey);
        Path staged = resolveStagedDeletionPath(storageKey);

        try {
            ensureDirectory(uploadPath);
            ensureDirectory(stagedDeletionPath);

            if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
                return new StagedFileDeletion(storageKey, false);
            }
            if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) {
                throw new IllegalStateException("Le document stocké n'est pas un fichier régulier");
            }

            move(source, staged);
            return new StagedFileDeletion(storageKey, true);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de préparer la suppression du document", e);
        }
    }

    public void restoreStagedDeletion(StagedFileDeletion stagedDeletion) {
        if (stagedDeletion == null || !stagedDeletion.existed()) {
            return;
        }

        Path staged = resolveStagedDeletionPath(stagedDeletion.storageKey());
        Path target = resolveStoragePath(stagedDeletion.storageKey());
        try {
            if (!Files.exists(staged, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            ensureDirectory(uploadPath);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Impossible de restaurer le document stocké");
            }
            move(staged, target);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de restaurer le document stocké", e);
        }
    }

    public void finalizeStagedDeletion(StagedFileDeletion stagedDeletion) {
        if (stagedDeletion == null || !stagedDeletion.existed()) {
            return;
        }

        Path staged = resolveStagedDeletionPath(stagedDeletion.storageKey());
        try {
            Files.deleteIfExists(staged);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de finaliser la suppression du document", e);
        }
    }

    public List<String> listStagedDeletionKeys() {
        if (!Files.isDirectory(stagedDeletionPath, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(stagedDeletionPath)) {
            return List.of();
        }

        try (var files = Files.list(stagedDeletionPath)) {
            return files
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .map(path -> path.getFileName().toString())
                    .filter(this::isValidStorageKey)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de parcourir les suppressions en attente", e);
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

    private Path resolveStagedDeletionPath(String storageKey) {
        validateStorageKey(storageKey);
        Path resolved = stagedDeletionPath.resolve(storageKey).normalize();
        if (!resolved.getParent().equals(stagedDeletionPath)) {
            throw new BadRequestException("Référence de stockage invalide");
        }
        return resolved;
    }

    private void ensureDirectory(Path directory) throws IOException {
        Files.createDirectories(directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) {
            throw new IOException("Répertoire de stockage invalide");
        }
    }

    private void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private void validateStorageKey(String storageKey) {
        if (!isValidStorageKey(storageKey)) {
            throw new BadRequestException("Référence de stockage invalide");
        }
    }

    private boolean isValidStorageKey(String storageKey) {
        return storageKey != null && STORAGE_KEY_PATTERN.matcher(storageKey).matches();
    }

    public record StagedFileDeletion(String storageKey, boolean existed) {
    }
}
