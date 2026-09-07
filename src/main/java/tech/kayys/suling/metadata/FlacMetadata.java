package tech.kayys.suling.metadata;

import tech.kayys.suling.FlacAudioFormat;
import tech.kayys.suling.FlacException;
import tech.kayys.suling.format.FlacFormat;

import java.lang.foreign.*;
import java.nio.file.Path;

/**
 * High-level Java API for the three libFLAC metadata interfaces.
 *
 * <h2>Level 0 – fast read-only access</h2>
 * <pre>{@code
 * FlacMetadata.StreamInfo info = FlacMetadata.getStreamInfo(Path.of("song.flac"));
 * System.out.printf("Duration: %.2f s%n", info.durationSeconds());
 * }</pre>
 *
 * <h2>Level 1 – simple iterator (read / replace blocks)</h2>
 * <pre>{@code
 * try (var it = new FlacMetadata.SimpleIterator(Path.of("song.flac"), true)) {
 *     do {
 *         System.out.println(FlacFormat.MetadataType.name(it.getBlockType())
 *                 + " @ offset " + it.getBlockOffset());
 *     } while (it.next());
 * }
 * }</pre>
 *
 * <h2>Level 2 – full chain (add / remove / reorder blocks)</h2>
 * <pre>{@code
 * try (var chain = new FlacMetadata.Chain()) {
 *     chain.read(Path.of("song.flac"));
 *     try (var it = chain.iterator()) {
 *         do {
 *             if (it.getBlockType() == FlacFormat.MetadataType.VORBIS_COMMENT) {
 *                 MemorySegment block = it.getBlock(); // owned by chain
 *                 // manipulate Vorbis tags via MetadataH.*
 *             }
 *         } while (it.next());
 *     }
 *     chain.mergePadding();
 *     chain.write(true, true);
 * }
 * }</pre>
 *
 * @since 1.5.0
 */
public final class FlacMetadata {

    private FlacMetadata() {}

    // -----------------------------------------------------------------------
    // StreamInfo record (Level 0)
    // -----------------------------------------------------------------------

    /**
     * Immutable snapshot of a {@code FLAC__StreamMetadata_StreamInfo} block.
     *
     * @param minBlocksize  minimum block size in samples
     * @param maxBlocksize  maximum block size in samples
     * @param minFramesize  minimum encoded frame size in bytes (0 = unknown)
     * @param maxFramesize  maximum encoded frame size in bytes (0 = unknown)
     * @param sampleRate    sample rate in Hz
     * @param channels      number of audio channels (1–8)
     * @param bitsPerSample bits per sample (4–32)
     * @param totalSamples  total samples per channel (0 = unknown)
     * @param md5sum        16-byte MD5 signature of the unencoded audio data
     */
    public record StreamInfo(
            int minBlocksize,
            int maxBlocksize,
            int minFramesize,
            int maxFramesize,
            int sampleRate,
            int channels,
            int bitsPerSample,
            long totalSamples,
            byte[] md5sum) {

        /**
         * Duration in seconds, or {@code Double.NaN} if {@link #totalSamples} is 0.
         */
        public double durationSeconds() {
            return totalSamples == 0 ? Double.NaN : (double) totalSamples / sampleRate;
        }

        /**
         * Converts this stream info to a {@link FlacAudioFormat}.
         * {@code totalSamples} is carried over.
         */
        public FlacAudioFormat toAudioFormat() {
            return new FlacAudioFormat(channels, bitsPerSample, sampleRate, totalSamples);
        }

        /**
         * Returns a hex string representation of {@link #md5sum}.
         */
        public String md5Hex() {
            StringBuilder sb = new StringBuilder(32);
            for (byte b : md5sum) sb.append(String.format("%02x", b & 0xFF));
            return sb.toString();
        }
    }

    // -----------------------------------------------------------------------
    // Level 0 – fast read-only helpers
    // -----------------------------------------------------------------------

    /**
     * Reads the STREAMINFO block from a FLAC file without decoding audio.
     *
     * @param flacFile path to an existing FLAC file
     * @return an immutable {@link StreamInfo} snapshot
     * @throws FlacException.FlacMetadataException if the file cannot be read
     */
    public static StreamInfo getStreamInfo(Path flacFile) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment filename = arena.allocateFrom(flacFile.toAbsolutePath().toString());
            MemorySegment infoStruct = arena.allocate(FlacFormat.STREAM_INFO_LAYOUT);

