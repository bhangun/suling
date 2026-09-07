package tech.kayys.suling.audio;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Codec-neutral options for audio encoding.
 */
public record AudioEncodeOptions(
        String format,
        int compressionLevel,
        int bitrateKbps,
        boolean verify,
        Map<String, String> metadata) {

    public AudioEncodeOptions {
        format = normalizeFormat(format);
        if (compressionLevel < 0 || compressionLevel > 12) {
            throw new IllegalArgumentException("compressionLevel must be between 0 and 12");
        }
        if (bitrateKbps < 0) {
            throw new IllegalArgumentException("bitrateKbps must be >= 0");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static AudioEncodeOptions wav() {
        return builder().format("wav").build();
    }

    public static AudioEncodeOptions flac() {
        return builder().format("flac").compressionLevel(5).build();
    }

    public static AudioEncodeOptions forPath(Path path) {
        return builder().format(extension(path)).build();
    }

    public static AudioEncodeOptions forExtension(String extension) {
        return builder().format(extension).build();
    }

    public AudioEncodeOptions withFormat(String value) {
        return new AudioEncodeOptions(value, compressionLevel, bitrateKbps, verify, metadata);
    }

    public AudioEncodeOptions withMetadata(Map<String, String> extra) {
        if (extra == null || extra.isEmpty()) {
            return this;
        }
        Map<String, String> merged = new LinkedHashMap<>(metadata);
        merged.putAll(extra);
        return new AudioEncodeOptions(format, compressionLevel, bitrateKbps, verify, merged);
    }

    public static Builder builder() {
        return new Builder();
    }

    static String normalizeFormat(String value) {
        if (value == null || value.isBlank()) {
            return "wav";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return switch (normalized) {
            case "wave", "audio/wav", "audio/x-wav" -> "wav";
            case "audio/flac", "x-flac" -> "flac";
            case "mpeg", "audio/mpeg" -> "mp3";
            case "oga", "ogg" -> "opus";
            default -> normalized;
        };
    }

    private static String extension(Path path) {
        if (path == null || path.getFileName() == null) {
            return "wav";
        }
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot + 1 < name.length() ? name.substring(dot + 1) : "wav";
    }

    public static final class Builder {
        private String format = "wav";
        private int compressionLevel = 5;
        private int bitrateKbps = 192;
        private boolean verify;
        private final Map<String, String> metadata = new LinkedHashMap<>();

        public Builder format(String value) {
            this.format = value;
            return this;
        }

        public Builder compressionLevel(int value) {
            this.compressionLevel = value;
            return this;
        }

        public Builder bitrateKbps(int value) {
            this.bitrateKbps = value;
            return this;
        }

        public Builder verify(boolean value) {
            this.verify = value;
            return this;
        }

        public Builder metadata(String key, String value) {
            if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                metadata.put(key, value);
            }
            return this;
        }

        public Builder metadata(Map<String, String> values) {
            if (values != null) {
                values.forEach(this::metadata);
            }
            return this;
        }

        public AudioEncodeOptions build() {
            return new AudioEncodeOptions(format, compressionLevel, bitrateKbps, verify, metadata);
        }
    }
}
