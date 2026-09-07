package tech.kayys.suling.io;

import tech.kayys.suling.decoder.FlacPcmUtils;
import tech.kayys.suling.decoder.FlacStreamDecoder;
import tech.kayys.suling.decoder.StreamDecoderH;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A {@link java.io.InputStream} that decodes a FLAC file and produces a
 * continuous stream of raw linear PCM bytes.
 *
 * <p>The output format is: interleaved signed integers, little-endian,
 * with a bit-depth equal to {@link #getBitsPerSample()}. For 16-bit stereo
 * this produces standard WAV-compatible PCM (4 bytes per sample frame).
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * try (var in = new FlacInputStream(Path.of("song.flac"))) {
 *     System.out.printf("Format: %d Hz / %d-bit / %d ch%n",
 *             in.getSampleRate(), in.getBitsPerSample(), in.getChannels());
 *
 *     byte[] buf = new byte[4096];
 *     int n;
 *     while ((n = in.read(buf)) != -1) {
 *         // write n bytes of PCM to your audio sink
 *     }
 * }
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * Not thread-safe. Use from a single thread.
 *
 * @since 1.5.1
 */
public final class FlacInputStream extends InputStream {

    private final FlacStreamDecoder decoder;

    /** Queue of decoded PCM byte blocks waiting to be consumed by read(). */
    private final Deque<byte[]> queue = new ArrayDeque<>();

    /** Partial consumption offset into the head element of the queue. */
    private int headOffset = 0;

    // Audio format – available after the first metadata frame is processed
    private int sampleRate;
    private int channels;
    private int bitsPerSample;
    private long totalSamples;

    private boolean initialized = false;
    private boolean endOfStream = false;
    private boolean closed      = false;

    /**
     * Opens and begins decoding the given FLAC file.
     *
     * @param flacFile path to a readable FLAC file
     * @throws IOException              if the file cannot be opened
     */
    public FlacInputStream(Path flacFile) throws IOException {
        decoder = new FlacStreamDecoder();
        decoder.setMd5Checking(false); // allow seeking / partial reads
        decoder.setMetadataRespondAll();

        try {
            decoder.initFile(flacFile,
                    this::onWrite,
                    this::onMetadata,
                    err -> { /* errors silently drain the queue */ });
        } catch (Exception e) {
            decoder.close();
            throw new IOException("Failed to open FLAC file: " + flacFile, e);
        }

        // Decode the leading metadata so format fields are populated before caller uses them
        decoder.processUntilEndOfMetadata();
        initialized = true;
    }

    // -----------------------------------------------------------------------
    // InputStream API
    // -----------------------------------------------------------------------

    /**
     * Reads up to {@code len} bytes of decoded PCM into {@code b[off..off+len)}.
     *
     * @return number of bytes read, or {@code -1} at end of stream
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        checkOpen();
        if (len == 0) return 0;

        int totalRead = 0;
        while (totalRead < len) {
            // Refill queue from decoder if empty
            while (queue.isEmpty() && !endOfStream) {
                boolean ok = decoder.processSingle();
                if (!ok || decoder.getState() == StreamDecoderH.STATE_END_OF_STREAM) {
                    endOfStream = true;
                }
            }

            if (queue.isEmpty()) {
                return totalRead == 0 ? -1 : totalRead;
            }

            byte[] head = queue.peek();
            int available = head.length - headOffset;
            int toCopy   = Math.min(available, len - totalRead);
            System.arraycopy(head, headOffset, b, off + totalRead, toCopy);
            totalRead  += toCopy;
            headOffset += toCopy;

            if (headOffset >= head.length) {
                queue.poll();
                headOffset = 0;
            }
        }
        return totalRead;
    }

    /** Reads a single byte of decoded PCM, or {@code -1} at end of stream. */
    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int n = read(b, 0, 1);
        return n == -1 ? -1 : (b[0] & 0xFF);
    }

    /**
     * Returns an estimate of available buffered bytes without blocking.
     * This is the number of bytes currently sitting in the internal PCM queue.
     */
    @Override
    public int available() {
        int total = 0;
        for (byte[] chunk : queue) total += chunk.length;
        if (!queue.isEmpty()) total -= headOffset;
        return total;
    }

    /** Closes the decoder and releases all native resources. */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        decoder.close();
        queue.clear();
    }

    // -----------------------------------------------------------------------
    // Audio format accessors (valid after construction)
    // -----------------------------------------------------------------------

    /** Returns the sample rate in Hz (e.g. 44100). */
    public int getSampleRate()    { return sampleRate; }

    /** Returns the number of audio channels (1–8). */
    public int getChannels()      { return channels; }

    /** Returns bits per sample (4–32). */
    public int getBitsPerSample() { return bitsPerSample; }

    /**
     * Returns the total number of sample frames, or 0 if not reported by the file.
     */
    public long getTotalSamples() { return totalSamples; }

    /**
     * Returns the byte width of one sample frame:
     * {@code channels * ceil(bitsPerSample / 8)}.
     */
    public int getFrameByteWidth() {
        return channels * ((bitsPerSample + 7) / 8);
    }

    // -----------------------------------------------------------------------
    // Internal callbacks
    // -----------------------------------------------------------------------

    private int onWrite(MemorySegment frame, MemorySegment buffers) {
        int bs  = FlacPcmUtils.getBlocksize(frame);
        int ch  = FlacPcmUtils.getChannels(frame);
        int bps = FlacPcmUtils.getBitsPerSample(frame);

        // Update format from the first frame (should match metadata, but defensive)
        if (!initialized) {
            sampleRate    = FlacPcmUtils.getSampleRate(frame);
            channels      = ch;
            bitsPerSample = bps;
        }

        // Convert to little-endian PCM bytes.
        // Hoist per-channel IntBuffer views outside the sample loop (O(ch) instead of O(bs*ch)).
        int bytesPerSample = (bps + 7) / 8;
        byte[] pcmBytes = new byte[bs * ch * bytesPerSample];

        java.nio.IntBuffer[] views = new java.nio.IntBuffer[ch];
        for (int c = 0; c < ch; c++) {
            views[c] = FlacPcmUtils.channelBufferView(buffers, c, bs);
        }

        for (int s = 0; s < bs; s++) {
            for (int c = 0; c < ch; c++) {
                int sample  = views[c].get(s);
                int destIdx = (s * ch + c) * bytesPerSample;
                for (int b = 0; b < bytesPerSample; b++) {
                    pcmBytes[destIdx + b] = (byte) (sample >> (8 * b));
                }
            }
        }
        queue.add(pcmBytes);
        return StreamDecoderH.WRITE_STATUS_CONTINUE;
    }

    private void onMetadata(MemorySegment meta) {
        // STREAMINFO is at type offset 0; data starts after 8-byte header
        // We read format from decoder getters (populated after processUntilEndOfMetadata)
        if (!initialized) {
            sampleRate    = decoder.getSampleRate();
            channels      = decoder.getChannels();
            bitsPerSample = decoder.getBitsPerSample();
            totalSamples  = decoder.getTotalSamples();
        }
    }

    private void checkOpen() throws IOException {
        if (closed) throw new IOException("FlacInputStream is closed");
    }
}
