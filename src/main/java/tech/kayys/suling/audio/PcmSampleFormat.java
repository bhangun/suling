package tech.kayys.suling.audio;

import java.util.Locale;

/**
 * Linear PCM sample formats accepted by Suling audio encoders.
 */
public enum PcmSampleFormat {
    S16_LE("s16le", 16, true);

    private final String id;
    private final int bitsPerSample;
    private final boolean littleEndian;

    PcmSampleFormat(String id, int bitsPerSample, boolean littleEndian) {
        this.id = id;
        this.bitsPerSample = bitsPerSample;
        this.littleEndian = littleEndian;
    }

    public String id() {
        return id;
    }

    public int bitsPerSample() {
        return bitsPerSample;
    }

    public int bytesPerSample() {
        return (bitsPerSample + 7) / 8;
    }

    public boolean littleEndian() {
        return littleEndian;
    }

    public static PcmSampleFormat parse(String value) {
        if (value == null || value.isBlank()) {
            return S16_LE;
        }
        String normalized = value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("-", "")
                .replace("_", "");
        return switch (normalized) {
            case "s16le", "pcm16", "pcm16le", "signed16le" -> S16_LE;
            default -> throw new IllegalArgumentException("Unsupported PCM sample format: " + value);
        };
    }
}
