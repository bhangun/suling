package tech.kayys.suling.encoder;

import tech.kayys.suling.FlacAudioFormat;
import tech.kayys.suling.decoder.FlacStreamDecoder;
import tech.kayys.suling.FlacException;
import tech.kayys.suling.internal.FlacLibrary;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;

/**
 * High-level Java wrapper for {@code FLAC__StreamEncoder}.
 *
 * <p>Each instance wraps a single native encoder object and is
 * <em>not thread-safe</em>. All methods must be called from the same thread
 * that constructed the encoder, or with external synchronisation.
 *
 * <h2>File encoding (simplest usage)</h2>
 * <pre>{@code
 * var fmt = FlacAudioFormat.builder()
 *         .channels(2).bitsPerSample(16).sampleRate(44100)
 *         .totalSamples(numSamples).build();
 *
 * try (var enc = new FlacStreamEncoder()) {
 *     enc.applyFormat(fmt)
 *        .setCompressionLevel(5)
 *        .setVerify(true);
 *
 *     enc.initFile(Path.of("out.flac"), null); // throws on failure
 *     enc.processInterleaved(pcm);
 *     enc.finish();
 * }
 * }</pre>
 *
 * <h2>Stream encoding with custom write sink</h2>
 * <pre>{@code
 * try (var enc = new FlacStreamEncoder()) {
 *     enc.setChannels(1).setBitsPerSample(24).setSampleRate(48000);
 *     enc.initStream(
 *         (buf, size, samples, frame) -> {
 *             outputStream.write(buf.reinterpret(size).toArray(ValueLayout.JAVA_BYTE));
 *             return StreamEncoderH.WRITE_STATUS_OK;
 *         },
 *         null, null, null);
 *     enc.processInterleaved(pcm);
 *     enc.finish();
 * }
 * }</pre>
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Construct</li>
 *   <li>Configure via {@code set*()} / {@code applyFormat()}</li>
 *   <li>Call one {@code init*()} method (throws {@link FlacException.FlacInitException} on failure)</li>
 *   <li>Call {@code processInterleaved()} or {@code process()} one or more times</li>
 *   <li>Call {@code finish()} — writes the seek table and finalizes the stream</li>
 *   <li>{@link #close()} (or use try-with-resources; calls {@code finish()} if not yet called)</li>
 * </ol>
 *
 * @since 1.5.0
 */
public final class FlacStreamEncoder implements AutoCloseable {

    // -----------------------------------------------------------------------
    // Callback interfaces
    // -----------------------------------------------------------------------

    /**
     * Receives encoded FLAC data as the encoder produces it.
     */
    @FunctionalInterface
    public interface WriteCallback {
        /**
         * @param buffer       native buffer containing encoded FLAC data
         * @param bytes        number of bytes in {@code buffer}
         * @param samples      number of PCM samples represented (0 for metadata frames)
         * @param currentFrame sequential frame number
         * @return {@link StreamEncoderH#WRITE_STATUS_OK} or
         *         {@link StreamEncoderH#WRITE_STATUS_FATAL_ERROR}
         */
        int write(MemorySegment buffer, long bytes, int samples, int currentFrame);
    }

    /**
     * Called when the encoder needs to seek within the encoded output
     * (required for seekable streams so the seektable can be updated).
     */
    @FunctionalInterface
    public interface SeekCallback {
        /**
         * @return {@link StreamEncoderH#SEEK_STATUS_OK},
         *         {@link StreamEncoderH#SEEK_STATUS_ERROR}, or
         *         {@link StreamEncoderH#SEEK_STATUS_UNSUPPORTED}
         */
        int seek(long absoluteByteOffset);
    }

    /**
     * Called when the encoder queries the current write position.
     */
    @FunctionalInterface
    public interface TellCallback {
        /**
         * @return {@link StreamEncoderH#TELL_STATUS_OK},
         *         {@link StreamEncoderH#TELL_STATUS_ERROR}, or
         *         {@link StreamEncoderH#TELL_STATUS_UNSUPPORTED}
         */
        int tell(MemorySegment outOffset);
    }

    /**
     * Called once the encoder has finalized the STREAMINFO block (end of {@link #finish()}).
     */
    @FunctionalInterface
    public interface MetadataCallback {
        void metadata(MemorySegment metadataPtr);
    }

