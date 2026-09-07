package tech.kayys.suling.decoder;

import tech.kayys.suling.internal.FlacLibrary;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.*;

/**
 * Raw FFM downcall handles for every symbol in {@code FLAC/stream_decoder.h}.
 * <p>
 * This class is intentionally low-level; prefer {@link FlacStreamDecoder} for
 * a safe, callback-friendly Java wrapper.
 *
 * @since 1.5.0
 */
public final class StreamDecoderH {

    private StreamDecoderH() {}

    // -----------------------------------------------------------------------
    // FLAC__StreamDecoderState enum values
    // -----------------------------------------------------------------------
    public static final int STATE_SEARCH_FOR_METADATA          = 0;
    public static final int STATE_READ_METADATA                 = 1;
    public static final int STATE_SEARCH_FOR_FRAME_SYNC        = 2;
    public static final int STATE_READ_FRAME                   = 3;
    public static final int STATE_END_OF_STREAM                = 4;
    public static final int STATE_OGG_ERROR                    = 5;
    public static final int STATE_SEEK_ERROR                   = 6;
    public static final int STATE_ABORTED                      = 7;
    public static final int STATE_MEMORY_ALLOCATION_ERROR      = 8;
    public static final int STATE_UNINITIALIZED                = 9;

    // -----------------------------------------------------------------------
    // FLAC__StreamDecoderInitStatus enum values
    // -----------------------------------------------------------------------
    public static final int INIT_STATUS_OK                                  = 0;
    public static final int INIT_STATUS_UNSUPPORTED_CONTAINER               = 1;
    public static final int INIT_STATUS_INVALID_CALLBACKS                   = 2;
    public static final int INIT_STATUS_MEMORY_ALLOCATION_ERROR             = 3;
    public static final int INIT_STATUS_ERROR_OPENING_FILE                  = 4;
    public static final int INIT_STATUS_ALREADY_INITIALIZED                 = 5;

    // -----------------------------------------------------------------------
    // FLAC__StreamDecoderReadStatus enum values
    // -----------------------------------------------------------------------
    public static final int READ_STATUS_CONTINUE       = 0;
    public static final int READ_STATUS_END_OF_STREAM  = 1;
    public static final int READ_STATUS_ABORT          = 2;

    // -----------------------------------------------------------------------
    // FLAC__StreamDecoderSeekStatus enum values
    // -----------------------------------------------------------------------
    public static final int SEEK_STATUS_OK             = 0;
    public static final int SEEK_STATUS_ERROR          = 1;
    public static final int SEEK_STATUS_UNSUPPORTED    = 2;

    // -----------------------------------------------------------------------
    // FLAC__StreamDecoderTellStatus enum values
    // -----------------------------------------------------------------------
    public static final int TELL_STATUS_OK             = 0;
    public static final int TELL_STATUS_ERROR          = 1;
    public static final int TELL_STATUS_UNSUPPORTED    = 2;

    // -----------------------------------------------------------------------
    // FLAC__StreamDecoderLengthStatus enum values
    // -----------------------------------------------------------------------
    public static final int LENGTH_STATUS_OK           = 0;
    public static final int LENGTH_STATUS_ERROR        = 1;
    public static final int LENGTH_STATUS_UNSUPPORTED  = 2;

    // -----------------------------------------------------------------------
    // FLAC__StreamDecoderWriteStatus enum values
    // -----------------------------------------------------------------------
    public static final int WRITE_STATUS_CONTINUE  = 0;
    public static final int WRITE_STATUS_ABORT     = 1;

    // -----------------------------------------------------------------------
    // FLAC__StreamDecoderErrorStatus enum values
    // -----------------------------------------------------------------------
    public static final int ERROR_STATUS_LOST_SYNC             = 0;
    public static final int ERROR_STATUS_BAD_HEADER            = 1;
    public static final int ERROR_STATUS_FRAME_CRC_MISMATCH    = 2;
    public static final int ERROR_STATUS_UNPARSEABLE_STREAM    = 3;
    public static final int ERROR_STATUS_BAD_METADATA          = 4;

    // -----------------------------------------------------------------------
    // Function descriptors & method handles
    // -----------------------------------------------------------------------

    // FLAC__StreamDecoder *FLAC__stream_decoder_new(void)
    private static final MethodHandle MH_new = FlacLibrary.downcall(
            "FLAC__stream_decoder_new",
            FunctionDescriptor.of(ADDRESS));

