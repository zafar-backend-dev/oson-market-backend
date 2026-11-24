package uz.osonmarket.profile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import uz.osonmarket.auth.api.AuthException;

@Component
public class AvatarImages {
    private static final Set<String> AVATAR_FORMATS = Set.of("JPEG", "PNG", "GIF", "BMP", "TIFF", "TIF", "WBMP");
    private static final Set<String> CATALOG_FORMATS = Set.of("JPEG", "PNG");

    private final long maxBytes;
    private final int maxDimension;

    AvatarImages(@Value("${app.profile.avatar-max-bytes}") long maxBytes,
                 @Value("${app.profile.avatar-max-dimension}") int maxDimension) {
        this.maxBytes = maxBytes;
        this.maxDimension = maxDimension;
    }

    public Image read(MultipartFile file) {
        return read(file, CATALOG_FORMATS);
    }

    public Image readAvatar(MultipartFile file) {
        return read(file, AVATAR_FORMATS);
    }

    private Image read(MultipartFile file, Set<String> formats) {
        if (file.getSize() > maxBytes) {
            throw new AuthException(HttpStatus.PAYLOAD_TOO_LARGE, "profile.avatar.size");
        }
        try (var data = new MemoryCacheImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            var readers = ImageIO.getImageReaders(data);
            if (!readers.hasNext()) {
                throw invalid();
            }
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!formats.contains(format.toUpperCase(Locale.ROOT))) {
                    throw invalid();
                }
                reader.setInput(data, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > maxDimension || height > maxDimension) {
                    throw new AuthException(HttpStatus.BAD_REQUEST, "profile.avatar.dimensions");
                }
                var output = new ByteArrayOutputStream();
                ImageIO.write(reader.read(0), "PNG", output);
                return new Image(output.toByteArray(), width, height);
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw invalid();
        }
    }

    private AuthException invalid() {
        return new AuthException(HttpStatus.BAD_REQUEST, "profile.avatar.invalid");
    }

    public record Image(byte[] content, int width, int height) {}
}
