package uz.osonmarket.chat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

record ChatMedia(String type, String name, byte[] bytes) {
    static ChatMedia read(MultipartFile file, String kind) throws IOException {
        int limit = switch (kind) {
            case "IMAGE" -> 20 * 1024 * 1024;
            case "VOICE" -> 10 * 1024 * 1024;
            default -> throw unsupported();
        };
        if (file.getSize() > limit) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        if (file.isEmpty()) throw unsupported();
        byte[] bytes = file.getBytes();
        String type = "IMAGE".equals(kind) ? imageType(bytes) : voiceType(bytes);
        if (type == null) throw unsupported();

        String original = file.getOriginalFilename();
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (name.length() > 160) name = name.substring(0, 160);
        return new ChatMedia(type, name.isBlank() ? kind.toLowerCase(Locale.ROOT) : name, bytes);
    }

    private static String imageType(byte[] bytes) throws IOException {
        if (matches(bytes, 0, "RIFF") && matches(bytes, 8, "WEBP")
                && (matches(bytes, 12, "VP8 ") || matches(bytes, 12, "VP8L") || matches(bytes, 12, "VP8X"))) {
            return "image/webp";
        }
        if (matches(bytes, 4, "ftyp")) {
            String brand = new String(bytes, 8, 4, StandardCharsets.US_ASCII);
            return switch (brand) {
                case "avif", "avis" -> "image/avif";
                case "heic", "heix", "hevc", "hevx" -> "image/heic";
                case "mif1", "msf1" -> "image/heif";
                default -> null;
            };
        }
        if (matches(bytes, 0, "\u0000\u0000\u0001\u0000")) return "image/x-icon";
        if (matches(bytes, 0, "8BPS")) return "image/vnd.adobe.photoshop";
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (reader.getWidth(0) < 1 || reader.getHeight(0) < 1
                        || (long) reader.getWidth(0) * reader.getHeight(0) > 100_000_000L) return null;
                return switch (reader.getFormatName().toUpperCase(Locale.ROOT)) {
                    case "JPEG" -> "image/jpeg";
                    case "PNG" -> "image/png";
                    case "GIF" -> "image/gif";
                    case "BMP" -> "image/bmp";
                    case "TIFF", "TIF" -> "image/tiff";
                    case "WBMP" -> "image/vnd.wap.wbmp";
                    default -> null;
                };
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private static String voiceType(byte[] bytes) {
        if (matches(bytes, 0, "RIFF") && matches(bytes, 8, "WAVE")) return "audio/wav";
        if (matches(bytes, 0, "fLaC")) return "audio/flac";
        if (matches(bytes, 0, "OggS") && (matches(bytes, 28, "OpusHead") || matches(bytes, 28, "\u0001vorbis"))) {
            return "audio/ogg";
        }
        if (matches(bytes, 4, "ftyp") && matches(bytes, 8, "M4A ")) return "audio/mp4";
        if (matches(bytes, 0, "#!AMR\n") || matches(bytes, 0, "#!AMR-WB\n")) return "audio/amr";
        if (matches(bytes, 0, "ID3") || (bytes.length >= 4 && (bytes[0] & 255) == 255
                && (bytes[1] & 230) == 226)) return "audio/mpeg";
        if (bytes.length >= 4 && (bytes[0] & 255) == 255 && (bytes[1] & 246) == 240) return "audio/aac";
        if (matches(bytes, 0, "\u001aE\u00df\u00a3")) {
            String header = new String(bytes, 0, Math.min(bytes.length, 4096), StandardCharsets.ISO_8859_1);
            if (header.contains("webm") && (header.contains("A_OPUS") || header.contains("A_VORBIS"))
                    && !header.contains("V_VP") && !header.contains("V_AV1")) return "audio/webm";
        }
        return null;
    }

    private static boolean matches(byte[] bytes, int offset, String value) {
        if (bytes.length < Math.max(offset + value.length(), offset == 4 ? 12 : 0)) return false;
        for (int i = 0; i < value.length(); i++) {
            if ((bytes[offset + i] & 255) != value.charAt(i)) return false;
        }
        return true;
    }

    private static ResponseStatusException unsupported() {
        return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported chat attachment");
    }
}
