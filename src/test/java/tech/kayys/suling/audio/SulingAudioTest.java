package tech.kayys.suling.audio;

import org.junit.jupiter.api.Test;
import tech.kayys.suling.FlacLibraryCheck;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SulingAudioTest {
    @Test
    void wavEncodeWritesRiffHeader() throws Exception {
        PcmAudio audio = sinePcm();
        EncodedMedia encoded = Suling.encode(audio, AudioEncodeOptions.wav());
        byte[] bytes = encoded.bytes();

        assertEquals("wav", encoded.format());
        assertEquals("audio/wav", encoded.mimeType());
        assertEquals('R', bytes[0]);
        assertEquals('I', bytes[1]);
        assertEquals('F', bytes[2]);
        assertEquals('F', bytes[3]);
        assertEquals('W', bytes[8]);
        assertEquals('A', bytes[9]);
        assertEquals('V', bytes[10]);
        assertEquals('E', bytes[11]);
    }

    @Test
    void flacEncodeWritesFlacMarkerWhenLibFlacAvailable() throws Exception {
        assumeTrue(FlacLibraryCheck.isAvailable(), "libFLAC is required for this test");

        EncodedMedia encoded = Suling.encode(sinePcm(), AudioEncodeOptions.flac());
        assertEquals("flac", encoded.format());
        assertEquals("audio/flac", encoded.mimeType());
        assertArrayEquals(new byte[] {'f', 'L', 'a', 'C'}, java.util.Arrays.copyOf(encoded.bytes(), 4));
    }

    @Test
    void unsupportedCompressedFormatsExplainFfmpegBridge() {
        MediaCodecException error = assertThrows(MediaCodecException.class,
                () -> Suling.encode(sinePcm(), AudioEncodeOptions.forExtension("opus")));
        assertTrue(error.getMessage().contains("Unsupported audio output format: opus"));
        assertTrue(error.getMessage().contains("FFmpeg FFM bridge"));
    }

    @Test
    void mp3EncodeWritesMpegFramesWhenFfmpegLameAvailable() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                FfmpegAudioEncoder.isMp3EncodingAvailable(),
                "FFmpeg libmp3lame encoder is required for this test");

        EncodedMedia encoded = Suling.encode(sinePcm(), AudioEncodeOptions.builder()
                .format("mp3")
                .bitrateKbps(128)
                .build());
        byte[] bytes = encoded.bytes();

        assertEquals("mp3", encoded.format());
        assertEquals("audio/mpeg", encoded.mimeType());
        assertTrue(bytes.length > 128);
        boolean hasId3 = bytes.length >= 3 && bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3';
        boolean hasFrameSync = bytes.length >= 2 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xe0) == 0xe0;
        assertTrue(hasId3 || hasFrameSync, "Expected MP3 ID3 tag or MPEG frame sync");
    }

    @Test
    void processingRemovesDcOffsetAndFadesEdges() {
        int sampleRate = 1_000;
        int frames = 100;
        ByteBuffer buffer = ByteBuffer.allocate(frames * Short.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frames; i++) {
            short sample = (short) (1_000 + Math.round(Math.sin(2.0 * Math.PI * i / 16.0) * 500.0));
            buffer.putShort(sample);
        }
        PcmAudio source = PcmAudio.fromInterleavedS16(buffer.array(), 1, sampleRate, frames, Map.of());
        PcmAudio dcRemoved = Suling.process(source, AudioProcessingOptions.builder()
                .removeDcOffset(true)
                .build());
        assertTrue(Math.abs(mean(readS16(dcRemoved.data()))) < 5.0);

        PcmAudio faded = Suling.process(source, AudioProcessingOptions.builder()
                .fadeInSeconds(0.01)
                .fadeOutSeconds(0.01)
                .build());

        short[] samples = readS16(faded.data());
        assertEquals(0, samples[0]);
        assertEquals(0, samples[samples.length - 1]);
        assertEquals("true", faded.metadata().get("audio_processing"));
    }

    @Test
    void peakNormalizeTargetsDbfs() {
        short[] sourceSamples = new short[] { 1_000, -1_000, 500, -500 };
        ByteBuffer buffer = ByteBuffer.allocate(sourceSamples.length * Short.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : sourceSamples) {
            buffer.putShort(sample);
        }
        PcmAudio source = PcmAudio.fromInterleavedS16(buffer.array(), 1, 48_000, sourceSamples.length, Map.of());
        PcmAudio processed = Suling.process(source, AudioProcessingOptions.builder()
                .peakNormalizeDbfs(-6.0)
                .maxNormalizeGainDb(30.0)
                .build());

        short[] samples = readS16(processed.data());
        int peak = 0;
        for (short sample : samples) {
            peak = Math.max(peak, Math.abs((int) sample));
        }
        int expected = (int) Math.round(32767.0 * Math.pow(10.0, -6.0 / 20.0));
        assertTrue(Math.abs(peak - expected) <= 1, "peak=" + peak + " expected=" + expected);
        assertEquals("-6.00", processed.metadata().get("audio_processing_peak_target_dbfs"));
    }

    @Test
    void processingCanTrimLeadingAndTrailingSilence() {
        int sampleRate = 1_000;
        short[] sourceSamples = new short[] { 0, 1, 2, 0, 900, 1_000, 850, 0, 3, 1, 0 };
        ByteBuffer buffer = ByteBuffer.allocate(sourceSamples.length * Short.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : sourceSamples) {
            buffer.putShort(sample);
        }
        PcmAudio source = PcmAudio.fromInterleavedS16(buffer.array(), 1, sampleRate, sourceSamples.length, Map.of());
        PcmAudio processed = Suling.process(source, AudioProcessingOptions.builder()
                .trimSilence(true)
                .trimSilenceThresholdDbfs(-40.0)
                .trimSilencePaddingSeconds(0.001)
                .build());

        short[] samples = readS16(processed.data());
        assertArrayEquals(new short[] { 0, 900, 1_000, 850, 0 }, samples);
        assertEquals(5, processed.format().frames());
        assertEquals("true", processed.metadata().get("audio_processing_trim_silence"));
        assertEquals("6.0", processed.metadata().get("audio_processing_trim_removed_ms"));
    }

    private static PcmAudio sinePcm() {
        int sampleRate = 48_000;
        int channels = 2;
        int frames = 1024;
        ByteBuffer buffer = ByteBuffer.allocate(frames * channels * Short.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frames; i++) {
            short sample = (short) Math.round(Math.sin(2.0 * Math.PI * i / 32.0) * 10_000.0);
            buffer.putShort(sample);
            buffer.putShort(sample);
        }
        return PcmAudio.fromInterleavedS16(buffer.array(), channels, sampleRate, frames, Map.of("INAM", "test"));
    }

    private static short[] readS16(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        short[] samples = new short[data.length / Short.BYTES];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = buffer.getShort(i * Short.BYTES);
        }
        return samples;
    }

    private static double mean(short[] samples) {
        double sum = 0.0;
        for (short sample : samples) {
            sum += sample;
        }
        return sum / samples.length;
    }
}
