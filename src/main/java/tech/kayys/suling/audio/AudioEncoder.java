package tech.kayys.suling.audio;

import java.io.IOException;
import java.util.Set;

/**
 * Encodes interleaved PCM audio into one or more encoded audio formats.
 */
public interface AudioEncoder {
    String name();

    Set<String> formats();

    EncodedMedia encode(PcmAudio audio, AudioEncodeOptions options) throws IOException;

    default boolean supports(String format) {
        String normalized = AudioEncodeOptions.normalizeFormat(format);
        return formats().contains(normalized);
    }
}
