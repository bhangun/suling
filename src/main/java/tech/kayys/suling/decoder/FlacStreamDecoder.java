package tech.kayys.suling.decoder;

import tech.kayys.suling.FlacException;
import tech.kayys.suling.internal.FlacLibrary;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;

/**
 * High-level Java wrapper for {@code FLAC__StreamDecoder}.
 *
 * <p>Each instance wraps a single native decoder object and is
 * <em>not thread-safe</em>. All methods must be called from the same thread
 * that constructed the decoder, or with external synchronisation.
 *
 * <h2>Usage – file decoding (simplest)</h2>
 * <pre>{@code
 * try (var decoder = new FlacStreamDecoder()) {
 *     decoder.setMd5Checking(true);
 *     decoder.setMetadataRespondAll();
 *     decoder.initFile(Path.of("song.flac"),
 *         (frame, pcm) -> {
 *             int[] samples = FlacPcmUtils.extractInterleavedInt32(frame, pcm);
 *             // … process audio …
 *             return StreamDecoderH.WRITE_STATUS_CONTINUE;
 *         },
 *         meta -> { /* inspect metadata block *\/ },
 *         err  -> System.err.println("Decode error: " + err));
 *     decoder.processUntilEndOfStream();
 * }
 * }</pre>
 *
 * <h2>Usage – custom I/O stream</h2>
 * <pre>{@code
 * try (var decoder = new FlacStreamDecoder()) {
 *     decoder.initStream(
 *         (buf, bytes) -> { /* fill buf from your source *\/ return READ_CONTINUE; },
 *         null, null, null, null,
 *         (frame, pcm) -> { /* PCM *\/ return WRITE_STATUS_CONTINUE; },
 *         meta -> {},
 *         err  -> {});
 *     decoder.processUntilEndOfStream();
 * }
 * }</pre>
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Construct</li>
 *   <li>Configure via {@code set*()} methods</li>
 *   <li>Call one {@code init*()} method</li>
 *   <li>Call {@code process*()} / {@code seek*()} as needed</li>
 *   <li>{@link #close()} (or use try-with-resources)</li>
 * </ol>
 *
 * @see FlacPcmUtils for helpers to extract PCM data from callback arguments
 * @since 1.5.0
 */
public final class FlacStreamDecoder implements AutoCloseable {

    // -----------------------------------------------------------------------
    // Callback interfaces
    // -----------------------------------------------------------------------

    /**
     * Called by the decoder when it needs more compressed data.
     */
    @FunctionalInterface
    public interface ReadCallback {
        /**
         * @param buffer  native buffer to fill with FLAC-compressed bytes
         * @param bytes   in/out: on entry the buffer capacity; on exit bytes actually supplied
         * @return {@link StreamDecoderH#READ_STATUS_CONTINUE},
         *         {@link StreamDecoderH#READ_STATUS_END_OF_STREAM}, or
         *         {@link StreamDecoderH#READ_STATUS_ABORT}
         */
        int read(MemorySegment buffer, MemorySegment bytes);
    }

    /**
     * Called when the decoder needs to seek in the source stream.
     */
    @FunctionalInterface
    public interface SeekCallback {
        /**
         * @param absoluteByteOffset absolute byte offset from the start
         * @return {@link StreamDecoderH#SEEK_STATUS_OK},
         *         {@link StreamDecoderH#SEEK_STATUS_ERROR}, or
         *         {@link StreamDecoderH#SEEK_STATUS_UNSUPPORTED}
         */
        int seek(long absoluteByteOffset);
    }

    /**
     * Called when the decoder queries the current stream position.
     */
    @FunctionalInterface
    public interface TellCallback {
        /**
         * @param outOffset write the current byte offset from stream start here
         * @return {@link StreamDecoderH#TELL_STATUS_OK},
         *         {@link StreamDecoderH#TELL_STATUS_ERROR}, or
         *         {@link StreamDecoderH#TELL_STATUS_UNSUPPORTED}
         */
        int tell(MemorySegment outOffset);
    }