    /**
     * Periodically reports encoding progress.
     */
    @FunctionalInterface
    public interface ProgressCallback {
        /**
         * @param bytesWritten   encoded bytes written so far
         * @param samplesWritten PCM samples encoded so far
         * @param framesWritten  FLAC frames written so far
         * @param totalFramesEst total frame count estimate (0 if unknown)
         */
        void progress(long bytesWritten, long samplesWritten,
                      int framesWritten, int totalFramesEst);
    }

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    private final MemorySegment peer;
    private final Arena arena;
    private volatile boolean closed = false;
    private boolean finishCalled = false;

    @SuppressWarnings("FieldCanBeLocal")
    private MemorySegment cbRead, cbWrite, cbSeek, cbTell, cbMeta, cbProgress;

    // -----------------------------------------------------------------------
    // Constructor / AutoCloseable
    // -----------------------------------------------------------------------

    /**
     * Allocates a new {@code FLAC__StreamEncoder}.
     *
     * @throws OutOfMemoryError if libFLAC returns {@code NULL}
     */
    public FlacStreamEncoder() {
        peer = StreamEncoderH.newEncoder();
        if (peer.equals(MemorySegment.NULL)) {
            throw new OutOfMemoryError("FLAC__stream_encoder_new returned NULL");
        }
        arena = Arena.ofConfined();
    }

    /**
     * Calls {@link #finish()} if not yet called, then frees the native encoder.
     *
     * <p>Calling {@code close()} more than once is safe; subsequent calls are silently ignored.
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            if (!finishCalled) {
                StreamEncoderH.finish(peer);
            }
        } finally {
            StreamEncoderH.deleteEncoder(peer);
            arena.close();
        }
    }

    // -----------------------------------------------------------------------
    // Format shortcut
    // -----------------------------------------------------------------------

    /**
     * Applies all audio format parameters from a {@link FlacAudioFormat} in one call.
     * Equivalent to calling {@link #setChannels}, {@link #setBitsPerSample},
     * {@link #setSampleRate}, and {@link #setTotalSamplesEstimate} individually.
     *
     * @return {@code this} for fluent chaining
     */
    public FlacStreamEncoder applyFormat(FlacAudioFormat fmt) {
        return setChannels(fmt.channels())
                .setBitsPerSample(fmt.bitsPerSample())
                .setSampleRate(fmt.sampleRate())
                .setTotalSamplesEstimate(fmt.totalSamples());
    }

    // -----------------------------------------------------------------------
    // Setters – all return {@code this} for fluent chaining
    // -----------------------------------------------------------------------
    public FlacStreamEncoder setVerify(boolean v)                  { checkOpen(); StreamEncoderH.setVerify(peer, v);                  return this; }
    public FlacStreamEncoder setStreamableSubset(boolean v)        { checkOpen(); StreamEncoderH.setStreamableSubset(peer, v);        return this; }
    public FlacStreamEncoder setChannels(int v)                    { checkOpen(); StreamEncoderH.setChannels(peer, v);                return this; }
    public FlacStreamEncoder setBitsPerSample(int v)               { checkOpen(); StreamEncoderH.setBitsPerSample(peer, v);           return this; }
    public FlacStreamEncoder setSampleRate(int v)                  { checkOpen(); StreamEncoderH.setSampleRate(peer, v);              return this; }
    public FlacStreamEncoder setCompressionLevel(int v)            { checkOpen(); StreamEncoderH.setCompressionLevel(peer, v);        return this; }
    public FlacStreamEncoder setBlocksize(int v)                   { checkOpen(); StreamEncoderH.setBlocksize(peer, v);               return this; }
    public FlacStreamEncoder setDoMidSideStereo(boolean v)         { checkOpen(); StreamEncoderH.setDoMidSideStereo(peer, v);         return this; }
    public FlacStreamEncoder setLooseMidSideStereo(boolean v)      { checkOpen(); StreamEncoderH.setLooseMidSideStereo(peer, v);      return this; }
    public FlacStreamEncoder setApodization(String spec) {
        checkOpen();
        if (spec == null) throw new NullPointerException("spec must not be null");
        MemorySegment cStr = arena.allocateFrom(spec);
        StreamEncoderH.setApodization(peer, cStr);
        return this;
    }
    public FlacStreamEncoder setMaxLpcOrder(int v)                 { checkOpen(); StreamEncoderH.setMaxLpcOrder(peer, v);             return this; }
    public FlacStreamEncoder setQlpCoeffPrecision(int v)           { checkOpen(); StreamEncoderH.setQlpCoeffPrecision(peer, v);       return this; }
    public FlacStreamEncoder setDoQlpCoeffPrecSearch(boolean v)    { checkOpen(); StreamEncoderH.setDoQlpCoeffPrecSearch(peer, v);    return this; }
    public FlacStreamEncoder setDoEscapeCoding(boolean v)          { checkOpen(); StreamEncoderH.setDoEscapeCoding(peer, v);          return this; }
    public FlacStreamEncoder setDoExhaustiveModelSearch(boolean v) { checkOpen(); StreamEncoderH.setDoExhaustiveModelSearch(peer, v); return this; }
    public FlacStreamEncoder setMinResidualPartitionOrder(int v)   { checkOpen(); StreamEncoderH.setMinResidualPartitionOrder(peer, v);return this; }
    public FlacStreamEncoder setMaxResidualPartitionOrder(int v)   { checkOpen(); StreamEncoderH.setMaxResidualPartitionOrder(peer, v);return this; }
    public FlacStreamEncoder setRiceParameterSearchDist(int v)     { checkOpen(); StreamEncoderH.setRiceParameterSearchDist(peer, v); return this; }
    public FlacStreamEncoder setTotalSamplesEstimate(long v)       { checkOpen(); StreamEncoderH.setTotalSamplesEstimate(peer, v);    return this; }
    public FlacStreamEncoder setLimitMinBitrate(boolean v)         { checkOpen(); StreamEncoderH.setLimitMinBitrate(peer, v);         return this; }

