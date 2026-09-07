package tech.kayys.suling;

import org.junit.jupiter.api.Test;
import tech.kayys.suling.format.FlacFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sanity tests for {@link FlacFormat} constants — no native dependency.
 */
class FlacFormatConstantsTest {

    @Test
    void versionConstants() {
        assertEquals(1, FlacFormat.VERSION_MAJOR);
        assertTrue(FlacFormat.VERSION_MINOR >= 4,
                "Expected libFLAC minor >= 4, got " + FlacFormat.VERSION_MINOR);
    }

    @Test
    void blockSizeLimits() {
        assertEquals(16,    FlacFormat.FLAC__MIN_BLOCK_SIZE);
        assertEquals(65535, FlacFormat.FLAC__MAX_BLOCK_SIZE);
        assertTrue(FlacFormat.FLAC__MIN_BLOCK_SIZE < FlacFormat.FLAC__MAX_BLOCK_SIZE);
    }

    @Test
    void channelLimits() {
        assertEquals(8, FlacFormat.FLAC__MAX_CHANNELS);
    }

    @Test
    void bitsPerSampleLimits() {
        assertEquals(4,  FlacFormat.FLAC__MIN_BITS_PER_SAMPLE);
        assertEquals(32, FlacFormat.FLAC__MAX_BITS_PER_SAMPLE);
    }

    @Test
    void sampleRateLimit() {
        assertEquals(1_048_575, FlacFormat.FLAC__MAX_SAMPLE_RATE);
    }

    @Test
    void metadataTypeOrdinals() {
        assertEquals(0, FlacFormat.MetadataType.STREAMINFO);
        assertEquals(1, FlacFormat.MetadataType.PADDING);
        assertEquals(2, FlacFormat.MetadataType.APPLICATION);
        assertEquals(3, FlacFormat.MetadataType.SEEKTABLE);
        assertEquals(4, FlacFormat.MetadataType.VORBIS_COMMENT);
        assertEquals(5, FlacFormat.MetadataType.CUESHEET);
        assertEquals(6, FlacFormat.MetadataType.PICTURE);
    }

    @Test
    void channelAssignmentOrdinals() {
        assertEquals(0, FlacFormat.ChannelAssignment.INDEPENDENT);
        assertEquals(1, FlacFormat.ChannelAssignment.LEFT_SIDE);
        assertEquals(2, FlacFormat.ChannelAssignment.RIGHT_SIDE);
        assertEquals(3, FlacFormat.ChannelAssignment.MID_SIDE);
    }

    @Test
    void pictureTypeOrdinals() {
        assertEquals(0, FlacFormat.PictureType.OTHER);
        assertEquals(3, FlacFormat.PictureType.FRONT_COVER);
        assertEquals(4, FlacFormat.PictureType.BACK_COVER);
    }

    @Test
    void seekPointPlaceholder() {
        assertEquals(-1L, FlacFormat.FLAC__STREAM_METADATA_SEEKPOINT_PLACEHOLDER);
        // Must equal 0xFFFFFFFFFFFFFFFFL when interpreted as unsigned
        assertEquals(0xFFFFFFFFFFFFFFFFL, Long.toUnsignedString(
                FlacFormat.FLAC__STREAM_METADATA_SEEKPOINT_PLACEHOLDER).length() > 0
                ? FlacFormat.FLAC__STREAM_METADATA_SEEKPOINT_PLACEHOLDER : -2L);
    }

    @Test
    void streamInfoLayoutSize() {
        // min_blocksize(4) + max_blocksize(4) + min_framesize(4) + max_framesize(4)
        // + sample_rate(4) + channels(4) + bits_per_sample(4) + pad(4)
        // + total_samples(8) + md5sum(16) = 56 bytes
        assertEquals(56, FlacFormat.STREAM_INFO_LAYOUT.byteSize());
    }

    @Test
    void frameHeaderLayoutHasExpectedFields() {
        assertDoesNotThrow(() ->
                FlacFormat.FRAME_HEADER_LAYOUT.varHandle(
                        java.lang.foreign.MemoryLayout.PathElement.groupElement("blocksize")));
        assertDoesNotThrow(() ->
                FlacFormat.FRAME_HEADER_LAYOUT.varHandle(
                        java.lang.foreign.MemoryLayout.PathElement.groupElement("channels")));
        assertDoesNotThrow(() ->
                FlacFormat.FRAME_HEADER_LAYOUT.varHandle(
                        java.lang.foreign.MemoryLayout.PathElement.groupElement("sample_rate")));
    }

    @Test
    void streamInfoVarHandlesAreNonNull() {
        assertNotNull(FlacFormat.STREAM_INFO_MIN_BLOCKSIZE);
        assertNotNull(FlacFormat.STREAM_INFO_MAX_BLOCKSIZE);
        assertNotNull(FlacFormat.STREAM_INFO_SAMPLE_RATE);
        assertNotNull(FlacFormat.STREAM_INFO_CHANNELS);
        assertNotNull(FlacFormat.STREAM_INFO_BITS_PER_SAMPLE);
        assertNotNull(FlacFormat.STREAM_INFO_TOTAL_SAMPLES);
    }
}
