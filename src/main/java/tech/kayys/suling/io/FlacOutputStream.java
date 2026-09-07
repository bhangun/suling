package tech.kayys.suling.io;

import tech.kayys.suling.FlacAudioFormat;
import tech.kayys.suling.FlacException;
import tech.kayys.suling.encoder.FlacStreamEncoder;
import tech.kayys.suling.encoder.StreamEncoderH;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.MemorySegment;

/**
 * An {@link java.io.OutputStream} that encodes raw linear PCM bytes to FLAC
 * and delivers the compressed output to a delegate {@link OutputStream}.
 *
 * <p>Input bytes must be interleaved, signed, little-endian PCM matching the
 * {@link FlacAudioFormat} supplied at construction time (same byte-width as
 * {@code ceil(bitsPerSample / 8)}).
 *
 * <h2>Usage – encode to file</h2>
 * <pre>{@code
 * var fmt = FlacAudioFormat.CD_QUALITY;
 * try (var fos  = new java.io.FileOutputStream("output.flac");
 *      var out  = new FlacOutputStream(fos, fmt, 5)) {
 *
 *     // Write raw 16-bit LE stereo PCM
 *     out.write(pcmBytes);      // byte[] from a WAV file, audio capture, etc.
 * }
 * // fos is closed automatically; FLAC stream is finalized before close()
 * }</pre>
 *
 * <h2>Usage – encode to byte array</h2>
 * <pre>{@code
 * var baos = new java.io.ByteArrayOutputStream();
 * try (var out = new FlacOutputStream(baos, FlacAudioFormat.STUDIO_48K, 5)) {
 *     out.write(pcmBytes);
 * }
 * byte[] flacData = baos.toByteArray();
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * Not thread-safe. Use from a single thread.
 *
 * @since 1.5.1
 */
public final class FlacOutputStream extends OutputStream {

    private final FlacStreamEncoder encoder;
    private final OutputStream sink;
    private final int channels;
    private final int bytesPerSample;

    /** Accumulation buffer for partial PCM frames. */
    private byte[] pending = new byte[0];
    private int pendingLen = 0;

    private boolean closed = false;
    private boolean finished = false;

    /**
     * Creates a new FLAC output stream.
     *
     * @param sink             the delegate stream to write compressed FLAC bytes to
     * @param fmt              audio format of the incoming raw PCM
     * @param compressionLevel 0 (fastest) to 8 (best compression); 5 is a good default
     * @throws IOException if the encoder cannot be initialised
     */
    public FlacOutputStream(OutputStream sink, FlacAudioFormat fmt, int compressionLevel)
            throws IOException {
        this.sink          = sink;
        this.channels      = fmt.channels();
        this.bytesPerSample = (fmt.bitsPerSample() + 7) / 8;

        encoder = new FlacStreamEncoder();
        encoder.applyFormat(fmt).setCompressionLevel(compressionLevel);

        try {
            encoder.initStream(
                    this::onWrite,
                    null,   // non-seekable → no seektable in stream mode
                    null,
                    null);
        } catch (FlacException.FlacInitException e) {
            encoder.close();
            throw new IOException("Failed to initialise FLAC encoder", e);
        }

        // Allocate a pending buffer large enough for 4096 sample frames
        pending = new byte[4096 * channels * bytesPerSample];
    }

    // -----------------------------------------------------------------------
    // OutputStream API
    // -----------------------------------------------------------------------

    /**
     * Buffers raw PCM bytes and encodes complete sample frames as they arrive.
     *
     * <p>Partial frames are held internally until enough bytes accumulate for
     * at least one complete frame ({@code channels * bytesPerSample} bytes).
     */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        checkOpen();
        if (len == 0) return;

        // Append to pending buffer
        ensurePendingCapacity(pendingLen + len);
        System.arraycopy(b, off, pending, pendingLen, len);
        pendingLen += len;

        // Flush complete frames
        int frameBytes = channels * bytesPerSample;
        int completeFrames = pendingLen / frameBytes;
        if (completeFrames > 0) {
            encodeFrames(pending, 0, completeFrames * frameBytes, completeFrames);
            int remainder = pendingLen - completeFrames * frameBytes;
            if (remainder > 0) {
                System.arraycopy(pending, completeFrames * frameBytes, pending, 0, remainder);
            }
            pendingLen = remainder;
        }
    }

    /** Delegates to {@link #write(byte[], int, int)}. */
    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    /** Writes a single byte; buffered until a full frame is available. */
    @Override
    public void write(int b) throws IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }

    /**
     * No-op flush: FLAC frames are written as they fill; call {@link #close()} to finalize.
     */
    @Override
    public void flush() throws IOException {
        checkOpen();
        sink.flush();
    }

    /**
     * Finalises the FLAC stream (writes seek table and end-of-stream markers),
     * then closes both the encoder and the delegate sink.
     *
     * <p>Any buffered partial frame is discarded. Call {@link #write} with complete
     * frames to avoid this.
     */
    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        try {
            if (!finished) {
                finished = true;
                encoder.finish();
            }
        } finally {
            encoder.close();
            sink.close();
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private void encodeFrames(byte[] data, int off, int totalBytes, int frames) throws IOException {
        // Convert little-endian bytes to signed int32 samples.
        // Sign-extend sub-32-bit values so the encoder sees correct negative numbers.
        int[] intSamples = new int[frames * channels];
        int totalBits = bytesPerSample * 8;
        for (int i = 0; i < intSamples.length; i++) {
            int base = off + i * bytesPerSample;
            int value = 0;
            for (int b = 0; b < bytesPerSample; b++) {
                value |= (data[base + b] & 0xFF) << (8 * b);
            }
            // Sign-extend only when sub-32-bit (32-bit ints are already full range)
            if (totalBits < 32) {
                int shift = 32 - totalBits;
                value = (value << shift) >> shift; // arithmetic right-shift propagates sign
            }
            intSamples[i] = value;
        }
        try {
            encoder.processInterleaved(intSamples, frames);
        } catch (FlacException.FlacEncodingException e) {
            throw new IOException("FLAC encoding error", e);
        }
    }

    private int onWrite(MemorySegment buffer, long bytes, int samples, int frame) {
        byte[] chunk = new byte[(int) bytes];
        MemorySegment.ofArray(chunk).copyFrom(buffer.reinterpret(bytes));
        try {
            sink.write(chunk);
            return StreamEncoderH.WRITE_STATUS_OK;
        } catch (IOException e) {
            return StreamEncoderH.WRITE_STATUS_FATAL_ERROR;
        }
    }

    private void ensurePendingCapacity(int required) {
        if (required > pending.length) {
            byte[] newBuf = new byte[Math.max(required, pending.length * 2)];
            System.arraycopy(pending, 0, newBuf, 0, pendingLen);
            pending = newBuf;
        }
    }

    private void checkOpen() throws IOException {
        if (closed) throw new IOException("FlacOutputStream is closed");
    }
}
