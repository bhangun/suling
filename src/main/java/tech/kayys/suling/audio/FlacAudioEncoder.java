package tech.kayys.suling.audio;

import tech.kayys.suling.FlacAudioFormat;
import tech.kayys.suling.FlacLibraryCheck;
import tech.kayys.suling.io.FlacOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * FLAC encoder backend backed by Suling's libFLAC FFM bindings.
 */
public final class FlacAudioEncoder implements AudioEncoder {
    @Override
    public String name() {
        return "suling-flac-ffm";
    }

    @Override
    public Set<String> formats() {
        return Set.of("flac");
    }

    @Override
    public EncodedMedia encode(PcmAudio audio, AudioEncodeOptions options) throws IOException {
        if (!FlacLibraryCheck.isAvailable()) {
            throw new MediaCodecException("FLAC output requires libFLAC. " + FlacLibraryCheck.getDiagnostics());
        }
        if (audio.format().sampleFormat() != PcmSampleFormat.S16_LE) {
            throw new MediaCodecException("FLAC encoder currently supports PCM S16_LE only");
        }

        FlacAudioFormat flacFormat = new FlacAudioFormat(
                audio.format().channels(),
                audio.format().bitsPerSample(),
                audio.format().sampleRate(),
                audio.format().frames());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (FlacOutputStream flac = new FlacOutputStream(
                out,
                flacFormat,
                Math.min(8, Math.max(0, options.compressionLevel())))) {
            flac.write(audio.data());
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("audio_format", "flac");
        metadata.put("audio_mime", "audio/flac");
        metadata.put("audio_metadata_embedded", "false");
        metadata.put("audio_lossless", "true");
        metadata.put("flac_compression_level", String.valueOf(Math.min(8, Math.max(0, options.compressionLevel()))));
        metadata.put("flac_library_version", FlacLibraryCheck.getVersion());
        return new EncodedMedia(out.toByteArray(), "flac", "audio/flac", metadata);
    }
}
