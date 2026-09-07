package tech.kayys.suling.audio;

import java.util.Map;

/**
 * Encoded media bytes and their format metadata.
 */
public record EncodedMedia(
        byte[] bytes,
        String format,
        String mimeType,
        Map<String, String> metadata) {

    public EncodedMedia {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("format must not be blank");
        }
        if (mimeType == null || mimeType.isBlank()) {
            throw new IllegalArgumentException("mimeType must not be blank");
        }
        bytes = bytes.clone();
        format = format.trim().toLowerCase(java.util.Locale.ROOT);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
