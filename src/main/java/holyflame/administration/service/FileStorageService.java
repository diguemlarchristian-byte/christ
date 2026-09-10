package holyflame.administration.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${app.upload.dir:./holyflame_uploads}")
    private String uploadDir;

    public String store(MultipartFile file, String subDir) throws IOException {
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path dir = Paths.get(uploadDir, subDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);

        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (ext != null && !ext.matches("[a-zA-Z0-9]{1,10}")) ext = null;
        String stored = UUID.randomUUID().toString() + (ext != null ? "." + ext.toLowerCase() : "");
        Path target = dir.resolve(stored).normalize();
        if (!target.startsWith(base)) {
            throw new IOException("Nom de fichier invalide.");
        }
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return subDir + "/" + stored;
    }

    /**
     * Range un contenu produit par l'application elle-meme — un PDF genere, pas un fichier
     * televerse. Meme arborescence et memes garde-fous que {@link #store}, mais sans passer
     * par un MultipartFile qui n'aurait ici aucun sens.
     */
    public String storeBytes(byte[] contenu, String extension, String subDir) throws IOException {
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path dir = Paths.get(uploadDir, subDir).toAbsolutePath().normalize();
        if (!dir.startsWith(base)) {
            throw new IOException("Sous-dossier invalide.");
        }
        Files.createDirectories(dir);

        String ext = extension != null && extension.matches("[a-zA-Z0-9]{1,10}")
            ? "." + extension.toLowerCase() : "";
        String stored = UUID.randomUUID() + ext;
        Path target = dir.resolve(stored).normalize();
        if (!target.startsWith(base)) {
            throw new IOException("Nom de fichier invalide.");
        }
        Files.write(target, contenu);
        return subDir + "/" + stored;
    }

    public Resource loadAsResource(String relativePath) throws IOException {
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = base.resolve(relativePath).normalize();
        if (!target.startsWith(base)) {
            throw new IOException("Accès refusé : chemin invalide");
        }
        Resource resource = new UrlResource(target.toUri());
        if (resource.exists() && resource.isReadable()) return resource;
        throw new IOException("Fichier introuvable : " + relativePath);
    }

    public void delete(String relativePath) {
        try {
            Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path target = base.resolve(relativePath).normalize();
            if (target.startsWith(base)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException ignored) {}
    }
}