    /**
     * Injects metadata blocks into the output stream before initialisation.
     *
     * <p>Must be called <em>before</em> any {@code init*()} method.
     * Common uses: inject a VORBIS_COMMENT block for tags, a SEEKTABLE placeholder
     * for better seeking, or a PICTURE block for embedded cover art.
     *
     * @param metadataBlocks array of {@code FLAC__StreamMetadata*} pointers
     * @return {@code this} for fluent chaining
     * @throws FlacException.FlacEncodingException if libFLAC rejects the metadata
     */
    public FlacStreamEncoder setMetadata(MemorySegment[] metadataBlocks) {
        checkOpen();
        if (metadataBlocks == null || metadataBlocks.length == 0)
            throw new IllegalArgumentException("metadataBlocks must not be null or empty");
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment ptrArray = tmp.allocate(ValueLayout.ADDRESS, metadataBlocks.length);
            for (int i = 0; i < metadataBlocks.length; i++) {
                ptrArray.setAtIndex(ValueLayout.ADDRESS, i, metadataBlocks[i]);
            }
            boolean ok = StreamEncoderH.setMetadata(peer, ptrArray, metadataBlocks.length);
            if (!ok) throw new FlacException.FlacEncodingException(
                    "FLAC__stream_encoder_set_metadata rejected the supplied blocks");
        }
        return this;
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------
    public int     getState()                    { checkOpen(); return StreamEncoderH.getState(peer); }
    public String  getStateString()              { checkOpen(); return StreamEncoderH.getResolvedStateString(peer); }
    public int     getVerifyDecoderState()       { checkOpen(); return StreamEncoderH.getVerifyDecoderState(peer); }
    public boolean getVerify()                   { checkOpen(); return StreamEncoderH.getVerify(peer); }
    public boolean getStreamableSubset()         { checkOpen(); return StreamEncoderH.getStreamableSubset(peer); }
    public int     getChannels()                 { checkOpen(); return StreamEncoderH.getChannels(peer); }
    public int     getBitsPerSample()            { checkOpen(); return StreamEncoderH.getBitsPerSample(peer); }
    public int     getSampleRate()               { checkOpen(); return StreamEncoderH.getSampleRate(peer); }
    public int     getBlocksize()                { checkOpen(); return StreamEncoderH.getBlocksize(peer); }
    public boolean getDoMidSideStereo()          { checkOpen(); return StreamEncoderH.getDoMidSideStereo(peer); }
    public boolean getLooseMidSideStereo()       { checkOpen(); return StreamEncoderH.getLooseMidSideStereo(peer); }
    public int     getMaxLpcOrder()              { checkOpen(); return StreamEncoderH.getMaxLpcOrder(peer); }
    public int     getQlpCoeffPrecision()        { checkOpen(); return StreamEncoderH.getQlpCoeffPrecision(peer); }
    public boolean getDoQlpCoeffPrecSearch()     { checkOpen(); return StreamEncoderH.getDoQlpCoeffPrecSearch(peer); }
    public boolean getDoEscapeCoding()           { checkOpen(); return StreamEncoderH.getDoEscapeCoding(peer); }
    public boolean getDoExhaustiveModelSearch()  { checkOpen(); return StreamEncoderH.getDoExhaustiveModelSearch(peer); }
    public int     getMinPartitionOrder()        { checkOpen(); return StreamEncoderH.getMinPartitionOrder(peer); }
    public int     getMaxPartitionOrder()        { checkOpen(); return StreamEncoderH.getMaxPartitionOrder(peer); }
    public int     getRiceParamSearchDist()      { checkOpen(); return StreamEncoderH.getRiceParamSearchDist(peer); }
    public long    getTotalSamplesEstimate()     { checkOpen(); return StreamEncoderH.getTotalSamplesEstimate(peer); }
    public boolean getLimitMinBitrate()          { checkOpen(); return StreamEncoderH.getLimitMinBitrate(peer); }

