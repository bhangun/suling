package tech.kayys.suling.decoder;

import tech.kayys.suling.format.FlacFormat;

import java.lang.foreign.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * Utility methods for extracting PCM audio data from the native pointers
 * delivered to {@link FlacStreamDecoder.WriteCallback}.
 *
 * <p>In the write callback, {@code frame} is a {@code FLAC__Frame*} describing
 * the block geometry, and {@code buffers} is a
 * {@code const FLAC__int32 * const[]} — an array of {@code channels} pointers,
 * each pointing to {@code blocksize} consecutive {@code int32} samples.
 *
 * <h2>Performance notes</h2>
 * <ul>
 *   <li>Methods that return a {@code ByteBuffer} or {@code IntBuffer} avoid
 *       heap allocation by wrapping native memory directly (zero-copy within
 *       the callback's scope). The buffers are only valid inside the callback
 *       invocation — do not store them.</li>
 *   <li>Methods that return Java arrays ({@code int[]}, {@code short[]}) always
 *       copy data to the heap. Use the buffer variants for hot paths.</li>
 * </ul>
 *
 * @since 1.5.0
 */
public final class FlacPcmUtils {

    private FlacPcmUtils() {}

    // -----------------------------------------------------------------------
    // Frame header accessors
    // -----------------------------------------------------------------------

    /**
     * Returns the number of inter-channel sample frames in this block (blocksize).
     */
    public static int getBlocksize(MemorySegment frame) {
        return (int) FlacFormat.FRAME_HEADER_BLOCKSIZE.get(frame, 0L);
    }

    /**
     * Returns the number of audio channels.
     */
    public static int getChannels(MemorySegment frame) {
        return (int) FlacFormat.FRAME_HEADER_CHANNELS.get(frame, 0L);
    }

    /**
     * Returns the sample rate of this block in Hz.
     */
    public static int getSampleRate(MemorySegment frame) {
        return (int) FlacFormat.FRAME_HEADER_SAMPLE_RATE.get(frame, 0L);
    }

    /**
     * Returns bits per sample of this block.
     */
    public static int getBitsPerSample(MemorySegment frame) {
        return (int) FlacFormat.FRAME_HEADER_BITS_PER_SAMPLE.get(frame, 0L);
    }

    /**
     * Returns the frame number (valid when
     * {@link FlacFormat.FrameNumberType#FRAME_NUMBER} is the number type).
     */
    public static int getFrameNumber(MemorySegment frame) {
        return (int) FlacFormat.FRAME_HEADER_NUMBER.get(frame, 0L);
    }

    /**
     * Returns the sample number of the first sample in this block (valid when
     * {@link FlacFormat.FrameNumberType#SAMPLE_NUMBER} is the number type).
     */
    public static long getSampleNumber(MemorySegment frame) {
        return (long) FlacFormat.FRAME_HEADER_NUMBER.get(frame, 0L);
    }

    /**
     * Returns the frame number type constant
     * ({@link FlacFormat.FrameNumberType#FRAME_NUMBER} or
     * {@link FlacFormat.FrameNumberType#SAMPLE_NUMBER}).
     */
    public static int getNumberType(MemorySegment frame) {
        return (int) FlacFormat.FRAME_HEADER_NUMBER_TYPE.get(frame, 0L);
    }

    /**
     * Returns the channel assignment constant
     * (one of {@link FlacFormat.ChannelAssignment}).
     */
    public static int getChannelAssignment(MemorySegment frame) {
        return (int) FlacFormat.FRAME_HEADER_CHANNEL_ASSIGNMENT.get(frame, 0L);
    }

    // -----------------------------------------------------------------------
    // Channel buffer extraction – zero-copy view variants
    // -----------------------------------------------------------------------

    /**
     * Returns a <em>direct, native-memory-backed</em> {@link IntBuffer} for
     * the given channel.
     *
     * <p><strong>The buffer is valid only within the write callback invocation.</strong>
     * Do not escape it. For a safe copy, use {@link #extractChannelInt32(MemorySegment, int, int)}.
     *
     * @param buffers  the channel-buffer-pointer array from the write callback
     * @param channel  zero-based channel index
     * @param blocksize number of samples in the block (from {@link #getBlocksize})
     */
    public static IntBuffer channelBufferView(MemorySegment buffers, int channel, int blocksize) {
        MemorySegment seg = buffers.getAtIndex(ValueLayout.ADDRESS, channel)
                .reinterpret((long) blocksize * Integer.BYTES);
        return seg.asByteBuffer().order(ByteOrder.nativeOrder()).asIntBuffer();
    }

    // -----------------------------------------------------------------------
    // Interleaved extraction (heap-copy)
    // -----------------------------------------------------------------------

    /**
     * Extracts all channels as an interleaved {@code int[]} (L0, R0, L1, R1, …).
     *
     * @param frame   the {@code FLAC__Frame*}
     * @param buffers the channel buffer pointer array
     * @return interleaved int32 samples; length = {@code blocksize * channels}
     */
    public static int[] extractInterleavedInt32(MemorySegment frame, MemorySegment buffers) {
        int blocksize = getBlocksize(frame);
        int channels  = getChannels(frame);

        int[] output = new int[blocksize * channels];
        for (int ch = 0; ch < channels; ch++) {
            IntBuffer src = channelBufferView(buffers, ch, blocksize);
            for (int s = 0; s < blocksize; s++) {
                output[s * channels + ch] = src.get(s);
            }
        }
        return output;
    }

    /**
     * Fills {@code dest} starting at {@code offset} with interleaved samples.
     * The caller is responsible for ensuring {@code dest} has enough capacity.
     *
     * @param frame   the {@code FLAC__Frame*}
     * @param buffers the channel buffer pointer array
     * @param dest    destination array
     * @param offset  start index in {@code dest}
     * @return number of int elements written ({@code blocksize * channels})
     */
    public static int extractInterleavedInt32Into(MemorySegment frame, MemorySegment buffers,
                                                   int[] dest, int offset) {
        int blocksize = getBlocksize(frame);
        int channels  = getChannels(frame);
        for (int ch = 0; ch < channels; ch++) {
            IntBuffer src = channelBufferView(buffers, ch, blocksize);
            for (int s = 0; s < blocksize; s++) {
                dest[offset + s * channels + ch] = src.get(s);
            }
        }
        return blocksize * channels;
    }

    // -----------------------------------------------------------------------
    // Per-channel extraction
    // -----------------------------------------------------------------------

    /**
     * Extracts one channel as a new {@code int[]}.
     *
     * @param buffers  channel buffer pointer array
     * @param channel  zero-based channel index
     * @param blocksize number of samples per channel
     */
    public static int[] extractChannelInt32(MemorySegment buffers, int channel, int blocksize) {
        IntBuffer src = channelBufferView(buffers, channel, blocksize);
        int[] out = new int[blocksize];
        src.get(out);
        return out;
    }

    /**
     * Extracts all channels as {@code int[channels][blocksize]}.
     *
     * @param frame   the {@code FLAC__Frame*}
     * @param buffers the channel buffer pointer array
     * @return {@code int[channels][blocksize]}
     */
    public static int[][] extractPerChannelInt32(MemorySegment frame, MemorySegment buffers) {
        int blocksize = getBlocksize(frame);
        int channels  = getChannels(frame);
        int[][] out = new int[channels][blocksize];
        for (int ch = 0; ch < channels; ch++) {
            channelBufferView(buffers, ch, blocksize).get(out[ch]);
        }
        return out;
    }

    // -----------------------------------------------------------------------
    // Short (16-bit) extraction
    // -----------------------------------------------------------------------

    /**
     * Extracts interleaved 16-bit samples, scaling from {@code bitsPerSample}.
     *
     * <ul>
     *   <li>If {@code bitsPerSample > 16}: right-shift to truncate.</li>
     *   <li>If {@code bitsPerSample == 16}: straight cast.</li>
     *   <li>If {@code bitsPerSample < 16}: left-shift to expand.</li>
     * </ul>
     *
     * @param frame         the {@code FLAC__Frame*}
     * @param buffers       channel buffer pointer array
     * @param bitsPerSample bits per sample (from {@link #getBitsPerSample})
     */
    public static short[] extractInterleavedShort(MemorySegment frame, MemorySegment buffers,
                                                   int bitsPerSample) {
        int blocksize = getBlocksize(frame);
        int channels  = getChannels(frame);
        int total     = blocksize * channels;
        short[] out   = new short[total];
        int shift     = bitsPerSample - 16;

        for (int ch = 0; ch < channels; ch++) {
            IntBuffer src = channelBufferView(buffers, ch, blocksize);
            if (shift > 0) {
                for (int s = 0; s < blocksize; s++) out[s * channels + ch] = (short) (src.get(s) >> shift);
            } else if (shift < 0) {
                int lshift = -shift;
                for (int s = 0; s < blocksize; s++) out[s * channels + ch] = (short) (src.get(s) << lshift);
            } else {
                for (int s = 0; s < blocksize; s++) out[s * channels + ch] = (short) src.get(s);
            }
        }
        return out;
    }

    /**
     * Fills an existing {@link ShortBuffer} with interleaved 16-bit samples.
     *
     * @param frame         the {@code FLAC__Frame*}
     * @param buffers       channel buffer pointer array
     * @param bitsPerSample bits per sample
     * @param dest          destination buffer (must have remaining capacity for
     *                      {@code blocksize * channels} shorts)
     */
    public static void extractInterleavedShortInto(MemorySegment frame, MemorySegment buffers,
                                                    int bitsPerSample, ShortBuffer dest) {
        int blocksize = getBlocksize(frame);
        int channels  = getChannels(frame);
        int shift     = bitsPerSample - 16;

        for (int ch = 0; ch < channels; ch++) {
            IntBuffer src = channelBufferView(buffers, ch, blocksize);
            int base = dest.position();
            if (shift > 0) {
                for (int s = 0; s < blocksize; s++) dest.put(base + s * channels + ch, (short) (src.get(s) >> shift));
            } else if (shift < 0) {
                int lshift = -shift;
                for (int s = 0; s < blocksize; s++) dest.put(base + s * channels + ch, (short) (src.get(s) << lshift));
            } else {
                for (int s = 0; s < blocksize; s++) dest.put(base + s * channels + ch, (short) src.get(s));
            }
        }
        dest.position(dest.position() + blocksize * channels);
    }

    // -----------------------------------------------------------------------
    // ByteBuffer (big-endian / WAV-like) extraction
    // -----------------------------------------------------------------------

    /**
     * Extracts interleaved 16-bit PCM as a big-endian {@link ByteBuffer}
     * (suitable for writing WAV data or streaming over a network).
     *
     * <p>The returned buffer is positioned at 0 with limit set to the data length.
     *
     * @param frame         the {@code FLAC__Frame*}
     * @param buffers       channel buffer pointer array
     * @param bitsPerSample bits per sample (must be &le; 32)
     * @param bigEndian     {@code true} for big-endian byte order (e.g. AIFF/network);
     *                      {@code false} for little-endian (e.g. WAV/PCM)
     */
    public static ByteBuffer extractInterleavedBytes16(MemorySegment frame, MemorySegment buffers,
                                                        int bitsPerSample, boolean bigEndian) {
        short[] shorts = extractInterleavedShort(frame, buffers, bitsPerSample);
        ByteBuffer bb = ByteBuffer.allocate(shorts.length * Short.BYTES)
                .order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        bb.asShortBuffer().put(shorts);
        bb.rewind();
        return bb;
    }
}
