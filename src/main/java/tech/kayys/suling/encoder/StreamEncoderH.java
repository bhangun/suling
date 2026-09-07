package tech.kayys.suling.encoder;

import tech.kayys.suling.internal.FlacLibrary;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.*;

/**
 * Raw FFM downcall handles for every symbol in {@code FLAC/stream_encoder.h}.
 * Prefer {@link FlacStreamEncoder} for a safe Java wrapper.
 *
 * @since 1.5.0
 */
public final class StreamEncoderH {

    private StreamEncoderH() {}

    // -----------------------------------------------------------------------
    // FLAC__StreamEncoderState enum values
    // -----------------------------------------------------------------------
    public static final int STATE_OK                       = 0;
    public static final int STATE_UNINITIALIZED            = 1;
    public static final int STATE_OGG_ERROR                = 2;
    public static final int STATE_VERIFY_DECODER_ERROR     = 3;
    public static final int STATE_VERIFY_MISMATCH_IN_AUDIO_DATA = 4;
    public static final int STATE_CLIENT_ERROR             = 5;
    public static final int STATE_IO_ERROR                 = 6;
    public static final int STATE_FRAMING_ERROR            = 7;
    public static final int STATE_MEMORY_ALLOCATION_ERROR  = 8;

    // -----------------------------------------------------------------------
    // FLAC__StreamEncoderInitStatus enum values
    // -----------------------------------------------------------------------
    public static final int INIT_STATUS_OK                             = 0;
    public static final int INIT_STATUS_ENCODER_ERROR                  = 1;
    public static final int INIT_STATUS_UNSUPPORTED_CONTAINER          = 2;
    public static final int INIT_STATUS_INVALID_CALLBACKS              = 3;
    public static final int INIT_STATUS_INVALID_NUMBER_OF_CHANNELS     = 4;
    public static final int INIT_STATUS_INVALID_BITS_PER_SAMPLE        = 5;
    public static final int INIT_STATUS_INVALID_SAMPLE_RATE            = 6;
    public static final int INIT_STATUS_INVALID_BLOCK_SIZE             = 7;
    public static final int INIT_STATUS_INVALID_MAX_LPC_ORDER          = 8;
    public static final int INIT_STATUS_INVALID_QLP_COEFF_PRECISION    = 9;
    public static final int INIT_STATUS_BLOCK_SIZE_TOO_SMALL_FOR_LPC_ORDER = 10;
    public static final int INIT_STATUS_NOT_STREAMABLE                 = 11;
    public static final int INIT_STATUS_INVALID_METADATA               = 12;
    public static final int INIT_STATUS_ALREADY_INITIALIZED            = 13;

    // -----------------------------------------------------------------------
    // FLAC__StreamEncoderReadStatus enum values
    // -----------------------------------------------------------------------
    public static final int READ_STATUS_CONTINUE       = 0;
    public static final int READ_STATUS_END_OF_STREAM  = 1;
    public static final int READ_STATUS_ABORT          = 2;
    public static final int READ_STATUS_UNSUPPORTED    = 3;

    // -----------------------------------------------------------------------
    // FLAC__StreamEncoderWriteStatus enum values
    // -----------------------------------------------------------------------
    public static final int WRITE_STATUS_OK    = 0;
    public static final int WRITE_STATUS_FATAL_ERROR = 1;

    // -----------------------------------------------------------------------
    // FLAC__StreamEncoderSeekStatus enum values
    // -----------------------------------------------------------------------
    public static final int SEEK_STATUS_OK          = 0;
    public static final int SEEK_STATUS_ERROR       = 1;
    public static final int SEEK_STATUS_UNSUPPORTED = 2;

    // -----------------------------------------------------------------------
    // FLAC__StreamEncoderTellStatus enum values
    // -----------------------------------------------------------------------
    public static final int TELL_STATUS_OK          = 0;
    public static final int TELL_STATUS_ERROR       = 1;
    public static final int TELL_STATUS_UNSUPPORTED = 2;