    /**
     * Returns {@code true} if the encoder has not yet been closed.
     */
    public boolean isOpen() { return !closed; }

    // -----------------------------------------------------------------------
    // Initialization
    // -----------------------------------------------------------------------

    /**
     * Initialises the encoder to write to a custom sink via callbacks.
     *
     * <p>{@code seekCb}, {@code tellCb}, and {@code metaCb} may be {@code null}
     * for non-seekable sinks. Omitting them disables seektable generation.
     *
     * @throws FlacException.FlacInitException if initialisation fails
     */
    public int initStream(WriteCallback writeCb,
                          SeekCallback seekCb,
                          TellCallback tellCb,
                          MetadataCallback metaCb) {
        checkOpen();
        cbWrite = makeWriteUpcall(writeCb);
        cbSeek  = seekCb != null ? makeSeekUpcall(seekCb) : MemorySegment.NULL;
        cbTell  = tellCb != null ? makeTellUpcall(tellCb) : MemorySegment.NULL;
        cbMeta  = metaCb != null ? makeMetaUpcall(metaCb) : MemorySegment.NULL;

        int status = StreamEncoderH.initStream(peer, cbWrite, cbSeek, cbTell, cbMeta, MemorySegment.NULL);
        checkInitStatus(status, "stream");
        return status;
    }

    /**
     * Initialises the encoder to write to an Ogg-FLAC stream via callbacks.
     *
     * <p>The {@code readCb} is required for Ogg (the encoder reads back Ogg pages to
     * update the STREAMINFO block). {@code seekCb}, {@code tellCb}, and {@code metaCb}
     * may be {@code null} for non-seekable sinks.
     *
     * @throws FlacException.FlacInitException if initialisation fails
     */
    public int initOggStream(FlacStreamDecoder.ReadCallback readCb,
                             WriteCallback writeCb,
                             SeekCallback seekCb,
                             TellCallback tellCb,
                             MetadataCallback metaCb) {
        checkOpen();
        // Ogg read callback uses the encoder's read-callback signature
        cbRead = readCb != null ? makeOggReadUpcall(readCb) : MemorySegment.NULL;
        cbWrite = makeWriteUpcall(writeCb);
        cbSeek  = seekCb  != null ? makeSeekUpcall(seekCb)  : MemorySegment.NULL;
        cbTell  = tellCb  != null ? makeTellUpcall(tellCb)  : MemorySegment.NULL;
        cbMeta  = metaCb  != null ? makeMetaUpcall(metaCb)  : MemorySegment.NULL;

        int status = StreamEncoderH.initOggStream(peer, cbRead, cbWrite, cbSeek, cbTell, cbMeta, MemorySegment.NULL);
        checkInitStatus(status, "ogg-stream");
        return status;
    }

    /**
     * Initialises for file encoding. {@code progressCb} may be {@code null}.
     *
     * @param path       destination file path; created or truncated
     * @param progressCb optional progress notification callback
     * @throws FlacException.FlacInitException if initialisation fails
     */
    public int initFile(Path path, ProgressCallback progressCb) {
        checkOpen();
        cbProgress = progressCb != null ? makeProgressUpcall(progressCb) : MemorySegment.NULL;
        MemorySegment cPath = arena.allocateFrom(path.toAbsolutePath().toString());
        int status = StreamEncoderH.initFile(peer, cPath, cbProgress, MemorySegment.NULL);
        checkInitStatus(status, "file: " + path);
        return status;
    }

