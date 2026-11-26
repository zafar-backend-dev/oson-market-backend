package uz.osonmarket.chat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
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

        String original = file.getOriginalFilename();
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (type == null && "IMAGE".equals(kind)) {
            String contentType = file.getContentType();
            boolean declaredImage = contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/");
            boolean imageExtension = name.toLowerCase(Locale.ROOT).matches(
                    ".*\\.(?:png|jpe?g|jpe|jfif|gif|webp|bmp|dib|tiff?|heic|heif|avif|svgz?|ico|psd|psb|eps|epsf|pcx|dcx|jxl|jp2|j2k|dng|cr2|cr3|nef|arw|raf|rw2|orf|raw|sr2|pef|x3f|tga|dds|exr|hdr|pnm|pbm|pgm|ppm|qoi|xcf|wmf|emf)");
            if (declaredImage || imageExtension) type = "image/x-chat-original";
        }
        if (type == null) throw unsupported();
        if (name.length() > 160) name = name.substring(0, 160);
        return new ChatMedia(type, name.isBlank() ? kind.toLowerCase(Locale.ROOT) : name, bytes);
    }

    private static String imageType(byte[] bytes) {
        if (matches(bytes, 0, "\u00ff\u00d8\u00ff")) return "image/jpeg";
        if (matches(bytes, 0, "\u0089PNG\r\n\u001a\n")) return "image/png";
        if (matches(bytes, 0, "GIF87a") || matches(bytes, 0, "GIF89a")) return "image/gif";
        if (bytes.length >= 26 && matches(bytes, 0, "BM")) return "image/bmp";
        if (matches(bytes, 0, "II*\u0000") || matches(bytes, 0, "MM\u0000*")
                || matches(bytes, 0, "II+\u0000") || matches(bytes, 0, "MM\u0000+")) return "image/tiff";
        if (matches(bytes, 0, "FUJIFILMCCD-RAW") || matches(bytes, 0, "IIU\u0000")
                || matches(bytes, 0, "IIRO") || matches(bytes, 0, "MMOR")) return "image/x-chat-original";
        if (matches(bytes, 0, "\u00ff\n") || matches(bytes, 0, "\u0000\u0000\u0000\fJXL \r\n\u0087\n")) return "image/jxl";
        if (matches(bytes, 0, "\u0000\u0000\u0000\u000cjP  \r\n\u0087\n")) return "image/jp2";
        if (matches(bytes, 0, "RIFF") && matches(bytes, 8, "WEBP")
                && (matches(bytes, 12, "VP8 ") || matches(bytes, 12, "VP8L") || matches(bytes, 12, "VP8X"))) {
            return "image/webp";
        }
        if (matches(bytes, 4, "ftyp")) {
            String brand = new String(bytes, 8, 4, StandardCharsets.US_ASCII);
            String type = switch (brand) {
                case "avif", "avis" -> "image/avif";
                case "heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs" -> "image/heic";
                case "mif1", "msf1" -> "image/heif";
                case "crx " -> "image/x-chat-original";
                default -> null;
            };
            if (type != null) return type;
        }
        if (matches(bytes, 0, "\u0000\u0000\u0001\u0000")) return "image/x-icon";
        if (matches(bytes, 0, "8BPS")) return "image/vnd.adobe.photoshop";
        return null;
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
