package tech.kayys.suling.format;

import java.lang.foreign.*;
import java.lang.invoke.VarHandle;

/**
 * Java mirror of {@code FLAC/format.h}.
 * <p>
 * Contains all constants, type aliases, and struct {@link MemoryLayout}s that
 * describe the FLAC bitstream format itself (independent of encoder/decoder).
 */
public final class FlacFormat {

    private FlacFormat() {}

    // -----------------------------------------------------------------------
    // Version
    // -----------------------------------------------------------------------
    public static final int VERSION_MAJOR = 1;
    public static final int VERSION_MINOR = 5;
    public static final int VERSION_PATCH = 0;

    // -----------------------------------------------------------------------
    // Stream limits
    // -----------------------------------------------------------------------
    public static final int FLAC__MAX_METADATA_TYPE_CODE     = 126;
    public static final int FLAC__MIN_BLOCK_SIZE             = 16;
    public static final int FLAC__MAX_BLOCK_SIZE             = 65535;
    public static final int FLAC__SUBSET_MAX_BLOCK_SIZE_48000HZ = 4608;
    public static final int FLAC__MAX_CHANNELS               = 8;
    public static final int FLAC__MIN_BITS_PER_SAMPLE        = 4;
    public static final int FLAC__MAX_BITS_PER_SAMPLE        = 32;
    public static final int FLAC__REFERENCE_CODEC_MAX_BITS_PER_SAMPLE = 32;
    public static final int FLAC__MAX_SAMPLE_RATE            = 1048575; // 2^20 - 1
    public static final int FLAC__MAX_LPC_ORDER              = 32;
    public static final int FLAC__SUBSET_MAX_LPC_ORDER_48000HZ = 12;
    public static final int FLAC__MIN_QLP_COEFF_PRECISION    = 5;
    public static final int FLAC__MAX_QLP_COEFF_PRECISION    = 15;
    public static final int FLAC__MAX_FIXED_ORDER            = 4;
    public static final int FLAC__MAX_RICE_PARTITION_ORDER   = 15;
    public static final int FLAC__SUBSET_MAX_RICE_PARTITION_ORDER = 8;
    public static final long FLAC__STREAM_SYNC_LENGTH        = 4L; // bytes

    // -----------------------------------------------------------------------
    // FLAC__MetadataType enum
    // -----------------------------------------------------------------------
    /** Enum values for {@code FLAC__MetadataType}. */
    public static final class MetadataType {
        public static final int STREAMINFO     = 0;
        public static final int PADDING        = 1;
        public static final int APPLICATION    = 2;
        public static final int SEEKTABLE      = 3;
        public static final int VORBIS_COMMENT = 4;
        public static final int CUESHEET       = 5;
        public static final int PICTURE        = 6;
        public static final int UNDEFINED      = 7;
        public static final int MAX_METADATA_TYPE = FLAC__MAX_METADATA_TYPE_CODE;
        private MetadataType() {}
    }

    // -----------------------------------------------------------------------
    // FLAC__ChannelAssignment enum
    // -----------------------------------------------------------------------
    public static final class ChannelAssignment {
        public static final int INDEPENDENT   = 0;
        public static final int LEFT_SIDE     = 1;
        public static final int RIGHT_SIDE    = 2;
        public static final int MID_SIDE      = 3;
        private ChannelAssignment() {}
    }

    // -----------------------------------------------------------------------
    // FLAC__FrameNumberType enum
    // -----------------------------------------------------------------------
    public static final class FrameNumberType {
        public static final int FRAME_NUMBER  = 0;
        public static final int SAMPLE_NUMBER = 1;
        private FrameNumberType() {}
    }

    // -----------------------------------------------------------------------
    // FLAC__SubframeType enum
    // -----------------------------------------------------------------------
    public static final class SubframeType {
        public static final int CONSTANT  = 0;
        public static final int VERBATIM  = 1;
        public static final int FIXED     = 2;
        public static final int LPC       = 3;
        private SubframeType() {}
    }

    // -----------------------------------------------------------------------
    // FLAC__EntropyCodingMethodType enum
    // -----------------------------------------------------------------------
    public static final class EntropyCodingMethodType {
        public static final int PARTITIONED_RICE  = 0;
        public static final int PARTITIONED_RICE2 = 1;
        private EntropyCodingMethodType() {}
    }

    // -----------------------------------------------------------------------
    // FLAC__StreamMetadata_Picture_Type enum
    // -----------------------------------------------------------------------
    public static final class PictureType {
        public static final int OTHER                = 0;
        public static final int FILE_ICON_STANDARD   = 1;
        public static final int FILE_ICON            = 2;
        public static final int FRONT_COVER          = 3;
        public static final int BACK_COVER           = 4;
        public static final int LEAFLET_PAGE         = 5;
        public static final int MEDIA                = 6;
        public static final int LEAD_ARTIST          = 7;
        public static final int ARTIST               = 8;
        public static final int CONDUCTOR            = 9;
        public static final int BAND                 = 10;
        public static final int COMPOSER             = 11;
        public static final int LYRICIST             = 12;
        public static final int RECORDING_LOCATION   = 13;
        public static final int DURING_RECORDING     = 14;
        public static final int DURING_PERFORMANCE   = 15;
        public static final int VIDEO_SCREEN_CAPTURE = 16;
        public static final int FISH                 = 17;
        public static final int ILLUSTRATION         = 18;
        public static final int BAND_LOGO            = 19;
        public static final int PUBLISHER_LOGO       = 20;
        public static final int UNDEFINED            = 21;
        private PictureType() {}
    }

