package tech.kayys.suling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tech.kayys.suling.decoder.FlacPcmUtils;
import tech.kayys.suling.decoder.FlacStreamDecoder;
import tech.kayys.suling.decoder.StreamDecoderH;
import tech.kayys.suling.encoder.FlacStreamEncoder;
import tech.kayys.suling.encoder.StreamEncoderH;
import tech.kayys.suling.metadata.FlacMetadata;

import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end encode → decode → verify round-trip tests.
 * Requires libFLAC and {@code -Dflac.test.integration=true}.
 */
class RoundTripTest extends IntegrationTestBase {

    // -----------------------------------------------------------------------
    // Basic round-trip
    // -----------------------------------------------------------------------

    @Test
    void roundTrip_sampleCountPreserved() {
        assumeIntegration();
        Path out = tempDir.resolve("rt_count.flac");
        writeSampleFile(out);

        AtomicInteger decoded = new AtomicInteger(0);
        try (var dec = new FlacStreamDecoder()) {
            dec.setMd5Checking(true);
            dec.initFile(out,
                    (frame, buffers) -> {
                        decoded.addAndGet(FlacPcmUtils.getBlocksize(frame));
                        return StreamDecoderH.WRITE_STATUS_CONTINUE;
                    },
                    null,
                    err -> fail("Unexpected decoder error: " + err));
            assertTrue(dec.processUntilEndOfStream());
        }

        assertEquals(NUM_FRAMES, decoded.get());
    }

    @Test
    void roundTrip_pcmValuesAreCloseToOriginal() {
        assumeIntegration();
        Path out = tempDir.resolve("rt_pcm.flac");
        writeSampleFile(out);

        List<int[]> frames = new ArrayList<>();
        try (var dec = new FlacStreamDecoder()) {
            dec.setMd5Checking(true);
            dec.initFile(out,
                    (frame, buffers) -> {
                        frames.add(FlacPcmUtils.extractInterleavedInt32(frame, buffers));
                        return StreamDecoderH.WRITE_STATUS_CONTINUE;
                    },
                    null,
                    err -> {});
            dec.processUntilEndOfStream();
        }

        // Flatten decoded PCM
        int totalSamples = frames.stream().mapToInt(a -> a.length).sum();
        int[] decoded = new int[totalSamples];
        int pos = 0;
        for (int[] f : frames) {
            System.arraycopy(f, 0, decoded, pos, f.length);
            pos += f.length;
        }

        assertEquals(TEST_PCM.length, decoded.length, "Sample count mismatch");

        // FLAC is lossless; every sample must match exactly
        int mismatches = 0;
        for (int i = 0; i < TEST_PCM.length; i++) {
            if (TEST_PCM[i] != decoded[i]) mismatches++;
        }
        assertEquals(0, mismatches, "FLAC round-trip should be lossless");
    }

