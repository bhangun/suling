package tech.kayys.suling.audio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure Java RIFF/WAVE encoder for interleaved signed PCM.
 */
public final class WavAudioEncoder implements AudioEncoder {
    @Override
    public String name() {
        return "suling-wav";
    }

    @Override
    public Set<String> formats() {
        return Set.of("wav");
    }

    @Override
    public EncodedMedia encode(PcmAudio audio, AudioEncodeOptions options) {
        if (audio.format().sampleFormat() != PcmSampleFormat.S16_LE) {
            throw new MediaCodecException("WAV encoder currently supports PCM S16_LE only");
        }
        byte[] pcm = audio.data();
        byte[] infoChunk = buildInfoChunk(audio.mergedMetadata(options.metadata()));
        int dataSize = pcm.length;
        int riffSize = 36 + infoChunk.length + dataSize;
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + infoChunk.length + dataSize);
        try {
            writeAscii(out, "RIFF");
            writeIntLE(out, riffSize);
            writeAscii(out, "WAVE");
            writeAscii(out, "fmt ");
            writeIntLE(out, 16);
            writeShortLE(out, (short) 1);
            writeShortLE(out, (short) audio.format().channels());
            writeIntLE(out, audio.format().sampleRate());
            writeIntLE(out, (int) audio.format().byteRate());
            writeShortLE(out, (short) audio.format().frameSizeBytes());
            writeShortLE(out, (short) audio.format().bitsPerSample());
            out.write(infoChunk);
            writeAscii(out, "data");
            writeIntLE(out, dataSize);
            out.write(pcm);
        } catch (IOException e) {
            throw new MediaCodecException("Failed to encode WAV audio", e);
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("audio_format", "wav");
        metadata.put("audio_mime", "audio/wav");
        metadata.put("audio_metadata_embedded", String.valueOf(!infoChunkIsEmpty(infoChunk)));
        return new EncodedMedia(out.toByteArray(), "wav", "audio/wav", metadata);
    }

    private static boolean infoChunkIsEmpty(byte[] infoChunk) {
        return infoChunk == null || infoChunk.length == 0;
    }

    private static byte[] buildInfoChunk(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return new byte[0];
        }

        ByteArrayOutputStream info = new ByteArrayOutputStream();
        try {
            writeAscii(info, "INFO");
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                String key = normalizeInfoKey(entry.getKey());
                String value = normalizeInfoValue(entry.getValue());
                if (key.isBlank() || value.isBlank()) {
                    continue;
                }
                byte[] payload = value.getBytes(StandardCharsets.UTF_8);
                int chunkSize = payload.length + 1;
                writeAscii(info, key);
                writeIntLE(info, chunkSize);
                info.write(payload);
                info.write(0);
                if ((chunkSize & 1) != 0) {
                    info.write(0);
                }
            }
        } catch (IOException e) {
            throw new MediaCodecException("Failed to encode WAV INFO metadata", e);
        }

        if (info.size() <= 4) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(8 + info.size() + (info.size() & 1));
        try {
            writeAscii(out, "LIST");
            writeIntLE(out, info.size());
            out.write(info.toByteArray());
            if ((info.size() & 1) != 0) {
                out.write(0);
            }
        } catch (IOException e) {
            throw new MediaCodecException("Failed to encode WAV LIST metadata", e);
        }
        return out.toByteArray();
    }

    private static String normalizeInfoKey(String key) {
        if (key == null) {
            return "";
        }
        String value = key.trim().toUpperCase(Locale.ROOT);
        if (value.length() != 4) {
            return "";
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 'A' || c > 'Z') {
                return "";
            }
        }
        return value;
    }

    private static String normalizeInfoValue(String value) {
        if (value == null) {
            return "";
        }
        return value.strip().replace('\0', ' ');
    }

    private static void writeAscii(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static void writeIntLE(ByteArrayOutputStream out, int value) throws IOException {
        out.write(ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array());
    }

    private static void writeShortLE(ByteArrayOutputStream out, short value) throws IOException {
        out.write(ByteBuffer.allocate(Short.BYTES).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array());
    }
}