    // void FLAC__stream_decoder_delete(FLAC__StreamDecoder *)
    private static final MethodHandle MH_delete = FlacLibrary.downcall(
            "FLAC__stream_decoder_delete",
            FunctionDescriptor.ofVoid(ADDRESS));

    // FLAC__bool FLAC__stream_decoder_set_ogg_serial_number(decoder, long)
    private static final MethodHandle MH_set_ogg_serial = FlacLibrary.downcall(
            "FLAC__stream_decoder_set_ogg_serial_number",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG));

    // FLAC__bool FLAC__stream_decoder_set_md5_checking(decoder, FLAC__bool)
    private static final MethodHandle MH_set_md5 = FlacLibrary.downcall(
            "FLAC__stream_decoder_set_md5_checking",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    // FLAC__bool FLAC__stream_decoder_set_metadata_respond(decoder, FLAC__MetadataType)
    private static final MethodHandle MH_set_metadata_respond = FlacLibrary.downcall(
            "FLAC__stream_decoder_set_metadata_respond",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    // FLAC__bool FLAC__stream_decoder_set_metadata_respond_application(decoder, id[4])
    private static final MethodHandle MH_set_metadata_respond_app = FlacLibrary.downcall(
            "FLAC__stream_decoder_set_metadata_respond_application",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    // FLAC__bool FLAC__stream_decoder_set_metadata_respond_all(decoder)
    private static final MethodHandle MH_set_metadata_respond_all = FlacLibrary.downcall(
            "FLAC__stream_decoder_set_metadata_respond_all",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // FLAC__bool FLAC__stream_decoder_set_metadata_ignore(decoder, FLAC__MetadataType)
    private static final MethodHandle MH_set_metadata_ignore = FlacLibrary.downcall(
            "FLAC__stream_decoder_set_metadata_ignore",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    // FLAC__bool FLAC__stream_decoder_set_metadata_ignore_application(decoder, id[4])
    private static final MethodHandle MH_set_metadata_ignore_app = FlacLibrary.downcall(
            "FLAC__stream_decoder_set_metadata_ignore_application",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    // FLAC__bool FLAC__stream_decoder_set_metadata_ignore_all(decoder)
    private static final MethodHandle MH_set_metadata_ignore_all = FlacLibrary.downcall(
            "FLAC__stream_decoder_set_metadata_ignore_all",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // FLAC__StreamDecoderState FLAC__stream_decoder_get_state(const decoder)
    private static final MethodHandle MH_get_state = FlacLibrary.downcall(
            "FLAC__stream_decoder_get_state",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // const char *FLAC__stream_decoder_get_resolved_state_string(decoder)
    private static final MethodHandle MH_get_state_string = FlacLibrary.downcall(
            "FLAC__stream_decoder_get_resolved_state_string",
            FunctionDescriptor.of(ADDRESS, ADDRESS));

    // FLAC__bool FLAC__stream_decoder_get_md5_checking(decoder)
    private static final MethodHandle MH_get_md5 = FlacLibrary.downcall(
            "FLAC__stream_decoder_get_md5_checking",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // FLAC__uint64 FLAC__stream_decoder_get_total_samples(decoder)
    private static final MethodHandle MH_get_total_samples = FlacLibrary.downcall(
            "FLAC__stream_decoder_get_total_samples",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS));

    // uint32_t FLAC__stream_decoder_get_channels(decoder)
    private static final MethodHandle MH_get_channels = FlacLibrary.downcall(
            "FLAC__stream_decoder_get_channels",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // FLAC__ChannelAssignment FLAC__stream_decoder_get_channel_assignment(decoder)
    private static final MethodHandle MH_get_channel_assignment = FlacLibrary.downcall(
            "FLAC__stream_decoder_get_channel_assignment",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // uint32_t FLAC__stream_decoder_get_bits_per_sample(decoder)
    private static final MethodHandle MH_get_bits_per_sample = FlacLibrary.downcall(
            "FLAC__stream_decoder_get_bits_per_sample",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // uint32_t FLAC__stream_decoder_get_sample_rate(decoder)
    private static final MethodHandle MH_get_sample_rate = FlacLibrary.downcall(
            "FLAC__stream_decoder_get_sample_rate",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // uint32_t FLAC__stream_decoder_get_blocksize(decoder)
    private static final MethodHandle MH_get_blocksize = FlacLibrary.downcall(
            "FLAC__stream_decoder_get_blocksize",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // FLAC__bool FLAC__stream_decoder_get_decode_position(decoder, uint64_t*)
    private static final MethodHandle MH_get_decode_position = FlacLibrary.downcall(
            "FLAC__stream_decoder_get_decode_position",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    // FLAC__StreamDecoderInitStatus FLAC__stream_decoder_init_stream(...)
    private static final MethodHandle MH_init_stream = FlacLibrary.downcall(
            "FLAC__stream_decoder_init_stream",
            FunctionDescriptor.of(JAVA_INT,
                    ADDRESS, // decoder
                    ADDRESS, // read_callback
                    ADDRESS, // seek_callback  (nullable)
                    ADDRESS, // tell_callback  (nullable)
                    ADDRESS, // length_callback(nullable)
                    ADDRESS, // eof_callback   (nullable)
                    ADDRESS, // write_callback
                    ADDRESS, // metadata_callback (nullable)
                    ADDRESS, // error_callback
                    ADDRESS  // client_data
            ));

    // FLAC__StreamDecoderInitStatus FLAC__stream_decoder_init_ogg_stream(...)
    private static final MethodHandle MH_init_ogg_stream = FlacLibrary.downcall(
            "FLAC__stream_decoder_init_ogg_stream",
            FunctionDescriptor.of(JAVA_INT,
                    ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS,
                    ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    // FLAC__StreamDecoderInitStatus FLAC__stream_decoder_init_FILE(...)
    private static final MethodHandle MH_init_FILE = FlacLibrary.downcall(
            "FLAC__stream_decoder_init_FILE",
            FunctionDescriptor.of(JAVA_INT,
                    ADDRESS, // decoder
                    ADDRESS, // FILE*
                    ADDRESS, // write_callback
                    ADDRESS, // metadata_callback (nullable)
                    ADDRESS, // error_callback
                    ADDRESS  // client_data
            ));

    // FLAC__StreamDecoderInitStatus FLAC__stream_decoder_init_ogg_FILE(...)
    private static final MethodHandle MH_init_ogg_FILE = FlacLibrary.downcall(
            "FLAC__stream_decoder_init_ogg_FILE",
            FunctionDescriptor.of(JAVA_INT,
                    ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    // FLAC__StreamDecoderInitStatus FLAC__stream_decoder_init_file(...)
    private static final MethodHandle MH_init_file = FlacLibrary.downcall(
            "FLAC__stream_decoder_init_file",
            FunctionDescriptor.of(JAVA_INT,
                    ADDRESS, // decoder
                    ADDRESS, // filename (const char*)
                    ADDRESS, // write_callback
                    ADDRESS, // metadata_callback
                    ADDRESS, // error_callback
                    ADDRESS  // client_data
            ));

    // FLAC__StreamDecoderInitStatus FLAC__stream_decoder_init_ogg_file(...)
    private static final MethodHandle MH_init_ogg_file = FlacLibrary.downcall(
            "FLAC__stream_decoder_init_ogg_file",
            FunctionDescriptor.of(JAVA_INT,
                    ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    // FLAC__bool FLAC__stream_decoder_finish(decoder)
    private static final MethodHandle MH_finish = FlacLibrary.downcall(
            "FLAC__stream_decoder_finish",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // FLAC__bool FLAC__stream_decoder_flush(decoder)
    private static final MethodHandle MH_flush = FlacLibrary.downcall(
            "FLAC__stream_decoder_flush",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // FLAC__bool FLAC__stream_decoder_reset(decoder)
    private static final MethodHandle MH_reset = FlacLibrary.downcall(
            "FLAC__stream_decoder_reset",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // FLAC__bool FLAC__stream_decoder_process_single(decoder)
    private static final MethodHandle MH_process_single = FlacLibrary.downcall(
            "FLAC__stream_decoder_process_single",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // FLAC__bool FLAC__stream_decoder_process_until_end_of_metadata(decoder)
    private static final MethodHandle MH_process_until_end_of_metadata = FlacLibrary.downcall(
            "FLAC__stream_decoder_process_until_end_of_metadata",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // FLAC__bool FLAC__stream_decoder_process_until_end_of_stream(decoder)
    private static final MethodHandle MH_process_until_end_of_stream = FlacLibrary.downcall(
            "FLAC__stream_decoder_process_until_end_of_stream",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // FLAC__bool FLAC__stream_decoder_skip_single_frame(decoder)
    private static final MethodHandle MH_skip_single_frame = FlacLibrary.downcall(
            "FLAC__stream_decoder_skip_single_frame",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // FLAC__bool FLAC__stream_decoder_seek_absolute(decoder, uint64_t)
    private static final MethodHandle MH_seek_absolute = FlacLibrary.downcall(
            "FLAC__stream_decoder_seek_absolute",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG));

    // FLAC__bool FLAC__stream_decoder_set_decode_chained_stream(decoder, FLAC__bool)
    private static final MethodHandle MH_set_decode_chained = FlacLibrary.downcall(
            "FLAC__stream_decoder_set_decode_chained_stream",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    // FLAC__bool FLAC__stream_decoder_get_decode_chained_stream(decoder)
    private static final MethodHandle MH_get_decode_chained = FlacLibrary.downcall(
            "FLAC__stream_decoder_get_decode_chained_stream",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // -----------------------------------------------------------------------
    // Public static binding methods (thin wrappers – throw on error)
    // -----------------------------------------------------------------------

    public static MemorySegment newDecoder() {
        try { return (MemorySegment) MH_new.invokeExact(); }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static void deleteDecoder(MemorySegment decoder) {
        try { MH_delete.invokeExact(decoder); }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean setOggSerialNumber(MemorySegment d, long serial) {
        try { return (int) MH_set_ogg_serial.invokeExact(d, serial) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean setMd5Checking(MemorySegment d, boolean value) {
        try { return (int) MH_set_md5.invokeExact(d, value ? 1 : 0) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean setMetadataRespond(MemorySegment d, int type) {
        try { return (int) MH_set_metadata_respond.invokeExact(d, type) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean setMetadataRespondApplication(MemorySegment d, MemorySegment id4) {
        try { return (int) MH_set_metadata_respond_app.invokeExact(d, id4) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean setMetadataRespondAll(MemorySegment d) {
        try { return (int) MH_set_metadata_respond_all.invokeExact(d) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean setMetadataIgnore(MemorySegment d, int type) {
        try { return (int) MH_set_metadata_ignore.invokeExact(d, type) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean setMetadataIgnoreApplication(MemorySegment d, MemorySegment id4) {
        try { return (int) MH_set_metadata_ignore_app.invokeExact(d, id4) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean setMetadataIgnoreAll(MemorySegment d) {
        try { return (int) MH_set_metadata_ignore_all.invokeExact(d) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static int getState(MemorySegment d) {
        try { return (int) MH_get_state.invokeExact(d); }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static String getResolvedStateString(MemorySegment d) {
        try {
            MemorySegment p = (MemorySegment) MH_get_state_string.invokeExact(d);
            return p.getString(0);
        } catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean getMd5Checking(MemorySegment d) {
        try { return (int) MH_get_md5.invokeExact(d) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static long getTotalSamples(MemorySegment d) {
        try { return (long) MH_get_total_samples.invokeExact(d); }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static int getChannels(MemorySegment d) {
        try { return (int) MH_get_channels.invokeExact(d); }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static int getChannelAssignment(MemorySegment d) {
        try { return (int) MH_get_channel_assignment.invokeExact(d); }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static int getBitsPerSample(MemorySegment d) {
        try { return (int) MH_get_bits_per_sample.invokeExact(d); }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static int getSampleRate(MemorySegment d) {
        try { return (int) MH_get_sample_rate.invokeExact(d); }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static int getBlocksize(MemorySegment d) {
        try { return (int) MH_get_blocksize.invokeExact(d); }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean getDecodePosition(MemorySegment d, MemorySegment outU64) {
        try { return (int) MH_get_decode_position.invokeExact(d, outU64) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static int initStream(MemorySegment d,
            MemorySegment readCb, MemorySegment seekCb, MemorySegment tellCb,
            MemorySegment lengthCb, MemorySegment eofCb, MemorySegment writeCb,
            MemorySegment metaCb, MemorySegment errorCb, MemorySegment clientData) {
        try {
            return (int) MH_init_stream.invokeExact(
                    d, readCb, seekCb, tellCb, lengthCb, eofCb, writeCb, metaCb, errorCb, clientData);
        } catch (Throwable t) { throw new AssertionError(t); }
    }

    public static int initOggStream(MemorySegment d,
            MemorySegment readCb, MemorySegment seekCb, MemorySegment tellCb,
            MemorySegment lengthCb, MemorySegment eofCb, MemorySegment writeCb,
            MemorySegment metaCb, MemorySegment errorCb, MemorySegment clientData) {
        try {
            return (int) MH_init_ogg_stream.invokeExact(
                    d, readCb, seekCb, tellCb, lengthCb, eofCb, writeCb, metaCb, errorCb, clientData);
        } catch (Throwable t) { throw new AssertionError(t); }
    }

    public static int initFile(MemorySegment d, MemorySegment filename,
            MemorySegment writeCb, MemorySegment metaCb,
            MemorySegment errorCb, MemorySegment clientData) {
        try {
            return (int) MH_init_file.invokeExact(d, filename, writeCb, metaCb, errorCb, clientData);
        } catch (Throwable t) { throw new AssertionError(t); }
    }

    public static int initOggFile(MemorySegment d, MemorySegment filename,
            MemorySegment writeCb, MemorySegment metaCb,
            MemorySegment errorCb, MemorySegment clientData) {
        try {
            return (int) MH_init_ogg_file.invokeExact(d, filename, writeCb, metaCb, errorCb, clientData);
        } catch (Throwable t) { throw new AssertionError(t); }
    }

    public static int initFILE(MemorySegment d, MemorySegment file,
            MemorySegment writeCb, MemorySegment metaCb,
            MemorySegment errorCb, MemorySegment clientData) {
        try {
            return (int) MH_init_FILE.invokeExact(d, file, writeCb, metaCb, errorCb, clientData);
        } catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean finish(MemorySegment d) {
        try { return (int) MH_finish.invokeExact(d) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean flush(MemorySegment d) {
        try { return (int) MH_flush.invokeExact(d) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean reset(MemorySegment d) {
        try { return (int) MH_reset.invokeExact(d) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean processSingle(MemorySegment d) {
        try { return (int) MH_process_single.invokeExact(d) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean processUntilEndOfMetadata(MemorySegment d) {
        try { return (int) MH_process_until_end_of_metadata.invokeExact(d) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean processUntilEndOfStream(MemorySegment d) {
        try { return (int) MH_process_until_end_of_stream.invokeExact(d) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean skipSingleFrame(MemorySegment d) {
        try { return (int) MH_skip_single_frame.invokeExact(d) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean seekAbsolute(MemorySegment d, long sampleNumber) {
        try { return (int) MH_seek_absolute.invokeExact(d, sampleNumber) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean setDecodeChainedStream(MemorySegment d, boolean value) {
        try { return (int) MH_set_decode_chained.invokeExact(d, value ? 1 : 0) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    public static boolean getDecodeChainedStream(MemorySegment d) {
        try { return (int) MH_get_decode_chained.invokeExact(d) != 0; }
        catch (Throwable t) { throw new AssertionError(t); }
    }

    // -----------------------------------------------------------------------
    // Callback function descriptors (used by FlacStreamDecoder to upcall)
    // -----------------------------------------------------------------------

    /** read_callback: (decoder*, byte[], size_t*, clientData*) -> ReadStatus */
    public static final FunctionDescriptor FD_READ_CALLBACK =
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS);

    /** seek_callback: (decoder*, uint64, clientData*) -> SeekStatus */
    public static final FunctionDescriptor FD_SEEK_CALLBACK =
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS);

    /** tell_callback: (decoder*, uint64*, clientData*) -> TellStatus */
    public static final FunctionDescriptor FD_TELL_CALLBACK =
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS);

    /** length_callback: (decoder*, uint64*, clientData*) -> LengthStatus */
    public static final FunctionDescriptor FD_LENGTH_CALLBACK =
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS);

    /** eof_callback: (decoder*, clientData*) -> FLAC__bool */
    public static final FunctionDescriptor FD_EOF_CALLBACK =
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS);

    /** write_callback: (decoder*, frame*, buffer[][], clientData*) -> WriteStatus */
    public static final FunctionDescriptor FD_WRITE_CALLBACK =
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS);

    /** metadata_callback: (decoder*, metadata*, clientData*) -> void */
    public static final FunctionDescriptor FD_METADATA_CALLBACK =
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS);

    /** error_callback: (decoder*, ErrorStatus, clientData*) -> void */
    public static final FunctionDescriptor FD_ERROR_CALLBACK =
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS);
}
