package tech.kayys.suling.decoder;

import org.junit.jupiter.api.*;
import tech.kayys.suling.FlacAudioFormat;
import tech.kayys.suling.FlacException;
import tech.kayys.suling.IntegrationTestBase;
import tech.kayys.suling.format.FlacFormat;

import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link FlacStreamDecoder}.
 * Requires libFLAC and {@code -Dflac.test.integration=true}.
 */
class FlacStreamDecoderTest extends IntegrationTestBase {

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Test
    void constructor_allocatesNativeObject() {
        assumeIntegration();
        assertDoesNotThrow(() -> {
            try (var dec = new FlacStreamDecoder()) {
                assertTrue(dec.isOpen());
            }
        });
    }

    @Test
    void close_isIdempotent() {
        assumeIntegration();
        var dec = new FlacStreamDecoder();
        dec.close();
        dec.close(); // must not throw
    }

    @Test
    void methodsThrow_afterClose() {
        assumeIntegration();
        var dec = new FlacStreamDecoder();
        dec.close();
        assertThrows(IllegalStateException.class, dec::getChannels);
        assertThrows(IllegalStateException.class, () -> dec.initFile(SAMPLE_FLAC,
                (f, b) -> StreamDecoderH.WRITE_STATUS_CONTINUE, null, e -> {}));
    }

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    @Test
    void setMd5Checking_roundtrip() {
        assumeIntegration();
        try (var dec = new FlacStreamDecoder()) {
            assertTrue(dec.setMd5Checking(true));
            assertTrue(dec.getMd5Checking());
            assertTrue(dec.setMd5Checking(false));
            assertFalse(dec.getMd5Checking());
        }
    }

    @Test
    void setMetadataRespondAll_doesNotThrow() {
        assumeIntegration();
        try (var dec = new FlacStreamDecoder()) {
            assertDoesNotThrow(dec::setMetadataRespondAll);
        }
    }

    @Test
    void setMetadataRespondApplication_wrongLength_throws() {
        assumeIntegration();
        try (var dec = new FlacStreamDecoder()) {
            assertThrows(IllegalArgumentException.class,
                    () -> dec.setMetadataRespondApplication(new byte[]{1, 2, 3}));
        }
    }

    // -----------------------------------------------------------------------
    // File initialization
    // -----------------------------------------------------------------------

    @Test
    void initFile_nonExistentPath_throwsInitException() {
        assumeIntegration();
        try (var dec = new FlacStreamDecoder()) {
            Path bad = Path.of("/nonexistent/path/file.flac");
            assertThrows(FlacException.FlacInitException.class,
                    () -> dec.initFile(bad,
                            (f, b) -> StreamDecoderH.WRITE_STATUS_CONTINUE,
                            null,
                            e -> {}));
        }
    }

    @Test
    void initFile_validFile_returnsOkStatus() {
        assumeIntegration();
        try (var dec = new FlacStreamDecoder()) {
            assertDoesNotThrow(() -> dec.initFile(SAMPLE_FLAC,
                    (f, b) -> StreamDecoderH.WRITE_STATUS_CONTINUE,
                    null,
                    e -> {}));
        }
    }

    // -----------------------------------------------------------------------
    // Full decode round-trip
    // -----------------------------------------------------------------------

    @Test
    void decodeFile_sampleCountMatchesExpected() {
        assumeIntegration();
        AtomicInteger totalSamplesDecoded = new AtomicInteger(0);

        try (var dec = new FlacStreamDecoder()) {
            dec.setMd5Checking(true);
            dec.initFile(SAMPLE_FLAC,
                    (frame, buffers) -> {
                        totalSamplesDecoded.addAndGet(FlacPcmUtils.getBlocksize(frame));
                        return StreamDecoderH.WRITE_STATUS_CONTINUE;
                    },
                    null,
                    err -> fail("Unexpected decoder error: " + err));

            assertTrue(dec.processUntilEndOfStream());
        }

        assertEquals(NUM_FRAMES, totalSamplesDecoded.get());
    }

