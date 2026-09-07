package tech.kayys.suling.audio;

import tech.kayys.suling.ffmpeg.FfmpegLibraryCheck;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Registry of Suling audio encoders.
 */
public final class CodecRegistry {
    private final List<AudioEncoder> encoders;

    public CodecRegistry(List<AudioEncoder> encoders) {
        this.encoders = List.copyOf(encoders);
    }

    public static CodecRegistry createDefault() {
        List<AudioEncoder> encoders = new ArrayList<>();
        encoders.add(new WavAudioEncoder());
        encoders.add(new FlacAudioEncoder());
        encoders.add(new FfmpegAudioEncoder());
        return new CodecRegistry(encoders);
    }

    public List<AudioEncoder> encoders() {
        return Collections.unmodifiableList(encoders);
    }

    public Set<String> supportedFormats() {
        Set<String> formats = new LinkedHashSet<>();
        for (AudioEncoder encoder : encoders) {
            formats.addAll(encoder.formats());
        }
        return Collections.unmodifiableSet(formats);
    }

    public boolean supports(String format) {
        String normalized = AudioEncodeOptions.normalizeFormat(format);
        return encoders.stream().anyMatch(encoder -> encoder.supports(normalized));
    }

    public EncodedMedia encode(PcmAudio audio, AudioEncodeOptions options) throws IOException {
        String format = AudioEncodeOptions.normalizeFormat(options == null ? null : options.format());
        AudioEncodeOptions effective = options == null
                ? AudioEncodeOptions.builder().format(format).build()
                : options.withFormat(format);
        for (AudioEncoder encoder : encoders) {
            if (encoder.supports(format)) {
                return encoder.encode(audio, effective);
            }
        }
        throw new MediaCodecException(unsupportedFormatMessage(format));
    }

    private String unsupportedFormatMessage(String format) {
        StringBuilder message = new StringBuilder("Unsupported audio output format: ")
                .append(format)
                .append(". Supported Suling audio formats: ")
                .append(String.join(", ", supportedFormats()))
                .append(".");
        if (Set.of("opus", "ogg", "m4a", "aac").contains(format)) {
            if (FfmpegLibraryCheck.isAvailable()) {
                message.append(" FFmpeg FFM bridge is available (")
                        .append(FfmpegLibraryCheck.versionSummary())
                        .append("), but this container/codec backend is not implemented yet.");
            } else {
                message.append(" FFmpeg FFM bridge is not available; ")
                        .append("install FFmpeg libraries to enable the future backend.");
            }
        }
        return message.toString();
    }
}