    // -----------------------------------------------------------------------
    // Method handles – lifecycle
    // -----------------------------------------------------------------------
    private static final MethodHandle MH_new = FlacLibrary.downcall(
            "FLAC__stream_encoder_new", FunctionDescriptor.of(ADDRESS));

    private static final MethodHandle MH_delete = FlacLibrary.downcall(
            "FLAC__stream_encoder_delete", FunctionDescriptor.ofVoid(ADDRESS));

    // -----------------------------------------------------------------------
    // Setters
    // -----------------------------------------------------------------------
    private static final MethodHandle MH_set_verify = FlacLibrary.downcall(
            "FLAC__stream_encoder_set_verify", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    private static final MethodHandle MH_set_streamable_subset = FlacLibrary.downcall(
            "FLAC__stream_encoder_set_streamable_subset", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    private static final MethodHandle MH_set_channels = FlacLibrary.downcall(
            "FLAC__stream_encoder_set_channels", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    private static final MethodHandle MH_set_bits_per_sample = FlacLibrary.downcall(
            "FLAC__stream_encoder_set_bits_per_sample", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    private static final MethodHandle MH_set_sample_rate = FlacLibrary.downcall(
            "FLAC__stream_encoder_set_sample_rate", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    private static final MethodHandle MH_set_compression_level = FlacLibrary.downcall(
            "FLAC__stream_encoder_set_compression_level", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    private static final MethodHandle MH_set_blocksize = FlacLibrary.downcall(
            "FLAC__stream_encoder_set_blocksize", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    private static final MethodHandle MH_set_do_mid_side_stereo = FlacLibrary.downcall(
            "FLAC__stream_encoder_set_do_mid_side_stereo", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    private static final MethodHandle MH_set_loose_mid_side_stereo = FlacLibrary.downcall(
            "FLAC__stream_encoder_set_loose_mid_side_stereo", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    private static final MethodHandle MH_set_apodization = FlacLibrary.downcall(
            "FLAC__stream_encoder_set_apodization",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS)); // (encoder, const char*)

    private static final MethodHandle MH_set_max_lpc_order = FlacLibrary.downcall(
            "FLAC__stream_encoder_set_max_lpc_order", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    private static final MethodHandle MH_set_qlp_coeff_precision = FlacLibrary.downcall(
            "FLAC__stream_encoder_set_qlp_coeff_precision", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    private static final MethodHandle MH_set_do_qlp_coeff_prec_search = FlacLibrary.downcall(
            "FLAC__stream_encoder_set_do_qlp_coeff_prec_search", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    private static final MethodHandle MH_set_do_escape_coding = FlacLibrary.downcall(
            "FLAC__stream_encoder_set_do_escape_coding", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    private static final MethodHandle MH_set_do_exhaustive_model_search = FlacLibrary.downcall(
            "FLAC__stream_encoder_set_do_exhaustive_model_search", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    private static final MethodHandle MH_set_min_residual_partition_order = FlacLibrary.downcall(
            "FLAC__stream_encoder_set_min_residual_partition_order", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    private static final MethodHandle MH_set_max_residual_partition_order = FlacLibrary.downcall(
            "FLAC__stream_encoder_set_max_residual_partition_order", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    private static final MethodHandle MH_set_rice_parameter_search_dist = FlacLibrary.downcall(
            "FLAC__stream_encoder_set_rice_parameter_search_dist", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    private static final MethodHandle MH_set_total_samples_estimate = FlacLibrary.downcall(
            "FLAC__stream_encoder_set_total_samples_estimate", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG));

    private static final MethodHandle MH_set_metadata = FlacLibrary.downcall(
            "FLAC__stream_encoder_set_metadata",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));  // encoder, FLAC__StreamMetadata**, uint32_t

    private static final MethodHandle MH_set_limit_min_bitrate = FlacLibrary.downcall(
            "FLAC__stream_encoder_set_limit_min_bitrate", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------
    private static final MethodHandle MH_get_state = FlacLibrary.downcall(
            "FLAC__stream_encoder_get_state", FunctionDescriptor.of(JAVA_INT, ADDRESS));

    private static final MethodHandle MH_get_verify_decoder_state = FlacLibrary.downcall(
            "FLAC__stream_encoder_get_verify_decoder_state", FunctionDescriptor.of(JAVA_INT, ADDRESS));

    private static final MethodHandle MH_get_resolved_state_string = FlacLibrary.downcall(
            "FLAC__stream_encoder_get_resolved_state_string", FunctionDescriptor.of(ADDRESS, ADDRESS));

    private static final MethodHandle MH_get_verify_decoder_error_stats = FlacLibrary.downcall(
            "FLAC__stream_encoder_get_verify_decoder_error_stats",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    private static final MethodHandle MH_get_verify              = FlacLibrary.downcall("FLAC__stream_encoder_get_verify",              FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle MH_get_streamable_subset   = FlacLibrary.downcall("FLAC__stream_encoder_get_streamable_subset",   FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle MH_get_channels            = FlacLibrary.downcall("FLAC__stream_encoder_get_channels",            FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle MH_get_bits_per_sample     = FlacLibrary.downcall("FLAC__stream_encoder_get_bits_per_sample",     FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle MH_get_sample_rate         = FlacLibrary.downcall("FLAC__stream_encoder_get_sample_rate",         FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle MH_get_blocksize           = FlacLibrary.downcall("FLAC__stream_encoder_get_blocksize",           FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle MH_get_do_mid_side_stereo  = FlacLibrary.downcall("FLAC__stream_encoder_get_do_mid_side_stereo",  FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle MH_get_loose_mid_side      = FlacLibrary.downcall("FLAC__stream_encoder_get_loose_mid_side_stereo",FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle MH_get_max_lpc_order       = FlacLibrary.downcall("FLAC__stream_encoder_get_max_lpc_order",       FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle MH_get_qlp_coeff_precision = FlacLibrary.downcall("FLAC__stream_encoder_get_qlp_coeff_precision", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle MH_get_do_qlp_coeff_prec_search = FlacLibrary.downcall("FLAC__stream_encoder_get_do_qlp_coeff_prec_search", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle MH_get_do_qlp_search       = FlacLibrary.downcallOpt("FLAC__stream_encoder_get_do_qlp_search", FunctionDescriptor.of(JAVA_INT, ADDRESS)).orElse(null);
    private static final MethodHandle MH_get_do_escape_coding     = FlacLibrary.downcall("FLAC__stream_encoder_get_do_escape_coding",   FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle MH_get_do_exhaustive        = FlacLibrary.downcall("FLAC__stream_encoder_get_do_exhaustive_model_search", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle MH_get_min_partition_order  = FlacLibrary.downcall("FLAC__stream_encoder_get_min_residual_partition_order", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle MH_get_max_partition_order  = FlacLibrary.downcall("FLAC__stream_encoder_get_max_residual_partition_order", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle MH_get_rice_param_search    = FlacLibrary.downcall("FLAC__stream_encoder_get_rice_parameter_search_dist", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle MH_get_total_samples_est    = FlacLibrary.downcall("FLAC__stream_encoder_get_total_samples_estimate", FunctionDescriptor.of(JAVA_LONG, ADDRESS));
    private static final MethodHandle MH_get_limit_min_bitrate    = FlacLibrary.downcall("FLAC__stream_encoder_get_limit_min_bitrate",  FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // -----------------------------------------------------------------------
    // Init methods
    // -----------------------------------------------------------------------
    private static final MethodHandle MH_init_stream = FlacLibrary.downcall(
            "FLAC__stream_encoder_init_stream",
            FunctionDescriptor.of(JAVA_INT,
                    ADDRESS, // encoder
                    ADDRESS, // write_callback
                    ADDRESS, // seek_callback (nullable)
                    ADDRESS, // tell_callback (nullable)
                    ADDRESS, // metadata_callback (nullable)
                    ADDRESS  // client_data
            ));

    private static final MethodHandle MH_init_ogg_stream = FlacLibrary.downcall(
            "FLAC__stream_encoder_init_ogg_stream",
            FunctionDescriptor.of(JAVA_INT,
                    ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    private static final MethodHandle MH_init_FILE = FlacLibrary.downcall(
            "FLAC__stream_encoder_init_FILE",
            FunctionDescriptor.of(JAVA_INT,
                    ADDRESS, // encoder
                    ADDRESS, // FILE*
                    ADDRESS, // progress_callback (nullable)
                    ADDRESS  // client_data
            ));

    private static final MethodHandle MH_init_ogg_FILE = FlacLibrary.downcall(
            "FLAC__stream_encoder_init_ogg_FILE",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    private static final MethodHandle MH_init_file = FlacLibrary.downcall(
            "FLAC__stream_encoder_init_file",
            FunctionDescriptor.of(JAVA_INT,
                    ADDRESS, // encoder
                    ADDRESS, // const char* filename
                    ADDRESS, // progress_callback (nullable)
                    ADDRESS  // client_data
            ));

    private static final MethodHandle MH_init_ogg_file = FlacLibrary.downcall(
            "FLAC__stream_encoder_init_ogg_file",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    // -----------------------------------------------------------------------
    // Encode / finish
    // -----------------------------------------------------------------------
    private static final MethodHandle MH_finish = FlacLibrary.downcall(
            "FLAC__stream_encoder_finish", FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // FLAC__bool FLAC__stream_encoder_process(encoder, const FLAC__int32 * const buffer[], uint32_t samples)
    private static final MethodHandle MH_process = FlacLibrary.downcall(
            "FLAC__stream_encoder_process",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

    // FLAC__bool FLAC__stream_encoder_process_interleaved(encoder, const FLAC__int32 buffer[], uint32_t samples)
    private static final MethodHandle MH_process_interleaved = FlacLibrary.downcall(
            "FLAC__stream_encoder_process_interleaved",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

    // -----------------------------------------------------------------------
    // Callback descriptors
    // -----------------------------------------------------------------------
    /** write_callback: (encoder*, byte[], size_t, uint32, uint32, clientData*) -> WriteStatus */
    public static final FunctionDescriptor FD_WRITE_CALLBACK =
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, ADDRESS);

    /** seek_callback: (encoder*, uint64, clientData*) -> SeekStatus */
    public static final FunctionDescriptor FD_SEEK_CALLBACK =
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS);

    /** tell_callback: (encoder*, uint64*, clientData*) -> TellStatus */
    public static final FunctionDescriptor FD_TELL_CALLBACK =
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS);

    /** metadata_callback: (encoder*, metadata*, clientData*) -> void */
    public static final FunctionDescriptor FD_METADATA_CALLBACK =
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS);

    /** read_callback for Ogg: (encoder*, byte[], size_t*, clientData*) -> ReadStatus */
    public static final FunctionDescriptor FD_READ_CALLBACK =
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS);

    /** progress_callback: (encoder*, uint64 bytes_written, uint64 samples_written,
     *                      uint32 frames_written, uint32 total_frames_estimate, clientData*) -> void */
    public static final FunctionDescriptor FD_PROGRESS_CALLBACK =
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, ADDRESS);

    // -----------------------------------------------------------------------
    // Public static invocation methods
    // -----------------------------------------------------------------------

    public static MemorySegment newEncoder() {
        try { return (MemorySegment) MH_new.invokeExact(); }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static void deleteEncoder(MemorySegment e) {
        try { MH_delete.invokeExact(e); }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    // -- Setters --
    private static boolean setBool(MethodHandle mh, MemorySegment e, boolean v) {
        try { return (int) mh.invokeExact(e, v ? 1 : 0) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    private static boolean setInt(MethodHandle mh, MemorySegment e, int v) {
        try { return (int) mh.invokeExact(e, v) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean setVerify(MemorySegment e, boolean v)                  { return setBool(MH_set_verify, e, v); }
    public static boolean setStreamableSubset(MemorySegment e, boolean v)        { return setBool(MH_set_streamable_subset, e, v); }
    public static boolean setChannels(MemorySegment e, int v)                    { return setInt(MH_set_channels, e, v); }
    public static boolean setBitsPerSample(MemorySegment e, int v)               { return setInt(MH_set_bits_per_sample, e, v); }
    public static boolean setSampleRate(MemorySegment e, int v)                  { return setInt(MH_set_sample_rate, e, v); }
    public static boolean setCompressionLevel(MemorySegment e, int v)            { return setInt(MH_set_compression_level, e, v); }
    public static boolean setBlocksize(MemorySegment e, int v)                   { return setInt(MH_set_blocksize, e, v); }
    public static boolean setDoMidSideStereo(MemorySegment e, boolean v)         { return setBool(MH_set_do_mid_side_stereo, e, v); }
    public static boolean setLooseMidSideStereo(MemorySegment e, boolean v)      { return setBool(MH_set_loose_mid_side_stereo, e, v); }
    public static boolean setApodization(MemorySegment e, MemorySegment cStr)    {
        try { return (int) MH_set_apodization.invokeExact(e, cStr) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean setMaxLpcOrder(MemorySegment e, int v)                 { return setInt(MH_set_max_lpc_order, e, v); }
    public static boolean setQlpCoeffPrecision(MemorySegment e, int v)           { return setInt(MH_set_qlp_coeff_precision, e, v); }
    public static boolean setDoQlpCoeffPrecSearch(MemorySegment e, boolean v)    { return setBool(MH_set_do_qlp_coeff_prec_search, e, v); }
    public static boolean setDoEscapeCoding(MemorySegment e, boolean v)          { return setBool(MH_set_do_escape_coding, e, v); }
    public static boolean setDoExhaustiveModelSearch(MemorySegment e, boolean v) { return setBool(MH_set_do_exhaustive_model_search, e, v); }
    public static boolean setMinResidualPartitionOrder(MemorySegment e, int v)   { return setInt(MH_set_min_residual_partition_order, e, v); }
    public static boolean setMaxResidualPartitionOrder(MemorySegment e, int v)   { return setInt(MH_set_max_residual_partition_order, e, v); }
    public static boolean setRiceParameterSearchDist(MemorySegment e, int v)     { return setInt(MH_set_rice_parameter_search_dist, e, v); }
    public static boolean setTotalSamplesEstimate(MemorySegment e, long v) {
        try { return (int) MH_set_total_samples_estimate.invokeExact(e, v) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean setMetadata(MemorySegment e, MemorySegment metaArray, int count) {
        try { return (int) MH_set_metadata.invokeExact(e, metaArray, count) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean setLimitMinBitrate(MemorySegment e, boolean v)         { return setBool(MH_set_limit_min_bitrate, e, v); }

    // -- Getters --
    private static int getInt(MethodHandle mh, MemorySegment e) {
        try { return (int) mh.invokeExact(e); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static int    getState(MemorySegment e)               { return getInt(MH_get_state, e); }
    public static int    getVerifyDecoderState(MemorySegment e)  { return getInt(MH_get_verify_decoder_state, e); }
    public static String getResolvedStateString(MemorySegment e) {
        try { MemorySegment p = (MemorySegment) MH_get_resolved_state_string.invokeExact(e); return p.getString(0); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean getVerify(MemorySegment e)             { return getInt(MH_get_verify, e) != 0; }
    public static boolean getStreamableSubset(MemorySegment e)   { return getInt(MH_get_streamable_subset, e) != 0; }
    public static int     getChannels(MemorySegment e)           { return getInt(MH_get_channels, e); }
    public static int     getBitsPerSample(MemorySegment e)      { return getInt(MH_get_bits_per_sample, e); }
    public static int     getSampleRate(MemorySegment e)         { return getInt(MH_get_sample_rate, e); }
    public static int     getBlocksize(MemorySegment e)          { return getInt(MH_get_blocksize, e); }
    public static boolean getDoMidSideStereo(MemorySegment e)    { return getInt(MH_get_do_mid_side_stereo, e) != 0; }
    public static boolean getLooseMidSideStereo(MemorySegment e) { return getInt(MH_get_loose_mid_side, e) != 0; }
    public static int     getMaxLpcOrder(MemorySegment e)        { return getInt(MH_get_max_lpc_order, e); }
    public static int     getQlpCoeffPrecision(MemorySegment e)  { return getInt(MH_get_qlp_coeff_precision, e); }
    public static boolean getDoQlpCoeffPrecSearch(MemorySegment e) { return getInt(MH_get_do_qlp_coeff_prec_search, e) != 0; }
    public static boolean getDoQlpSearch(MemorySegment e)        { return MH_get_do_qlp_search != null && getInt(MH_get_do_qlp_search, e) != 0; }
    public static boolean getDoEscapeCoding(MemorySegment e)     { return getInt(MH_get_do_escape_coding, e) != 0; }
    public static boolean getDoExhaustiveModelSearch(MemorySegment e){ return getInt(MH_get_do_exhaustive, e) != 0; }
    public static int     getMinPartitionOrder(MemorySegment e)  { return getInt(MH_get_min_partition_order, e); }
    public static int     getMaxPartitionOrder(MemorySegment e)  { return getInt(MH_get_max_partition_order, e); }
    public static int     getRiceParamSearchDist(MemorySegment e){ return getInt(MH_get_rice_param_search, e); }
    public static long    getTotalSamplesEstimate(MemorySegment e){
        try { return (long) MH_get_total_samples_est.invokeExact(e); }
        catch (Throwable t) { throw new AssertionError(t); }
    }
    public static boolean getLimitMinBitrate(MemorySegment e)    { return getInt(MH_get_limit_min_bitrate, e) != 0; }

    // -- Init --
    public static int initStream(MemorySegment e,
            MemorySegment writeCb, MemorySegment seekCb, MemorySegment tellCb,
            MemorySegment metaCb, MemorySegment clientData) {
        try { return (int) MH_init_stream.invokeExact(e, writeCb, seekCb, tellCb, metaCb, clientData); }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static int initOggStream(MemorySegment e,
            MemorySegment readCb, MemorySegment writeCb, MemorySegment seekCb,
            MemorySegment tellCb, MemorySegment metaCb, MemorySegment clientData) {
        try { return (int) MH_init_ogg_stream.invokeExact(e, readCb, writeCb, seekCb, tellCb, metaCb, clientData); }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static int initFile(MemorySegment e, MemorySegment filename,
            MemorySegment progressCb, MemorySegment clientData) {
        try { return (int) MH_init_file.invokeExact(e, filename, progressCb, clientData); }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static int initOggFile(MemorySegment e, MemorySegment filename,
            MemorySegment progressCb, MemorySegment clientData) {
        try { return (int) MH_init_ogg_file.invokeExact(e, filename, progressCb, clientData); }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static int initFILE(MemorySegment e, MemorySegment file,
            MemorySegment progressCb, MemorySegment clientData) {
        try { return (int) MH_init_FILE.invokeExact(e, file, progressCb, clientData); }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    // -- Process --
    public static boolean finish(MemorySegment e) {
        try { return (int) MH_finish.invokeExact(e) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean process(MemorySegment e, MemorySegment bufferPtrs, int samples) {
        try { return (int) MH_process.invokeExact(e, bufferPtrs, samples) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean processInterleaved(MemorySegment e, MemorySegment interleaved, int samples) {
        try { return (int) MH_process_interleaved.invokeExact(e, interleaved, samples) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }
}