    /**
     * Called when the decoder queries the total stream length.
     */
    @FunctionalInterface
    public interface LengthCallback {
        /**
         * @param outLength write total stream length in bytes here
         * @return {@link StreamDecoderH#LENGTH_STATUS_OK},
         *         {@link StreamDecoderH#LENGTH_STATUS_ERROR}, or
         *         {@link StreamDecoderH#LENGTH_STATUS_UNSUPPORTED}
         */
        int length(MemorySegment outLength);
    }

    /**
     * Called when the decoder checks for end-of-stream.
     */
    @FunctionalInterface
    public interface EofCallback {
        /** @return {@code true} when no more data is available */
        boolean eof();
    }

    /**
     * Delivers one decoded audio block (frame) to the application.
     */
    @FunctionalInterface
    public interface WriteCallback {
        /**
         * @param frame   pointer to the {@code FLAC__Frame} struct;
         *                use {@link FlacPcmUtils} to extract header fields
         * @param buffers pointer to the array of per-channel {@code int32} pointers;
         *                use {@link FlacPcmUtils#extractInterleavedInt32} to copy out PCM
         * @return {@link StreamDecoderH#WRITE_STATUS_CONTINUE} to continue decoding, or
         *         {@link StreamDecoderH#WRITE_STATUS_ABORT} to abort
         */
        int write(MemorySegment frame, MemorySegment buffers);
    }

    /**
     * Called once per metadata block the decoder is configured to report.
     */
    @FunctionalInterface
    public interface MetadataCallback {
        /**
         * @param metadata pointer to the {@code FLAC__StreamMetadata} struct
         */
        void metadata(MemorySegment metadata);
    }

    /**
     * Called when a decode error occurs.
     */
    @FunctionalInterface
    public interface ErrorCallback {
        /**
         * @param status one of the {@code FLAC__StreamDecoderErrorStatus} constants
         *               in {@link StreamDecoderH} (e.g.
         *               {@link StreamDecoderH#ERROR_STATUS_LOST_SYNC})
         */
        void error(int status);
    }

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    /** The native {@code FLAC__StreamDecoder *}. */
    private final MemorySegment peer;

    /** Owns all upcall stubs created for this decoder instance. */
    private final Arena arena;

    /** Guards against double-close. */
    private volatile boolean closed = false;

    // Strong references so the GC cannot collect the upcall stub objects.
    @SuppressWarnings("FieldCanBeLocal")
    private MemorySegment cbRead, cbSeek, cbTell, cbLength, cbEof,
                          cbWrite, cbMeta, cbError;

    // -----------------------------------------------------------------------
    // Constructor / AutoCloseable
    // -----------------------------------------------------------------------

    /**
     * Allocates a new {@code FLAC__StreamDecoder}.
     *
     * @throws OutOfMemoryError if libFLAC returns {@code NULL}
     */
    public FlacStreamDecoder() {
        peer = StreamDecoderH.newDecoder();
        if (peer.equals(MemorySegment.NULL)) {
            throw new OutOfMemoryError("FLAC__stream_decoder_new returned NULL");
        }
        arena = Arena.ofConfined();
    }

    /**
     * Flushes, finalizes, and frees the native decoder.
     *
     * <p>Calling {@code close()} more than once is safe; subsequent calls
     * are silently ignored.
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            StreamDecoderH.finish(peer);
        } finally {
            StreamDecoderH.deleteDecoder(peer);
            arena.close();
        }
    }

    // -----------------------------------------------------------------------
    // Configuration (must be called before init*)
    // -----------------------------------------------------------------------

    /**
     * Enables or disables MD5 signature verification on decoding completion.
     * Defaults to {@code false}; enable for data-integrity checking.
     */
    public boolean setMd5Checking(boolean value) {
        checkOpen();
        return StreamDecoderH.setMd5Checking(peer, value);
    }

    /** Sets the Ogg serial number for Ogg-FLAC streams. */
    public boolean setOggSerialNumber(long serial) {
        checkOpen();
        return StreamDecoderH.setOggSerialNumber(peer, serial);
    }

    /** Requests delivery of a specific metadata block type in the metadata callback. */
    public boolean setMetadataRespond(int metadataType) {
        checkOpen();
        return StreamDecoderH.setMetadataRespond(peer, metadataType);
    }

