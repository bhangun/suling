package tech.kayys.suling.encoder;

import org.junit.jupiter.api.Test;
import tech.kayys.suling.FlacAudioFormat;
import tech.kayys.suling.FlacException;
import tech.kayys.suling.IntegrationTestBase;

import java.io.ByteArrayOutputStream;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link FlacStreamEncoder}.
 * Requires libFLAC and {@code -Dflac.test.integration=true}.
 */
class FlacStreamEncoderTest extends IntegrationTestBase {

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Test
    void constructor_allocatesNativeObject() {
        assumeIntegration();
        assertDoesNotThrow(() -> {
            try (var enc = new FlacStreamEncoder()) {
                assertTrue(enc.isOpen());
            }
        });
    }

    @Test
    void close_isIdempotent() {
        assumeIntegration();
        var enc = new FlacStreamEncoder();
        enc.close();
        enc.close(); // must not throw
    }

    @Test
    void methodsThrow_afterClose() {
        assumeIntegration();
        var enc = new FlacStreamEncoder();
        enc.close();
        assertThrows(IllegalStateException.class, enc::getChannels);
        assertThrows(IllegalStateException.class, () -> enc.setChannels(2));
    }

    // -----------------------------------------------------------------------
    // applyFormat
    // -----------------------------------------------------------------------

    @Test
    void applyFormat_setsAllFields() {
        assumeIntegration();
        var fmt = new FlacAudioFormat(1, 24, 48_000, 100_000L);
        try (var enc = new FlacStreamEncoder()) {
            enc.applyFormat(fmt);
            assertEquals(1,       enc.getChannels());
            assertEquals(24,      enc.getBitsPerSample());
            assertEquals(48_000,  enc.getSampleRate());
            assertEquals(100_000L, enc.getTotalSamplesEstimate());
        }
    }

    // -----------------------------------------------------------------------
    // Setters / getters
    // -----------------------------------------------------------------------

    @Test
    void fluentChaining_returnsThis() {
        assumeIntegration();
        try (var enc = new FlacStreamEncoder()) {
            assertSame(enc, enc.setChannels(2).setBitsPerSample(16).setSampleRate(44_100));
        }
    }

    @Test
    void compressionLevel_roundtrip() {
        assumeIntegration();
        // setCompressionLevel configures multiple fields; just verify it doesn't throw
        try (var enc = new FlacStreamEncoder()) {
            for (int level = 0; level <= 8; level++) {
                enc.setCompressionLevel(level);
            }
        }
    }

    @Test
    void setApodization_nullThrows() {
        assumeIntegration();
        try (var enc = new FlacStreamEncoder()) {
            assertThrows(NullPointerException.class, () -> enc.setApodization(null));
        }
    }

    // -----------------------------------------------------------------------
    // File encoding
    // -----------------------------------------------------------------------

    @Test
    void encodeFile_createsNonEmptyFile() throws Exception {
        assumeIntegration();
        Path out = tempDir.resolve("enc_basic.flac");

        try (var enc = new FlacStreamEncoder()) {
            enc.applyFormat(FORMAT).setCompressionLevel(5);
            enc.initFile(out, null);
            enc.processInterleaved(TEST_PCM);
            enc.finish();
        }

        assertTrue(Files.exists(out));
        assertTrue(Files.size(out) > 1_000, "FLAC file should be at least 1 KB");
    }

    @Test
    void encodeFile_nonExistentDir_throwsInitException() {
        assumeIntegration();
        Path bad = Path.of("/nonexistent/dir/out.flac");
        try (var enc = new FlacStreamEncoder()) {
            enc.applyFormat(FORMAT);
            assertThrows(FlacException.FlacInitException.class,
                    () -> enc.initFile(bad, null));
        }
    }

    @Test
    void processInterleaved_wrongArrayLength_throws() {
        assumeIntegration();
        Path out = tempDir.resolve("enc_wrong_len.flac");
        try (var enc = new FlacStreamEncoder()) {
            enc.applyFormat(FORMAT).setCompressionLevel(0);
            enc.initFile(out, null);
            // 3 samples for 2-channel encoder → not divisible by channels
            assertThrows(IllegalArgumentException.class,
                    () -> enc.processInterleaved(new int[]{1, 2, 3}));
        }
    }

