package tech.kayys.suling.metadata;

import org.junit.jupiter.api.Test;
import tech.kayys.suling.FlacAudioFormat;
import tech.kayys.suling.FlacException;
import tech.kayys.suling.IntegrationTestBase;
import tech.kayys.suling.format.FlacFormat;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link FlacMetadata}.
 * Requires libFLAC and {@code -Dflac.test.integration=true}.
 */
class FlacMetadataTest extends IntegrationTestBase {

    // -----------------------------------------------------------------------
    // Level 0 – StreamInfo
    // -----------------------------------------------------------------------

    @Test
    void getStreamInfo_returnsCorrectFormat() {
        assumeIntegration();
        var info = FlacMetadata.getStreamInfo(SAMPLE_FLAC);

        assertEquals(SAMPLE_RATE,     info.sampleRate());
        assertEquals(CHANNELS,        info.channels());
        assertEquals(BITS_PER_SAMPLE, info.bitsPerSample());
        assertEquals(NUM_FRAMES,      info.totalSamples());
    }

    @Test
    void getStreamInfo_md5sumIsNonZero() {
        assumeIntegration();
        var info = FlacMetadata.getStreamInfo(SAMPLE_FLAC);
        assertNotNull(info.md5sum());
        assertEquals(16, info.md5sum().length);
        // At least one non-zero byte in MD5
        boolean anyNonZero = false;
        for (byte b : info.md5sum()) if (b != 0) { anyNonZero = true; break; }
        assertTrue(anyNonZero, "MD5 sum should be non-zero for actual audio");
    }

    @Test
    void getStreamInfo_md5HexIs32Chars() {
        assumeIntegration();
        var info = FlacMetadata.getStreamInfo(SAMPLE_FLAC);
        String hex = info.md5Hex();
        assertEquals(32, hex.length());
        assertTrue(hex.matches("[0-9a-f]+"), "md5Hex should be lowercase hex");
    }

    @Test
    void getStreamInfo_durationSeconds_matchesExpected() {
        assumeIntegration();
        var info = FlacMetadata.getStreamInfo(SAMPLE_FLAC);
        double expected = (double) NUM_FRAMES / SAMPLE_RATE;
        assertEquals(expected, info.durationSeconds(), 0.001);
    }

    @Test
    void getStreamInfo_toAudioFormat_roundtrip() {
        assumeIntegration();
        var info = FlacMetadata.getStreamInfo(SAMPLE_FLAC);
        FlacAudioFormat fmt = info.toAudioFormat();

        assertEquals(info.channels(),       fmt.channels());
        assertEquals(info.bitsPerSample(),  fmt.bitsPerSample());
        assertEquals(info.sampleRate(),     fmt.sampleRate());
        assertEquals(info.totalSamples(),   fmt.totalSamples());
    }

    @Test
    void getStreamInfo_nonExistentFile_throws() {
        assumeIntegration();
        assertThrows(FlacException.FlacMetadataException.class,
                () -> FlacMetadata.getStreamInfo(Path.of("/nonexistent.flac")));
    }

    @Test
    void getStreamInfo_blockSizeLimitsAreValid() {
        assumeIntegration();
        var info = FlacMetadata.getStreamInfo(SAMPLE_FLAC);
        assertTrue(info.minBlocksize() >= FlacFormat.FLAC__MIN_BLOCK_SIZE);
        assertTrue(info.maxBlocksize() <= FlacFormat.FLAC__MAX_BLOCK_SIZE);
    }

    // -----------------------------------------------------------------------
    // Level 1 – SimpleIterator
    // -----------------------------------------------------------------------

    @Test
    void simpleIterator_firstBlock_isStreamInfo() {
        assumeIntegration();
        try (var it = new FlacMetadata.SimpleIterator(SAMPLE_FLAC, true)) {
            assertEquals(FlacFormat.MetadataType.STREAMINFO, it.getBlockType());
        }
    }

    @Test
    void simpleIterator_traversesAtLeastOneBlock() {
        assumeIntegration();
        int count = 0;
        try (var it = new FlacMetadata.SimpleIterator(SAMPLE_FLAC, true)) {
            do { count++; } while (it.next());
        }
        assertTrue(count >= 1, "Expected at least one metadata block");
    }