            if (!MetadataH.metadataGetStreaminfo(filename, infoStruct)) {
                throw new FlacException.FlacMetadataException(
                        "FLAC__metadata_get_streaminfo failed for: " + flacFile);
            }

            int  minBlocksize  = (int)  FlacFormat.STREAM_INFO_MIN_BLOCKSIZE.get(infoStruct, 0L);
            int  maxBlocksize  = (int)  FlacFormat.STREAM_INFO_MAX_BLOCKSIZE.get(infoStruct, 0L);
            int  sampleRate    = (int)  FlacFormat.STREAM_INFO_SAMPLE_RATE.get(infoStruct, 0L);
            int  channels      = (int)  FlacFormat.STREAM_INFO_CHANNELS.get(infoStruct, 0L);
            int  bps           = (int)  FlacFormat.STREAM_INFO_BITS_PER_SAMPLE.get(infoStruct, 0L);
            long totalSamples  = (long) FlacFormat.STREAM_INFO_TOTAL_SAMPLES.get(infoStruct, 0L);

            byte[] md5 = new byte[16];
            long md5Offset = FlacFormat.STREAM_INFO_LAYOUT.byteOffset(
                    MemoryLayout.PathElement.groupElement("md5sum"));
            for (int i = 0; i < 16; i++) {
                md5[i] = infoStruct.get(ValueLayout.JAVA_BYTE, md5Offset + i);
            }