    /**
     * Initialises for Ogg-FLAC file encoding.
     *
     * @throws FlacException.FlacInitException if initialisation fails
     */
    public int initOggFile(Path path, ProgressCallback progressCb) {
        checkOpen();
        cbProgress = progressCb != null ? makeProgressUpcall(progressCb) : MemorySegment.NULL;
        MemorySegment cPath = arena.allocateFrom(path.toAbsolutePath().toString());
        int status = StreamEncoderH.initOggFile(peer, cPath, cbProgress, MemorySegment.NULL);
        checkInitStatus(status, "ogg-file: " + path);
        return status;
    }

    // -----------------------------------------------------------------------
    // Encoding
    // -----------------------------------------------------------------------

    /**
     * Encodes a block of interleaved PCM samples.
     *
     * <p>The array must have exactly {@code samples * channels} elements.
     * The encoder copies from the Java array into native memory for each call;
     * for maximum throughput on large files, prefer larger blocks.
     *
     * @param interleaved interleaved int32 PCM (L0, R0, L1, R1, …)
     * @param samples     number of inter-channel sample frames
     * @return {@code true} on success
     * @throws FlacException.FlacEncodingException if encoding fails
     */
    public boolean processInterleaved(int[] interleaved, int samples) {
        checkOpen();
        if (interleaved == null) throw new NullPointerException("interleaved must not be null");
        if (samples < 0) throw new IllegalArgumentException("samples must be >= 0");
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment seg = tmp.allocateFrom(ValueLayout.JAVA_INT, interleaved);
            boolean ok = StreamEncoderH.processInterleaved(peer, seg, samples);
            if (!ok) throw new FlacException.FlacEncodingException(
                    "processInterleaved failed: " + getStateString());
            return true;
        }
    }

    /**
     * Convenience: encode the entire interleaved array as one block.
     *
     * @param interleaved interleaved PCM; length must be divisible by {@link #getChannels()}
     */
    public boolean processInterleaved(int[] interleaved) {
        checkOpen();
        int channels = getChannels();
        if (channels == 0) throw new IllegalStateException(
                "channels not set; call setChannels() before encoding");
        if (interleaved.length % channels != 0)
            throw new IllegalArgumentException(
                    "interleaved.length (" + interleaved.length
                    + ") is not divisible by channels (" + channels + ")");
        return processInterleaved(interleaved, interleaved.length / channels);
    }

    /**
     * Encodes a block of per-channel PCM samples.
     *
     * @param channelBuffers array of per-channel arrays, each {@code samples} long;
     *                       array length must equal {@link #getChannels()}
     * @param samples        number of sample frames per channel
     * @return {@code true} on success
     * @throws FlacException.FlacEncodingException if encoding fails
     */
    public boolean process(int[][] channelBuffers, int samples) {
        checkOpen();
        if (channelBuffers == null) throw new NullPointerException("channelBuffers must not be null");
        int channels = channelBuffers.length;
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment[] nativeChannels = new MemorySegment[channels];
            for (int c = 0; c < channels; c++) {
                nativeChannels[c] = tmp.allocateFrom(ValueLayout.JAVA_INT, channelBuffers[c]);
            }
            MemorySegment ptrArray = tmp.allocate(ValueLayout.ADDRESS, channels);
            for (int c = 0; c < channels; c++) {
                ptrArray.setAtIndex(ValueLayout.ADDRESS, c, nativeChannels[c]);
            }
            boolean ok = StreamEncoderH.process(peer, ptrArray, samples);
            if (!ok) throw new FlacException.FlacEncodingException(
                    "process failed: " + getStateString());
            return true;
        }
    }

    /**
     * Flushes buffered audio, writes the seek table, and finalises the stream.
     *
     * <p>Returns {@code false} if the internal MD5 verify check failed (only
     * relevant when {@link #setVerify(boolean) verify} is enabled).
     *
     * <p>After {@code finish()} the encoder cannot be reused; {@link #close()}
     * it or use try-with-resources.
     */
    public boolean finish() {
        checkOpen();
        finishCalled = true;
        return StreamEncoderH.finish(peer);
    }

    /**
     * Returns the raw native {@code FLAC__StreamEncoder*} peer.
     *
     * @apiNote Use only when calling a {@link StreamEncoderH} method not exposed here.
     */
    public MemorySegment peer() { return peer; }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private void checkOpen() {
        if (closed) throw new IllegalStateException("FlacStreamEncoder is closed");
    }

    private static void checkInitStatus(int status, String ctx) {
        if (status != StreamEncoderH.INIT_STATUS_OK) {
            throw new FlacException.FlacInitException(
                    "FLAC__stream_encoder_init failed for " + ctx, status);
        }
    }

    // -----------------------------------------------------------------------
    // Upcall stub factories
    // -----------------------------------------------------------------------
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private MemorySegment makeWriteUpcall(WriteCallback cb) {
        try {
            MethodHandle mh = LOOKUP.findVirtual(WriteCallback.class, "write",
                    MethodType.methodType(int.class,
                            MemorySegment.class, long.class, int.class, int.class)).bindTo(cb);
            // Native: (encoder*, byte*, size_t, uint32, uint32, clientData*) -> int
            MethodHandle adapted = MethodHandles.dropArguments(
                    MethodHandles.dropArguments(mh, 4, MemorySegment.class), 0, MemorySegment.class);
            return FlacLibrary.linker().upcallStub(adapted, StreamEncoderH.FD_WRITE_CALLBACK, arena);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private MemorySegment makeSeekUpcall(SeekCallback cb) {
        try {
            MethodHandle mh = LOOKUP.findVirtual(SeekCallback.class, "seek",
                    MethodType.methodType(int.class, long.class)).bindTo(cb);
            MethodHandle adapted = MethodHandles.dropArguments(
                    MethodHandles.dropArguments(mh, 1, MemorySegment.class), 0, MemorySegment.class);
            return FlacLibrary.linker().upcallStub(adapted, StreamEncoderH.FD_SEEK_CALLBACK, arena);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private MemorySegment makeTellUpcall(TellCallback cb) {
        try {
            MethodHandle mh = LOOKUP.findVirtual(TellCallback.class, "tell",
                    MethodType.methodType(int.class, MemorySegment.class)).bindTo(cb);
            MethodHandle adapted = MethodHandles.dropArguments(
                    MethodHandles.dropArguments(mh, 1, MemorySegment.class), 0, MemorySegment.class);
            return FlacLibrary.linker().upcallStub(adapted, StreamEncoderH.FD_TELL_CALLBACK, arena);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private MemorySegment makeMetaUpcall(MetadataCallback cb) {
        try {
            MethodHandle mh = LOOKUP.findVirtual(MetadataCallback.class, "metadata",
                    MethodType.methodType(void.class, MemorySegment.class)).bindTo(cb);
            MethodHandle adapted = MethodHandles.dropArguments(
                    MethodHandles.dropArguments(mh, 1, MemorySegment.class), 0, MemorySegment.class);
            return FlacLibrary.linker().upcallStub(adapted, StreamEncoderH.FD_METADATA_CALLBACK, arena);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private MemorySegment makeProgressUpcall(ProgressCallback cb) {
        try {
            MethodHandle mh = LOOKUP.findVirtual(ProgressCallback.class, "progress",
                    MethodType.methodType(void.class, long.class, long.class, int.class, int.class)).bindTo(cb);
            // Native: (encoder*, uint64, uint64, uint32, uint32, clientData*) -> void
            MethodHandle adapted = MethodHandles.dropArguments(
                    MethodHandles.dropArguments(mh, 4, MemorySegment.class), 0, MemorySegment.class);
            return FlacLibrary.linker().upcallStub(adapted, StreamEncoderH.FD_PROGRESS_CALLBACK, arena);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private MemorySegment makeOggReadUpcall(FlacStreamDecoder.ReadCallback cb) {
        try {
            MethodHandle mh = LOOKUP.findVirtual(FlacStreamDecoder.ReadCallback.class, "read",
                    MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class)).bindTo(cb);
            // Ogg read: (encoder*, buf*, size_t*, clientData*) -> int
            MethodHandle adapted = MethodHandles.dropArguments(
                    MethodHandles.dropArguments(mh, 3, MemorySegment.class), 0, MemorySegment.class);
            return FlacLibrary.linker().upcallStub(adapted, StreamEncoderH.FD_READ_CALLBACK, arena);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