    @Test
    void decodeFile_audioFormatMatchesEncoded() {
        assumeIntegration();
        AtomicBoolean firstFrame = new AtomicBoolean(true);
        int[] frameChannels = new int[1];
        int[] frameBps      = new int[1];
        int[] frameSr       = new int[1];

        try (var dec = new FlacStreamDecoder()) {
            dec.initFile(SAMPLE_FLAC,
                    (frame, buffers) -> {
                        if (firstFrame.compareAndSet(true, false)) {
                            frameChannels[0] = FlacPcmUtils.getChannels(frame);
                            frameBps[0]      = FlacPcmUtils.getBitsPerSample(frame);
                            frameSr[0]       = FlacPcmUtils.getSampleRate(frame);
                        }
                        return StreamDecoderH.WRITE_STATUS_CONTINUE;
                    },
                    null,
                    err -> {});
            dec.processUntilEndOfStream();
        }

        assertEquals(CHANNELS,         frameChannels[0]);
        assertEquals(BITS_PER_SAMPLE,  frameBps[0]);
        assertEquals(SAMPLE_RATE,      frameSr[0]);
    }

    @Test
    void decodeFile_pcmValuesAreNonTrivial() {
        assumeIntegration();
        // The sine wave should produce non-zero and non-MAX samples
        List<int[]> frames = new ArrayList<>();
        try (var dec = new FlacStreamDecoder()) {
            dec.initFile(SAMPLE_FLAC,
                    (frame, buffers) -> {
                        frames.add(FlacPcmUtils.extractInterleavedInt32(frame, buffers));
                        return StreamDecoderH.WRITE_STATUS_CONTINUE;
                    },
                    null, err -> {});
            dec.processUntilEndOfStream();
        }
        assertFalse(frames.isEmpty());

        // Find max absolute value across all samples
        int maxAbs = 0;
        for (int[] f : frames) {
            for (int s : f) if (Math.abs(s) > maxAbs) maxAbs = Math.abs(s);
        }
        assertTrue(maxAbs > 1000, "Expected meaningful PCM amplitude, got max=" + maxAbs);
    }

    // -----------------------------------------------------------------------
    // State queries after decode
    // -----------------------------------------------------------------------

    @Test
    void getters_returnExpectedValues_afterDecode() {
        assumeIntegration();
        try (var dec = new FlacStreamDecoder()) {
            dec.setMetadataRespondAll();
            dec.initFile(SAMPLE_FLAC,
                    (f, b) -> StreamDecoderH.WRITE_STATUS_CONTINUE,
                    null, e -> {});
            dec.processUntilEndOfStream();

            assertEquals(SAMPLE_RATE,     dec.getSampleRate());
            assertEquals(CHANNELS,        dec.getChannels());
            assertEquals(BITS_PER_SAMPLE, dec.getBitsPerSample());
            assertEquals(NUM_FRAMES,      dec.getTotalSamples());
        }
    }

    // -----------------------------------------------------------------------
    // Seek
    // -----------------------------------------------------------------------

    @Test
    void seekAbsolute_succeedsForValidSampleNumber() {
        assumeIntegration();
        AtomicInteger framesAfterSeek = new AtomicInteger(0);

        try (var dec = new FlacStreamDecoder()) {
            dec.setMd5Checking(false);
            dec.initFile(SAMPLE_FLAC,
                    (frame, buffers) -> {
                        framesAfterSeek.incrementAndGet();
                        return StreamDecoderH.WRITE_STATUS_CONTINUE;
                    },
                    null, err -> {});

            dec.processUntilEndOfMetadata();
            boolean sought = dec.seekAbsolute(SAMPLE_RATE / 2); // mid-file
            assertTrue(sought, "seekAbsolute should succeed for a valid seekable file");

            dec.processSingle();
        }

        assertTrue(framesAfterSeek.get() > 0, "Expected at least one frame after seek");
    }