            return new StreamInfo(minBlocksize, maxBlocksize, 0, 0,
                    sampleRate, channels, bps, totalSamples, md5);
        }
    }

    /**
     * Retrieves the raw Vorbis comment (tags) block.
     *
     * <p><strong>The caller is responsible for freeing the returned pointer
     * with {@link #objectDelete(MemorySegment)}.</strong>
     *
     * @return native {@code FLAC__StreamMetadata*}, or {@link MemorySegment#NULL} if absent
     */
    public static MemorySegment getTagsRaw(Path flacFile) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment filename = arena.allocateFrom(flacFile.toAbsolutePath().toString());
            MemorySegment ptrSlot  = arena.allocate(ValueLayout.ADDRESS);
            if (!MetadataH.metadataGetTags(filename, ptrSlot)) return MemorySegment.NULL;
            return ptrSlot.get(ValueLayout.ADDRESS, 0).reinterpret(Long.MAX_VALUE);
        }
    }

    // -----------------------------------------------------------------------
    // Object lifecycle helpers
    // -----------------------------------------------------------------------

    /**
     * Creates a new {@code FLAC__StreamMetadata} of the given type.
     * Must be freed with {@link #objectDelete} unless handed to the chain.
     *
     * @param metadataType one of {@link FlacFormat.MetadataType}
     */
    public static MemorySegment newObject(int metadataType) {
        MemorySegment obj = MetadataH.objectNew(metadataType);
        if (obj.equals(MemorySegment.NULL))
            throw new OutOfMemoryError("FLAC__metadata_object_new returned NULL");
        return obj.reinterpret(Long.MAX_VALUE);
    }

    /** Deep-clones a {@code FLAC__StreamMetadata*}. Caller must free the clone. */
    public static MemorySegment cloneObject(MemorySegment meta) {
        MemorySegment clone = MetadataH.objectClone(meta);
        if (clone.equals(MemorySegment.NULL))
            throw new OutOfMemoryError("FLAC__metadata_object_clone returned NULL");
        return clone.reinterpret(Long.MAX_VALUE);
    }

    /** Frees a {@code FLAC__StreamMetadata*} allocated by libFLAC. */
    public static void objectDelete(MemorySegment meta) {
        MetadataH.objectDelete(meta);
    }

    // -----------------------------------------------------------------------
    // Level 1 – Simple Iterator
    // -----------------------------------------------------------------------

    /**
     * Wraps {@code FLAC__Metadata_SimpleIterator} with {@link AutoCloseable}.
     *
     * <p>The iterator is positioned at the first metadata block on construction.
     * Double-close is safe.
     */
    public static final class SimpleIterator implements AutoCloseable {

        private final MemorySegment peer;
        private final Arena arena;
        private boolean closed = false;

        /**
         * @param flacFile path to the FLAC file
         * @param readOnly {@code true} for read-only access (no file locking on Linux)
         * @throws FlacException.FlacMetadataException if the file cannot be opened
         */
        public SimpleIterator(Path flacFile, boolean readOnly) {
            peer = MetadataH.simpleIteratorNew();
            if (peer.equals(MemorySegment.NULL))
                throw new OutOfMemoryError("FLAC__metadata_simple_iterator_new returned NULL");
            arena = Arena.ofConfined();

            MemorySegment filename = arena.allocateFrom(flacFile.toAbsolutePath().toString());
            if (!MetadataH.simpleIteratorInit(peer, filename, readOnly, false)) {
                int status = MetadataH.simpleIteratorStatus(peer);
                MetadataH.simpleIteratorDelete(peer);
                arena.close();
                throw new FlacException.FlacMetadataException(
                        "SimpleIterator init failed for " + flacFile + ", status=" + status);
            }
        }

        public int     getStatus()       { checkOpen(); return MetadataH.simpleIteratorStatus(peer); }
        public boolean isWritable()      { checkOpen(); return MetadataH.simpleIteratorIsWritable(peer); }
        public boolean next()            { checkOpen(); return MetadataH.simpleIteratorNext(peer); }
        public boolean prev()            { checkOpen(); return MetadataH.simpleIteratorPrev(peer); }
        public boolean isLast()          { checkOpen(); return MetadataH.simpleIteratorIsLast(peer); }
        public long    getBlockOffset()  { checkOpen(); return MetadataH.simpleIteratorGetBlockOffset(peer); }
        public int     getBlockType()    { checkOpen(); return MetadataH.simpleIteratorGetBlockType(peer); }
        public int     getBlockLength()  { checkOpen(); return MetadataH.simpleIteratorGetBlockLength(peer); }

        /**
         * Reads the current block into a new native object.
         * Caller must free it with {@link FlacMetadata#objectDelete(MemorySegment)}.
         */
        public MemorySegment getBlock() {
            checkOpen();
            return MetadataH.simpleIteratorGetBlock(peer).reinterpret(Long.MAX_VALUE);
        }

        public boolean setBlock(MemorySegment block, boolean usePadding) {
            checkOpen(); return MetadataH.simpleIteratorSetBlock(peer, block, usePadding);
        }
        public boolean insertBlockAfter(MemorySegment block, boolean usePadding) {
            checkOpen(); return MetadataH.simpleIteratorInsertBlockAfter(peer, block, usePadding);
        }
        public boolean deleteBlock(boolean usePadding) {
            checkOpen(); return MetadataH.simpleIteratorDeleteBlock(peer, usePadding);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            MetadataH.simpleIteratorDelete(peer);
            arena.close();
        }

        private void checkOpen() {
            if (closed) throw new IllegalStateException("SimpleIterator is closed");
        }
    }

    // -----------------------------------------------------------------------
    // Level 2 – Chain
    // -----------------------------------------------------------------------

    /**
     * Wraps {@code FLAC__Metadata_Chain} with {@link AutoCloseable}.
     * Double-close is safe.
     */
    public static final class Chain implements AutoCloseable {

        private final MemorySegment peer;
        private final Arena arena;
        private boolean closed = false;

        public Chain() {
            peer = MetadataH.chainNew();
            if (peer.equals(MemorySegment.NULL))
                throw new OutOfMemoryError("FLAC__metadata_chain_new returned NULL");
            arena = Arena.ofConfined();
        }

        /**
         * Reads all metadata blocks from a FLAC file into the chain.
         *
         * @throws FlacException.FlacMetadataException on failure
         */
        public boolean read(Path flacFile) {
            checkOpen();
            MemorySegment fn = arena.allocateFrom(flacFile.toAbsolutePath().toString());
            boolean ok = MetadataH.chainRead(peer, fn);
            if (!ok) throw new FlacException.FlacMetadataException(
                    "Chain read failed for " + flacFile + ", status=" + getStatus());
            return true;
        }

        /**
         * Reads all metadata blocks from an Ogg-FLAC file.
         *
         * @throws FlacException.FlacMetadataException on failure
         */
        public boolean readOgg(Path oggFlacFile) {
            checkOpen();
            MemorySegment fn = arena.allocateFrom(oggFlacFile.toAbsolutePath().toString());
            boolean ok = MetadataH.chainReadOgg(peer, fn);
            if (!ok) throw new FlacException.FlacMetadataException(
                    "Chain readOgg failed for " + oggFlacFile + ", status=" + getStatus());
            return true;
        }

        public int getStatus() { checkOpen(); return MetadataH.chainStatus(peer); }

        /**
         * Writes the chain back to the file in-place.
         *
         * @param usePadding      reuse existing PADDING blocks to avoid rewriting the audio data
         * @param preserveStats   restore original file timestamps and permissions after writing
         * @throws FlacException.FlacMetadataException on failure
         */
        public boolean write(boolean usePadding, boolean preserveStats) {
            checkOpen();
            boolean ok = MetadataH.chainWrite(peer, usePadding, preserveStats);
            if (!ok) throw new FlacException.FlacMetadataException(
                    "Chain write failed, status=" + getStatus());
            return true;
        }

        /** Coalesces adjacent PADDING blocks into one. */
        public void mergePadding() { checkOpen(); MetadataH.chainMergePadding(peer); }

        /** Moves all PADDING blocks to the end of the chain. */
        public void sortPadding()  { checkOpen(); MetadataH.chainSortPadding(peer); }

        /**
         * Returns {@code true} if writing requires a temporary file
         * (e.g. when not using padding and the file needs to grow).
         */
        public boolean checkIfTempfileNeeded(boolean usePadding) {
            checkOpen(); return MetadataH.chainCheckIfTempfileNeeded(peer, usePadding);
        }

        /**
         * Creates a new {@link ChainIterator} positioned at the first block.
         * The iterator borrows a reference to this chain and must be closed before the chain.
         */
        public ChainIterator iterator() { checkOpen(); return new ChainIterator(peer); }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            MetadataH.chainDelete(peer);
            arena.close();
        }

        private void checkOpen() {
            if (closed) throw new IllegalStateException("Chain is closed");
        }

        /** Returns the raw peer for advanced usage. */
        MemorySegment peer() { return peer; }
    }

    // -----------------------------------------------------------------------
    // Level 2 – Chain Iterator
    // -----------------------------------------------------------------------

    /**
     * Wraps {@code FLAC__Metadata_Iterator} (Level 2).
     *
     * <p><strong>Must be closed before the {@link Chain} it was created from.</strong>
     * Double-close is safe.
     */
    public static final class ChainIterator implements AutoCloseable {

        private final MemorySegment peer;
        private boolean closed = false;

        ChainIterator(MemorySegment chain) {
            peer = MetadataH.chainIteratorNew();
            if (peer.equals(MemorySegment.NULL))
                throw new OutOfMemoryError("FLAC__metadata_iterator_new returned NULL");
            MetadataH.chainIteratorInit(peer, chain);
        }

        public boolean next()  { checkOpen(); return MetadataH.chainIteratorNext(peer); }
        public boolean prev()  { checkOpen(); return MetadataH.chainIteratorPrev(peer); }
        public int getBlockType() { checkOpen(); return MetadataH.chainIteratorGetBlockType(peer); }

        /**
         * Returns the current block pointer (owned by the chain — do <strong>not</strong> free it).
         */
        public MemorySegment getBlock() {
            checkOpen();
            return MetadataH.chainIteratorGetBlock(peer).reinterpret(Long.MAX_VALUE);
        }

        /**
         * Replaces the current block. The chain takes ownership of {@code newBlock}.
         */
        public boolean setBlock(MemorySegment newBlock) {
            checkOpen(); return MetadataH.chainIteratorSetBlock(peer, newBlock);
        }
        public boolean deleteBlock(boolean usePadding) {
            checkOpen(); return MetadataH.chainIteratorDeleteBlock(peer, usePadding);
        }
        public boolean insertBefore(MemorySegment block) {
            checkOpen(); return MetadataH.chainIteratorInsertBefore(peer, block);
        }
        public boolean insertAfter(MemorySegment block) {
            checkOpen(); return MetadataH.chainIteratorInsertAfter(peer, block);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            MetadataH.chainIteratorDelete(peer);
        }

        private void checkOpen() {
            if (closed) throw new IllegalStateException("ChainIterator is closed");
        }
    }

    // -----------------------------------------------------------------------
    // Backward compatibility alias (kept for source compatibility with 1.5.0)
    // -----------------------------------------------------------------------
}
