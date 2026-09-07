package tech.kayys.suling.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Interleaved PCM bytes plus format and optional descriptive metadata.
 */
public record PcmAudio(
        byte[] data,
        PcmAudioFormat format,
        Map<String, String> metadata) {

    public PcmAudio {
        if (data == null) {
            throw new NullPointerException("data");
        }
        if (format == null) {
            throw new NullPointerException("format");
        }
        data = data.clone();
        int frameSize = format.frameSizeBytes();
        if (frameSize <= 0 || data.length % frameSize != 0) {
            throw new IllegalArgumentException("PCM byte length must be a whole number of sample frames");
        }
        long inferredFrames = data.length / frameSize;
        if (format.frames() == 0 && inferredFrames > 0) {
            format = format.withFrames(inferredFrames);
        } else if (format.frames() > 0 && format.frames() != inferredFrames) {
            throw new IllegalArgumentException("PCM byte length does not match format frame count");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    public static PcmAudio fromInterleavedS16(
            byte[] pcm,
            int channels,
            int sampleRate,
            long frames,
            Map<String, String> metadata) {
        return new PcmAudio(
                pcm,
                new PcmAudioFormat(channels, sampleRate, PcmSampleFormat.S16_LE, frames),
                metadata);
    }

    public static PcmAudio fromChannelMajorFloat(
            float[] channelMajorAudio,
            int channels,
            int frames,
            int sampleRate,
            Map<String, String> metadata) {
        if (channelMajorAudio == null) {
            throw new NullPointerException("channelMajorAudio");
        }
        if (channels < 1) {
            throw new IllegalArgumentException("channels must be positive");
        }
        if (frames < 0) {
            throw new IllegalArgumentException("frames must be >= 0");
        }
        if (channelMajorAudio.length < channels * frames) {
            throw new IllegalArgumentException("channelMajorAudio is shorter than channels * frames");
        }
        ByteBuffer buffer = ByteBuffer.allocate(frames * channels * Short.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (int frame = 0; frame < frames; frame++) {
            for (int channel = 0; channel < channels; channel++) {
                float value = channelMajorAudio[channel * frames + frame];
                int pcm = Math.round(Math.max(-1.0f, Math.min(1.0f, value)) * 32767.0f);
                buffer.putShort((short) pcm);
            }
        }
        return fromInterleavedS16(buffer.array(), channels, sampleRate, frames, metadata);
    }

    public Map<String, String> mergedMetadata(Map<String, String> extra) {
        if (extra == null || extra.isEmpty()) {
            return metadata;
        }
        Map<String, String> merged = new LinkedHashMap<>(metadata);
        merged.putAll(extra);
        return Map.copyOf(merged);
    }
}