    // -----------------------------------------------------------------------
    // Parametrized round-trips over different formats
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "1, 16, 44100",   // mono CD
            "2, 16, 44100",   // stereo CD
            "2, 24, 48000",   // studio stereo
            "1, 24, 96000",   // hi-res mono
            "2, 8,  22050",   // lo-fi stereo
            "6, 16, 44100",   // 5.1 surround
    })
    void roundTrip_variousFormats(int channels, int bitsPerSample, int sampleRate) {
        assumeIntegration();

        var fmt    = new FlacAudioFormat(channels, bitsPerSample, sampleRate, 0);
        int frames = 4096;
        int[] pcm  = generateSine(440.0, sampleRate, channels, frames);

        Path out = tempDir.resolve(String.format("rt_%dch_%dbit_%dhz.flac",
                channels, bitsPerSample, sampleRate));

        // Encode
        try (var enc = new FlacStreamEncoder()) {
            enc.applyFormat(fmt).setCompressionLevel(3).setVerify(true);
            enc.initFile(out, null);
            enc.processInterleaved(pcm);
            assertTrue(enc.finish(), "MD5 verify should pass");
        }

        // Decode and verify
        AtomicInteger samplesDecoded = new AtomicInteger(0);
        List<int[]> decodedFrames   = new ArrayList<>();

        try (var dec = new FlacStreamDecoder()) {
            dec.setMd5Checking(true);
            dec.initFile(out,
                    (frame, buffers) -> {
                        samplesDecoded.addAndGet(FlacPcmUtils.getBlocksize(frame));
                        decodedFrames.add(FlacPcmUtils.extractInterleavedInt32(frame, buffers));
                        return StreamDecoderH.WRITE_STATUS_CONTINUE;
                    },
                    null,
                    err -> fail("Decoder error for format " + fmt + ": " + err));
            assertTrue(dec.processUntilEndOfStream(),
                    "processUntilEndOfStream failed for " + fmt);
        }

        assertEquals(frames, samplesDecoded.get(),
                "Sample count mismatch for " + fmt);

        // Flatten and compare
        int[] decoded = decodedFrames.stream()
                .reduce(new int[0], (a, b) -> {
                    int[] c = new int[a.length + b.length];
                    System.arraycopy(a, 0, c, 0, a.length);
                    System.arraycopy(b, 0, c, a.length, b.length);
                    return c;
                });

        assertEquals(pcm.length, decoded.length);
        for (int i = 0; i < pcm.length; i++) {
            if (pcm[i] != decoded[i]) {
                fail(String.format("Lossless mismatch at sample %d (expected %d, got %d) for %s",
                        i, pcm[i], decoded[i], fmt));
            }
        }
    }

    // -----------------------------------------------------------------------
    // Stream encode → file decode
    // -----------------------------------------------------------------------

    @Test
    void streamEncode_fileDecodeRoundtrip() throws Exception {
        assumeIntegration();

        // Collect FLAC bytes via stream encoder
        List<byte[]> chunks = new ArrayList<>();
        try (var enc = new FlacStreamEncoder()) {
            enc.applyFormat(FORMAT).setCompressionLevel(5);
            enc.initStream(
                    (buf, size, samples, frame) -> {
                        byte[] chunk = new byte[(int) size];
                        MemorySegment.ofArray(chunk).copyFrom(buf.reinterpret(size));
                        chunks.add(chunk);
                        return StreamEncoderH.WRITE_STATUS_OK;
                    },
                    null, null, null);
            enc.processInterleaved(TEST_PCM);
            enc.finish();
        }

        // Write to file
        Path out = tempDir.resolve("rt_stream.flac");
        int total = chunks.stream().mapToInt(c -> c.length).sum();
        byte[] flacData = new byte[total];
        int p = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, flacData, p, chunk.length);
            p += chunk.length;
        }
        java.nio.file.Files.write(out, flacData);

        // Decode and count samples
        AtomicInteger decoded = new AtomicInteger(0);
        try (var dec = new FlacStreamDecoder()) {
            dec.initFile(out,
                    (frame, buffers) -> {
                        decoded.addAndGet(FlacPcmUtils.getBlocksize(frame));
                        return StreamDecoderH.WRITE_STATUS_CONTINUE;
                    },
                    null, err -> {});
            dec.processUntilEndOfStream();
        }

        assertEquals(NUM_FRAMES, decoded.get());
    }

    // -----------------------------------------------------------------------
    // Metadata round-trip
    // -----------------------------------------------------------------------

    @Test
    void streamInfo_matchesEncodeParams() {
        assumeIntegration();
        var info = FlacMetadata.getStreamInfo(SAMPLE_FLAC);
        assertEquals(SAMPLE_RATE,     info.sampleRate());
        assertEquals(CHANNELS,        info.channels());
        assertEquals(BITS_PER_SAMPLE, info.bitsPerSample());
        assertEquals(NUM_FRAMES,      info.totalSamples());
    }
}