    // -----------------------------------------------------------------------
    // Metadata callback
    // -----------------------------------------------------------------------

    @Test
    void metadataCallback_receivesStreamInfo() {
        assumeIntegration();
        AtomicBoolean gotStreamInfo = new AtomicBoolean(false);

        try (var dec = new FlacStreamDecoder()) {
            dec.setMetadataRespond(FlacFormat.MetadataType.STREAMINFO);
            dec.initFile(SAMPLE_FLAC,
                    (f, b) -> StreamDecoderH.WRITE_STATUS_CONTINUE,
                    meta -> {
                        // FLAC__StreamMetadata.type is first field (int at offset 0)
                        int type = meta.get(java.lang.foreign.ValueLayout.JAVA_INT, 0);
                        if (type == FlacFormat.MetadataType.STREAMINFO) {
                            gotStreamInfo.set(true);
                        }
                    },
                    err -> {});
            dec.processUntilEndOfMetadata();
        }
        assertTrue(gotStreamInfo.get(), "Expected STREAMINFO metadata callback");
    }

    // -----------------------------------------------------------------------
    // PCM extraction utilities
    // -----------------------------------------------------------------------

    @Test
    void extractPerChannelInt32_channelCountAndBlocksize() {
        assumeIntegration();
        AtomicBoolean checked = new AtomicBoolean(false);

        try (var dec = new FlacStreamDecoder()) {
            dec.initFile(SAMPLE_FLAC,
                    (frame, buffers) -> {
                        if (!checked.get()) {
                            int bs = FlacPcmUtils.getBlocksize(frame);
                            int ch = FlacPcmUtils.getChannels(frame);
                            int[][] perCh = FlacPcmUtils.extractPerChannelInt32(frame, buffers);
                            assertEquals(ch,  perCh.length);
                            assertEquals(bs,  perCh[0].length);
                            checked.set(true);
                        }
                        return StreamDecoderH.WRITE_STATUS_CONTINUE;
                    },
                    null, err -> {});
            dec.processSingle(); // one frame is enough
        }

        assertTrue(checked.get());
    }

    @Test
    void extractInterleavedShort_lengthIsBlocksizeTimesChannels() {
        assumeIntegration();
        AtomicBoolean checked = new AtomicBoolean(false);

        try (var dec = new FlacStreamDecoder()) {
            dec.initFile(SAMPLE_FLAC,
                    (frame, buffers) -> {
                        if (!checked.get()) {
                            int bs  = FlacPcmUtils.getBlocksize(frame);
                            int ch  = FlacPcmUtils.getChannels(frame);
                            int bps = FlacPcmUtils.getBitsPerSample(frame);
                            short[] s = FlacPcmUtils.extractInterleavedShort(frame, buffers, bps);
                            assertEquals(bs * ch, s.length);
                            checked.set(true);
                        }
                        return StreamDecoderH.WRITE_STATUS_CONTINUE;
                    },
                    null, err -> {});
            dec.processSingle();
        }

        assertTrue(checked.get());
    }

    @Test
    void channelBufferView_isZeroCopy_inCallback() {
        assumeIntegration();
        AtomicBoolean verified = new AtomicBoolean(false);

        try (var dec = new FlacStreamDecoder()) {
            dec.initFile(SAMPLE_FLAC,
                    (frame, buffers) -> {
                        if (!verified.get()) {
                            int bs = FlacPcmUtils.getBlocksize(frame);
                            var view = FlacPcmUtils.channelBufferView(buffers, 0, bs);
                            assertEquals(bs, view.limit());
                            verified.set(true);
                        }
                        return StreamDecoderH.WRITE_STATUS_CONTINUE;
                    },
                    null, err -> {});
            dec.processSingle();
        }

        assertTrue(verified.get());
    }
}