    // -----------------------------------------------------------------------
    // Struct MemoryLayouts (used by both encoder and decoder callback code)
    // -----------------------------------------------------------------------

    /**
     * Layout of {@code FLAC__StreamMetadata_StreamInfo}.
     * <pre>
     *   uint32_t  min_blocksize
     *   uint32_t  max_blocksize
     *   uint32_t  min_framesize
     *   uint32_t  max_framesize
     *   uint32_t  sample_rate
     *   uint32_t  channels
     *   uint32_t  bits_per_sample
     *   uint64_t  total_samples
     *   uint8_t   md5sum[16]
     * </pre>
     */
    public static final StructLayout STREAM_INFO_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("min_blocksize"),
            ValueLayout.JAVA_INT.withName("max_blocksize"),
            ValueLayout.JAVA_INT.withName("min_framesize"),
            ValueLayout.JAVA_INT.withName("max_framesize"),
            ValueLayout.JAVA_INT.withName("sample_rate"),
            ValueLayout.JAVA_INT.withName("channels"),
            ValueLayout.JAVA_INT.withName("bits_per_sample"),
            MemoryLayout.paddingLayout(4), // alignment pad before uint64
            ValueLayout.JAVA_LONG.withName("total_samples"),
            MemoryLayout.sequenceLayout(16, ValueLayout.JAVA_BYTE).withName("md5sum")
    ).withName("FLAC__StreamMetadata_StreamInfo");

    /**
     * Layout of {@code FLAC__StreamMetadata_SeekPoint}.
     * <pre>
     *   uint64_t  sample_number
     *   uint64_t  stream_offset
     *   uint32_t  frame_samples
     * </pre>
     */
    public static final StructLayout SEEK_POINT_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_LONG.withName("sample_number"),
            ValueLayout.JAVA_LONG.withName("stream_offset"),
            ValueLayout.JAVA_INT.withName("frame_samples"),
            MemoryLayout.paddingLayout(4)
    ).withName("FLAC__StreamMetadata_SeekPoint");

    /**
     * Sentinel value for a placeholder seek point (sample_number == 0xFFFFFFFFFFFFFFFF).
     */
    public static final long FLAC__STREAM_METADATA_SEEKPOINT_PLACEHOLDER = -1L; // == 0xFFFFFFFFFFFFFFFFL

    /**
     * Layout of {@code FLAC__FrameHeader}.
     */
    public static final StructLayout FRAME_HEADER_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("blocksize"),
            ValueLayout.JAVA_INT.withName("sample_rate"),
            ValueLayout.JAVA_INT.withName("channels"),
            ValueLayout.JAVA_INT.withName("channel_assignment"),
            ValueLayout.JAVA_INT.withName("bits_per_sample"),
            ValueLayout.JAVA_INT.withName("number_type"),
            // union { uint32_t frame_number; uint64_t sample_number; }
            // Use 8 bytes (the larger)
            ValueLayout.JAVA_LONG.withName("number"),
            ValueLayout.JAVA_BYTE.withName("crc"),
            MemoryLayout.paddingLayout(7)
    ).withName("FLAC__FrameHeader");

    /**
     * Layout of {@code FLAC__FrameFooter}.
     */
    public static final StructLayout FRAME_FOOTER_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_SHORT.withName("crc"),
            MemoryLayout.paddingLayout(6)
    ).withName("FLAC__FrameFooter");

    // VarHandle helpers for STREAM_INFO_LAYOUT
    public static final VarHandle STREAM_INFO_MIN_BLOCKSIZE =
            STREAM_INFO_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("min_blocksize"));
    public static final VarHandle STREAM_INFO_MAX_BLOCKSIZE =
            STREAM_INFO_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("max_blocksize"));
    public static final VarHandle STREAM_INFO_SAMPLE_RATE =
            STREAM_INFO_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("sample_rate"));
    public static final VarHandle STREAM_INFO_CHANNELS =
            STREAM_INFO_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("channels"));
    public static final VarHandle STREAM_INFO_BITS_PER_SAMPLE =
            STREAM_INFO_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("bits_per_sample"));
    public static final VarHandle STREAM_INFO_TOTAL_SAMPLES =
            STREAM_INFO_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("total_samples"));

    // VarHandle helpers for FRAME_HEADER_LAYOUT
    public static final VarHandle FRAME_HEADER_BLOCKSIZE =
            FRAME_HEADER_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("blocksize"));
    public static final VarHandle FRAME_HEADER_SAMPLE_RATE =
            FRAME_HEADER_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("sample_rate"));
    public static final VarHandle FRAME_HEADER_CHANNELS =
            FRAME_HEADER_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("channels"));
    public static final VarHandle FRAME_HEADER_CHANNEL_ASSIGNMENT =
            FRAME_HEADER_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("channel_assignment"));
    public static final VarHandle FRAME_HEADER_BITS_PER_SAMPLE =
            FRAME_HEADER_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("bits_per_sample"));
    public static final VarHandle FRAME_HEADER_NUMBER_TYPE =
            FRAME_HEADER_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("number_type"));
    public static final VarHandle FRAME_HEADER_NUMBER =
            FRAME_HEADER_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("number"));
}