    @Test
    void processInterleaved_nullThrows() {
        assumeIntegration();
        Path out = tempDir.resolve("enc_null.flac");
        try (var enc = new FlacStreamEncoder()) {
            enc.applyFormat(FORMAT).setCompressionLevel(0);
            enc.initFile(out, null);
            assertThrows(NullPointerException.class,
                    () -> enc.processInterleaved(null));
        }
    }

    // -----------------------------------------------------------------------
    // Progress callback
    // -----------------------------------------------------------------------

    @Test
    void progressCallback_isInvoked() throws Exception {
        assumeIntegration();
        Path out = tempDir.resolve("enc_progress.flac");
        AtomicLong totalBytesReported = new AtomicLong(0);

        try (var enc = new FlacStreamEncoder()) {
            enc.applyFormat(FORMAT)
               .setTotalSamplesEstimate(NUM_FRAMES)
               .setCompressionLevel(3);
            enc.initFile(out, (bytesWritten, samplesWritten, frames, totalEst) ->
                    totalBytesReported.set(bytesWritten));
            enc.processInterleaved(TEST_PCM);
            enc.finish();
        }

        assertTrue(totalBytesReported.get() > 0, "Progress callback should report bytes written");
    }

    // -----------------------------------------------------------------------
    // Stream encoding (write callback)
    // -----------------------------------------------------------------------

    @Test
    void encodeToBytes_producesValidFlacStream() {
        assumeIntegration();
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

        assertFalse(chunks.isEmpty(), "Expected at least one chunk");
        // Concatenate and verify magic number "fLaC"
        byte[] firstChunk = chunks.get(0);
        assertTrue(firstChunk.length >= 4);
        assertEquals((byte) 0x66, firstChunk[0]); // 'f'
        assertEquals((byte) 0x4C, firstChunk[1]); // 'L'
        assertEquals((byte) 0x61, firstChunk[2]); // 'a'
        assertEquals((byte) 0x43, firstChunk[3]); // 'C'
    }

    @Test
    void encodeToStream_totalBytesIsReasonable() {
        assumeIntegration();
        AtomicLong total = new AtomicLong(0);

        try (var enc = new FlacStreamEncoder()) {
            enc.applyFormat(FORMAT).setCompressionLevel(5);
            enc.initStream(
                    (buf, size, samples, frame) -> {
                        total.addAndGet(size);
                        return StreamEncoderH.WRITE_STATUS_OK;
                    },
                    null, null, null);
            enc.processInterleaved(TEST_PCM);
            enc.finish();
        }

        long rawPcmBytes = (long) NUM_FRAMES * CHANNELS * (BITS_PER_SAMPLE / 8);
        // FLAC should be meaningfully smaller than raw PCM for a sine wave
        assertTrue(total.get() > 0);
        assertTrue(total.get() < rawPcmBytes,
                "FLAC (" + total.get() + " B) should be smaller than raw PCM (" + rawPcmBytes + " B)");
    }

    // -----------------------------------------------------------------------
    // Per-channel encoding
    // -----------------------------------------------------------------------

    @Test
    void process_perChannel_encodesWithoutError() throws Exception {
        assumeIntegration();
        Path out = tempDir.resolve("enc_per_channel.flac");

        // Build per-channel buffers
        int ch = CHANNELS;
        int frames = 4096;
        int[][] channelBuffers = new int[ch][frames];
        for (int c = 0; c < ch; c++) {
            for (int i = 0; i < frames; i++) {
                channelBuffers[c][i] = (int) (Short.MAX_VALUE * Math.sin(2 * Math.PI * 440 * i / SAMPLE_RATE));
            }
        }

        try (var enc = new FlacStreamEncoder()) {
            enc.applyFormat(FORMAT).setCompressionLevel(3);
            enc.initFile(out, null);
            enc.process(channelBuffers, frames);
            enc.finish();
        }

        assertTrue(Files.size(out) > 100);
    }

    // -----------------------------------------------------------------------
    // Verify mode
    // -----------------------------------------------------------------------

    @Test
    void verifyMode_finishReturnsTrue() throws Exception {
        assumeIntegration();
        Path out = tempDir.resolve("enc_verify.flac");

        boolean md5Ok;
        try (var enc = new FlacStreamEncoder()) {
            enc.applyFormat(FORMAT).setCompressionLevel(5).setVerify(true);
            enc.initFile(out, null);
            enc.processInterleaved(TEST_PCM);
            md5Ok = enc.finish();
        }

        assertTrue(md5Ok, "MD5 verification should pass for faithfully encoded audio");
    }
}
