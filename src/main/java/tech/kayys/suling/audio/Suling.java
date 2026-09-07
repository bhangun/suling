package tech.kayys.suling.audio;

import tech.kayys.suling.FlacLibraryCheck;
import tech.kayys.suling.ffmpeg.FfmpegLibraryCheck;

import java.io.IOException;
import java.util.Set;

/**
 * Main facade for Suling's codec-neutral audio API.
 */
public final class Suling {
    private static final CodecRegistry DEFAULT_REGISTRY = CodecRegistry.createDefault();

    private Suling() {
    }

    public static EncodedMedia encode(PcmAudio audio, AudioEncodeOptions options) throws IOException {
        return DEFAULT_REGISTRY.encode(audio, options);
    }

    public static PcmAudio process(PcmAudio audio, AudioProcessingOptions options) {
        return PcmAudioProcessor.process(audio, options);
    }

    public static PcmAudio polishForSpeech(PcmAudio audio) {
        return process(audio, AudioProcessingOptions.speechPolish());
    }

    public static EncodedMedia encode(PcmAudio audio, String format) throws IOException {
        return encode(audio, AudioEncodeOptions.builder().format(format).build());
    }

    public static Set<String> supportedAudioFormats() {
        return DEFAULT_REGISTRY.supportedFormats();
    }

    public static boolean supportsAudioFormat(String format) {
        return DEFAULT_REGISTRY.supports(format);
    }

    public static String diagnostics() {
        return "Suling Diagnostics\n"
                + "==================\n"
                + "Audio formats: " + String.join(", ", supportedAudioFormats()) + "\n\n"
                + FlacLibraryCheck.getDiagnostics() + "\n"
                + FfmpegLibraryCheck.getDiagnostics();
    }
}