    /** Requests delivery of all metadata block types. */
    public boolean setMetadataRespondAll() {
        checkOpen();
        return StreamDecoderH.setMetadataRespondAll(peer);
    }

    /** Requests delivery of {@code APPLICATION} metadata for a given 4-byte ID. */
    public boolean setMetadataRespondApplication(byte[] id4) {
        checkOpen();
        if (id4 == null || id4.length != 4)
            throw new IllegalArgumentException("id4 must be exactly 4 bytes");
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment seg = tmp.allocateFrom(ValueLayout.JAVA_BYTE, id4[0], id4[1], id4[2], id4[3]);
            return StreamDecoderH.setMetadataRespondApplication(peer, seg);
        }
    }

    /** Suppresses delivery of a specific metadata block type. */
    public boolean setMetadataIgnore(int metadataType) {
        checkOpen();
        return StreamDecoderH.setMetadataIgnore(peer, metadataType);
    }

    /** Suppresses delivery of all metadata block types. */
    public boolean setMetadataIgnoreAll() {
        checkOpen();
        return StreamDecoderH.setMetadataIgnoreAll(peer);
    }

    /** Suppresses delivery of {@code APPLICATION} metadata for a given 4-byte ID. */
    public boolean setMetadataIgnoreApplication(byte[] id4) {
        checkOpen();
        if (id4 == null || id4.length != 4)
            throw new IllegalArgumentException("id4 must be exactly 4 bytes");
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment seg = tmp.allocateFrom(ValueLayout.JAVA_BYTE, id4[0], id4[1], id4[2], id4[3]);
            return StreamDecoderH.setMetadataIgnoreApplication(peer, seg);
        }
    }

    /** Controls whether chained Ogg FLAC streams are decoded. */
    public boolean setDecodeChainedStream(boolean value) {
        checkOpen();
        return StreamDecoderH.setDecodeChainedStream(peer, value);
    }

    // -----------------------------------------------------------------------
    // Initialization – custom I/O stream
    // -----------------------------------------------------------------------

    /**
     * Initialises the decoder to read from a custom I/O source via callbacks.
     *
     * <p>At minimum {@code readCb}, {@code writeCb}, and {@code errorCb} are
     * required. The seek/tell/length/eof callbacks may be {@code null} for
     * non-seekable streams (no seeking or seektable support in that case).
     *
     * @return one of the {@code FLAC__StreamDecoderInitStatus} constants
     * @throws FlacException.FlacInitException if the status indicates failure
     */
    public int initStream(ReadCallback readCb,
                          SeekCallback seekCb,
                          TellCallback tellCb,
                          LengthCallback lengthCb,
                          EofCallback eofCb,
                          WriteCallback writeCb,
                          MetadataCallback metaCb,
                          ErrorCallback errorCb) {
        checkOpen();
        cbRead   = makeReadUpcall(readCb);
        cbSeek   = seekCb   != null ? makeSeekUpcall(seekCb)     : MemorySegment.NULL;
        cbTell   = tellCb   != null ? makeTellUpcall(tellCb)     : MemorySegment.NULL;
        cbLength = lengthCb != null ? makeLengthUpcall(lengthCb) : MemorySegment.NULL;
        cbEof    = eofCb    != null ? makeEofUpcall(eofCb)       : MemorySegment.NULL;
        cbWrite  = makeWriteUpcall(writeCb);
        cbMeta   = metaCb   != null ? makeMetaUpcall(metaCb)     : MemorySegment.NULL;
        cbError  = makeErrorUpcall(errorCb);

        int status = StreamDecoderH.initStream(peer, cbRead, cbSeek, cbTell, cbLength,
                cbEof, cbWrite, cbMeta, cbError, MemorySegment.NULL);
        checkInitStatus(status, "stream");
        return status;
    }

    /** Initialises for decoding an Ogg-FLAC stream via callbacks. */
    public int initOggStream(ReadCallback readCb,
                             SeekCallback seekCb,
                             TellCallback tellCb,
                             LengthCallback lengthCb,
                             EofCallback eofCb,
                             WriteCallback writeCb,
                             MetadataCallback metaCb,
                             ErrorCallback errorCb) {
        checkOpen();
        cbRead   = makeReadUpcall(readCb);
        cbSeek   = seekCb   != null ? makeSeekUpcall(seekCb)     : MemorySegment.NULL;
        cbTell   = tellCb   != null ? makeTellUpcall(tellCb)     : MemorySegment.NULL;
        cbLength = lengthCb != null ? makeLengthUpcall(lengthCb) : MemorySegment.NULL;
        cbEof    = eofCb    != null ? makeEofUpcall(eofCb)       : MemorySegment.NULL;
        cbWrite  = makeWriteUpcall(writeCb);
        cbMeta   = metaCb   != null ? makeMetaUpcall(metaCb)     : MemorySegment.NULL;
        cbError  = makeErrorUpcall(errorCb);

        int status = StreamDecoderH.initOggStream(peer, cbRead, cbSeek, cbTell, cbLength,
                cbEof, cbWrite, cbMeta, cbError, MemorySegment.NULL);
        checkInitStatus(status, "ogg-stream");
        return status;
    }

    // -----------------------------------------------------------------------
    // Initialization – file by path
    // -----------------------------------------------------------------------

    /**
     * Initialises the decoder to read from a file.
     *
     * @param path     path to the FLAC file (must exist)
     * @param writeCb  required write callback
     * @param metaCb   optional metadata callback (pass {@code null} to suppress)
     * @param errorCb  required error callback
     * @return {@code FLAC__StreamDecoderInitStatus} (0 = OK)
     * @throws FlacException.FlacInitException if init fails
     */
    public int initFile(Path path, WriteCallback writeCb,
                        MetadataCallback metaCb, ErrorCallback errorCb) {
        checkOpen();
        cbWrite = makeWriteUpcall(writeCb);
        cbMeta  = metaCb != null ? makeMetaUpcall(metaCb) : MemorySegment.NULL;
        cbError = makeErrorUpcall(errorCb);

        MemorySegment cPath = arena.allocateFrom(path.toAbsolutePath().toString());
        int status = StreamDecoderH.initFile(peer, cPath, cbWrite, cbMeta, cbError, MemorySegment.NULL);
        checkInitStatus(status, "file: " + path);
        return status;
    }

    /** Initialises the decoder to read from an Ogg-FLAC file. */
    public int initOggFile(Path path, WriteCallback writeCb,
                           MetadataCallback metaCb, ErrorCallback errorCb) {
        checkOpen();
        cbWrite = makeWriteUpcall(writeCb);
        cbMeta  = metaCb != null ? makeMetaUpcall(metaCb) : MemorySegment.NULL;
        cbError = makeErrorUpcall(errorCb);

        MemorySegment cPath = arena.allocateFrom(path.toAbsolutePath().toString());
        int status = StreamDecoderH.initOggFile(peer, cPath, cbWrite, cbMeta, cbError, MemorySegment.NULL);
        checkInitStatus(status, "ogg-file: " + path);
        return status;
    }

    // -----------------------------------------------------------------------
    // Decoding operations
    // -----------------------------------------------------------------------

    /** Decodes a single FLAC frame. Returns {@code false} on error. */
    public boolean processSingle() {
        checkOpen();
        return StreamDecoderH.processSingle(peer);
    }

    /**
     * Decodes until all metadata blocks have been delivered.
     * Returns {@code false} on error.
     */
    public boolean processUntilEndOfMetadata() {
        checkOpen();
        return StreamDecoderH.processUntilEndOfMetadata(peer);
    }

    /**
     * Decodes the entire stream to completion.
     * Returns {@code false} on error.
     */
    public boolean processUntilEndOfStream() {
        checkOpen();
        return StreamDecoderH.processUntilEndOfStream(peer);
    }

    /** Skips one frame without delivering PCM data. Returns {@code false} on error. */
    public boolean skipSingleFrame() {
        checkOpen();
        return StreamDecoderH.skipSingleFrame(peer);
    }

    /**
     * Seeks to the frame containing the given sample number.
     *
     * @param sampleNumber absolute sample offset (0-based)
     * @return {@code true} on success; {@code false} if the stream does not support seeking
     */
    public boolean seekAbsolute(long sampleNumber) {
        checkOpen();
        return StreamDecoderH.seekAbsolute(peer, sampleNumber);
    }

    /**
     * Flushes the decoder's input buffer (useful after an error).
     */
    public boolean flush() {
        checkOpen();
        return StreamDecoderH.flush(peer);
    }

    /**
     * Resets the decoder to the beginning of the stream.
     */
    public boolean reset() {
        checkOpen();
        return StreamDecoderH.reset(peer);
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    /** Returns the current decoder state constant (one of {@code StreamDecoderH.STATE_*}). */
    public int    getState()               { checkOpen(); return StreamDecoderH.getState(peer); }

    /** Returns a human-readable string describing the current decoder state. */
    public String getStateString()         { checkOpen(); return StreamDecoderH.getResolvedStateString(peer); }

    /** Returns {@code true} if MD5 checking is enabled. */
    public boolean getMd5Checking()        { checkOpen(); return StreamDecoderH.getMd5Checking(peer); }

    /** Returns the total number of samples reported in the STREAMINFO block (0 if unknown). */
    public long   getTotalSamples()        { checkOpen(); return StreamDecoderH.getTotalSamples(peer); }

    /** Returns the channel count of the decoded stream. */
    public int    getChannels()            { checkOpen(); return StreamDecoderH.getChannels(peer); }

    /** Returns the channel assignment constant (see {@link tech.kayys.suling.format.FlacFormat.ChannelAssignment}). */
    public int    getChannelAssignment()   { checkOpen(); return StreamDecoderH.getChannelAssignment(peer); }

    /** Returns bits per sample of the decoded stream. */
    public int    getBitsPerSample()       { checkOpen(); return StreamDecoderH.getBitsPerSample(peer); }

    /** Returns the sample rate in Hz. */
    public int    getSampleRate()          { checkOpen(); return StreamDecoderH.getSampleRate(peer); }

    /** Returns the blocksize of the last decoded frame. */
    public int    getBlocksize()           { checkOpen(); return StreamDecoderH.getBlocksize(peer); }

    /** Returns whether chained Ogg stream decoding is enabled. */
    public boolean getDecodeChainedStream(){ checkOpen(); return StreamDecoderH.getDecodeChainedStream(peer); }

    /**
     * Returns the current byte offset within the stream, or {@code -1} if unsupported.
     */
    public long getDecodePosition() {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment out = tmp.allocate(ValueLayout.JAVA_LONG);
            return StreamDecoderH.getDecodePosition(peer, out) ? out.get(ValueLayout.JAVA_LONG, 0) : -1L;
        }
    }

    /**
     * Returns {@code true} if the decoder was successfully initialised and is not yet closed.
     */
    public boolean isOpen() { return !closed; }

    /**
     * Returns the raw native {@code FLAC__StreamDecoder*} peer for use with {@link StreamDecoderH}.
     *
     * @apiNote Use this only when you need to call a raw {@link StreamDecoderH} method
     *          not exposed by this wrapper.
     */
    public MemorySegment peer() { return peer; }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private void checkOpen() {
        if (closed) throw new IllegalStateException("FlacStreamDecoder is closed");
    }

    private static void checkInitStatus(int status, String ctx) {
        if (status != StreamDecoderH.INIT_STATUS_OK) {
            throw new FlacException.FlacInitException(
                    "FLAC__stream_decoder_init failed for " + ctx, status);
        }
    }

    // -----------------------------------------------------------------------
    // Upcall stub factories
    // -----------------------------------------------------------------------
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private MemorySegment makeReadUpcall(ReadCallback cb) {
        try {
            MethodHandle mh = LOOKUP.findVirtual(ReadCallback.class, "read",
                    MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class))
                    .bindTo(cb);
            // Native: (decoder*, buf*, size_t*, clientData*) -> int
            MethodHandle adapted = MethodHandles.dropArguments(
                    MethodHandles.dropArguments(mh, 3, MemorySegment.class), 0, MemorySegment.class);
            return FlacLibrary.linker().upcallStub(adapted, StreamDecoderH.FD_READ_CALLBACK, arena);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private MemorySegment makeSeekUpcall(SeekCallback cb) {
        try {
            MethodHandle mh = LOOKUP.findVirtual(SeekCallback.class, "seek",
                    MethodType.methodType(int.class, long.class)).bindTo(cb);
            // Native: (decoder*, uint64, clientData*) -> int
            MethodHandle adapted = MethodHandles.dropArguments(
                    MethodHandles.dropArguments(mh, 1, MemorySegment.class), 0, MemorySegment.class);
            return FlacLibrary.linker().upcallStub(adapted, StreamDecoderH.FD_SEEK_CALLBACK, arena);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private MemorySegment makeTellUpcall(TellCallback cb) {
        try {
            MethodHandle mh = LOOKUP.findVirtual(TellCallback.class, "tell",
                    MethodType.methodType(int.class, MemorySegment.class)).bindTo(cb);
            // Native: (decoder*, uint64*, clientData*) -> int
            MethodHandle adapted = MethodHandles.dropArguments(
                    MethodHandles.dropArguments(mh, 1, MemorySegment.class), 0, MemorySegment.class);
            return FlacLibrary.linker().upcallStub(adapted, StreamDecoderH.FD_TELL_CALLBACK, arena);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private MemorySegment makeLengthUpcall(LengthCallback cb) {
        try {
            MethodHandle mh = LOOKUP.findVirtual(LengthCallback.class, "length",
                    MethodType.methodType(int.class, MemorySegment.class)).bindTo(cb);
            MethodHandle adapted = MethodHandles.dropArguments(
                    MethodHandles.dropArguments(mh, 1, MemorySegment.class), 0, MemorySegment.class);
            return FlacLibrary.linker().upcallStub(adapted, StreamDecoderH.FD_LENGTH_CALLBACK, arena);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private MemorySegment makeEofUpcall(EofCallback cb) {
        try {
            MethodHandle raw = LOOKUP.findVirtual(EofCallback.class, "eof",
                    MethodType.methodType(boolean.class)).bindTo(cb);
            MethodHandle intResult = raw.asType(MethodType.methodType(int.class));
            MethodHandle adapted = MethodHandles.dropArguments(
                    intResult, 0, MemorySegment.class, MemorySegment.class);
            return FlacLibrary.linker().upcallStub(adapted, StreamDecoderH.FD_EOF_CALLBACK, arena);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private MemorySegment makeWriteUpcall(WriteCallback cb) {
        try {
            MethodHandle mh = LOOKUP.findVirtual(WriteCallback.class, "write",
                    MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class))
                    .bindTo(cb);
            // Native: (decoder*, frame*, buf**, clientData*) -> int
            MethodHandle adapted = MethodHandles.dropArguments(
                    MethodHandles.dropArguments(mh, 2, MemorySegment.class), 0, MemorySegment.class);
            return FlacLibrary.linker().upcallStub(adapted, StreamDecoderH.FD_WRITE_CALLBACK, arena);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private MemorySegment makeMetaUpcall(MetadataCallback cb) {
        try {
            MethodHandle mh = LOOKUP.findVirtual(MetadataCallback.class, "metadata",
                    MethodType.methodType(void.class, MemorySegment.class)).bindTo(cb);
            // Native: (decoder*, meta*, clientData*) -> void
            MethodHandle adapted = MethodHandles.dropArguments(
                    MethodHandles.dropArguments(mh, 1, MemorySegment.class), 0, MemorySegment.class);
            return FlacLibrary.linker().upcallStub(adapted, StreamDecoderH.FD_METADATA_CALLBACK, arena);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private MemorySegment makeErrorUpcall(ErrorCallback cb) {
        try {
            MethodHandle mh = LOOKUP.findVirtual(ErrorCallback.class, "error",
                    MethodType.methodType(void.class, int.class)).bindTo(cb);
            // Native: (decoder*, ErrorStatus, clientData*) -> void
            MethodHandle adapted = MethodHandles.dropArguments(
                    MethodHandles.dropArguments(mh, 1, MemorySegment.class), 0, MemorySegment.class);
            return FlacLibrary.linker().upcallStub(adapted, StreamDecoderH.FD_ERROR_CALLBACK, arena);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