    @Test
    void simpleIterator_blockOffsetIsNonNegative() {
        assumeIntegration();
        try (var it = new FlacMetadata.SimpleIterator(SAMPLE_FLAC, true)) {
            do {
                assertTrue(it.getBlockOffset() >= 0);
                assertTrue(it.getBlockLength() > 0);
            } while (it.next());
        }
    }

    @Test
    void simpleIterator_close_isIdempotent() {
        assumeIntegration();
        var it = new FlacMetadata.SimpleIterator(SAMPLE_FLAC, true);
        it.close();
        it.close(); // must not throw
    }

    @Test
    void simpleIterator_methodsThrow_afterClose() {
        assumeIntegration();
        var it = new FlacMetadata.SimpleIterator(SAMPLE_FLAC, true);
        it.close();
        assertThrows(IllegalStateException.class, it::getBlockType);
    }

    @Test
    void simpleIterator_nonExistentFile_throws() {
        assumeIntegration();
        assertThrows(FlacException.FlacMetadataException.class,
                () -> new FlacMetadata.SimpleIterator(Path.of("/nonexistent.flac"), true));
    }

    // -----------------------------------------------------------------------
    // Level 2 – Chain
    // -----------------------------------------------------------------------

    @Test
    void chain_read_succeeds() {
        assumeIntegration();
        try (var chain = new FlacMetadata.Chain()) {
            assertDoesNotThrow(() -> chain.read(SAMPLE_FLAC));
        }
    }

    @Test
    void chain_read_nonExistentFile_throws() {
        assumeIntegration();
        try (var chain = new FlacMetadata.Chain()) {
            assertThrows(FlacException.FlacMetadataException.class,
                    () -> chain.read(Path.of("/nonexistent.flac")));
        }
    }

    @Test
    void chainIterator_firstBlock_isStreamInfo() {
        assumeIntegration();
        try (var chain = new FlacMetadata.Chain()) {
            chain.read(SAMPLE_FLAC);
            try (var it = chain.iterator()) {
                assertEquals(FlacFormat.MetadataType.STREAMINFO, it.getBlockType());
            }
        }
    }

    @Test
    void chainIterator_traversesAllBlocks() {
        assumeIntegration();
        int count = 0;
        try (var chain = new FlacMetadata.Chain()) {
            chain.read(SAMPLE_FLAC);
            try (var it = chain.iterator()) {
                do { count++; } while (it.next());
            }
        }
        assertTrue(count >= 1);
    }

    @Test
    void chain_close_isIdempotent() {
        assumeIntegration();
        var chain = new FlacMetadata.Chain();
        chain.close();
        chain.close(); // must not throw
    }

    @Test
    void chainIterator_close_isIdempotent() {
        assumeIntegration();
        try (var chain = new FlacMetadata.Chain()) {
            chain.read(SAMPLE_FLAC);
            var it = chain.iterator();
            it.close();
            it.close(); // must not throw
        }
    }

    @Test
    void chain_methodsThrow_afterClose() {
        assumeIntegration();
        var chain = new FlacMetadata.Chain();
        chain.close();
        assertThrows(IllegalStateException.class, () -> chain.read(SAMPLE_FLAC));
        assertThrows(IllegalStateException.class, chain::iterator);
    }

    // -----------------------------------------------------------------------
    // Object lifecycle
    // -----------------------------------------------------------------------

    @Test
    void newObject_padding_notNull() {
        assumeIntegration();
        var obj = FlacMetadata.newObject(FlacFormat.MetadataType.PADDING);
        assertNotNull(obj);
        assertFalse(obj.equals(java.lang.foreign.MemorySegment.NULL));
        FlacMetadata.objectDelete(obj);
    }

    @Test
    void cloneObject_producesNonNullCopy() {
        assumeIntegration();
        var original = FlacMetadata.newObject(FlacFormat.MetadataType.PADDING);
        var clone    = FlacMetadata.cloneObject(original);
        assertNotNull(clone);
        assertFalse(clone.equals(java.lang.foreign.MemorySegment.NULL));
        FlacMetadata.objectDelete(original);
        FlacMetadata.objectDelete(clone);
    }
}
