package in.trendsfit.service;

import in.trendsfit.config.AppConfig;
import in.trendsfit.web.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Image uploads. Type is decided from the file's magic bytes (not the client-supplied name or content type). */
@Service
public class UploadService {
    private static final long MAX_BYTES = 3L * 1024 * 1024;
    private final AppConfig cfg;

    public UploadService(AppConfig cfg) { this.cfg = cfg; }

    /** @return public URL path such as /uploads/products/ab12.jpg */
    public String saveImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("NO_FILE", "Choose an image to upload.");
        if (file.getSize() > MAX_BYTES) throw ApiException.badRequest("TOO_LARGE", "Images must be 3 MB or smaller.");
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(16);
            String ext = detect(head);
            if (ext == null) throw ApiException.badRequest("BAD_TYPE", "Only JPG, PNG or WebP images are allowed.");
            Path dir = Path.of(cfg.uploadDir, folder).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String name = Tokens.random(12).replace('-', 'a').replace('_', 'b') + "." + ext;
            Path target = dir.resolve(name).normalize();
            if (!target.startsWith(dir)) throw ApiException.badRequest("BAD_PATH", "Invalid file name.");
            try (InputStream full = file.getInputStream()) {
                Files.copy(full, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return "/uploads/" + folder + "/" + name;
        } catch (IOException e) {
            throw ApiException.badRequest("UPLOAD_FAILED", "Could not save the image. Please try again.");
        }
    }

    static String detect(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return "jpg";
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return "png";
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return "webp";
        return null;
    }
}
