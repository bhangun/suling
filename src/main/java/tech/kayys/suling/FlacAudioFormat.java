package tech.kayys.suling;

import tech.kayys.suling.format.FlacFormat;

import java.util.Objects;

/**
 * Immutable value object describing the PCM audio parameters of a FLAC stream.
 *
 * <p>A {@code FlacAudioFormat} carries the four fundamental audio properties
 * (channels, bits-per-sample, sample rate, and total sample count) and provides
 * validation against the limits defined in {@link FlacFormat}.  Use the fluent
 * {@link Builder} to construct instances.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * var fmt = FlacAudioFormat.builder()
 *         .channels(2)
 *         .bitsPerSample(24)
 *         .sampleRate(96_000)
 *         .totalSamples(96_000L * 60)   // 60-second file
 *         .build();
 *
 * try (var enc = new FlacStreamEncoder()) {
 *     enc.applyFormat(fmt)
 *        .setCompressionLevel(5)
 *        .initFile(path, null);
 *     enc.processInterleaved(pcm);
 *     enc.finish();
 * }
 * }</pre>
 *
 * @param channels      number of audio channels (1–8)
 * @param bitsPerSample bits per sample (4–32)
 * @param sampleRate    sample rate in Hz (1–1,048,575)
 * @param totalSamples  total samples per channel, or 0 if unknown
 *
 * @since 0.1.0
 */
public record FlacAudioFormat(int channels, int bitsPerSample, int sampleRate, long totalSamples) {

    /** Standard CD-quality stereo format (44,100 Hz / 16-bit / 2 ch). */
    public static final FlacAudioFormat CD_QUALITY =
            new FlacAudioFormat(2, 16, 44_100, 0);

    /** Studio-quality stereo format (48,000 Hz / 24-bit / 2 ch). */
    public static final FlacAudioFormat STUDIO_48K =
            new FlacAudioFormat(2, 24, 48_000, 0);

    /** High-resolution stereo format (96,000 Hz / 24-bit / 2 ch). */
    public static final FlacAudioFormat HIRES_96K =
            new FlacAudioFormat(2, 24, 96_000, 0);

    // Compact canonical constructor with full validation
    public FlacAudioFormat {
        if (channels < 1 || channels > FlacFormat.FLAC__MAX_CHANNELS)
            throw new IllegalArgumentException(
                    "channels must be 1–" + FlacFormat.FLAC__MAX_CHANNELS + ", got: " + channels);
        if (bitsPerSample < FlacFormat.FLAC__MIN_BITS_PER_SAMPLE
                || bitsPerSample > FlacFormat.FLAC__MAX_BITS_PER_SAMPLE)
            throw new IllegalArgumentException(
                    "bitsPerSample must be " + FlacFormat.FLAC__MIN_BITS_PER_SAMPLE
                    + "–" + FlacFormat.FLAC__MAX_BITS_PER_SAMPLE + ", got: " + bitsPerSample);
        if (sampleRate < 1 || sampleRate > FlacFormat.FLAC__MAX_SAMPLE_RATE)
            throw new IllegalArgumentException(
                    "sampleRate must be 1–" + FlacFormat.FLAC__MAX_SAMPLE_RATE + ", got: " + sampleRate);
        if (totalSamples < 0)
            throw new IllegalArgumentException("totalSamples must be >= 0, got: " + totalSamples);
    }

    /**
     * Duration in seconds, or {@code Double.NaN} if {@link #totalSamples} is 0.
     */
    public double durationSeconds() {
        return totalSamples == 0 ? Double.NaN : (double) totalSamples / sampleRate;
    }

    /**
     * Uncompressed PCM byte rate: {@code channels * (bitsPerSample/8) * sampleRate}.
     */
    public long byteRate() {
        return (long) channels * ((bitsPerSample + 7) / 8) * sampleRate;
    }

    /**
     * Returns a {@link Builder} pre-populated with the values of this format.
     */
    public Builder toBuilder() {
        return new Builder()
                .channels(channels)
                .bitsPerSample(bitsPerSample)
                .sampleRate(sampleRate)
                .totalSamples(totalSamples);
    }

    /** Creates a new {@link Builder}. */
    public static Builder builder() { return new Builder(); }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    /**
     * Fluent builder for {@link FlacAudioFormat}.
     */
    public static final class Builder {
        private int channels = 2;
        private int bitsPerSample = 16;
        private int sampleRate = 44_100;
        private long totalSamples = 0;

        private Builder() {}

        /** Sets the number of audio channels (1–8). */
        public Builder channels(int v)      { this.channels = v;      return this; }

        /** Sets bits per sample (4–32). */
        public Builder bitsPerSample(int v) { this.bitsPerSample = v; return this; }

        /** Sets the sample rate in Hz. */
        public Builder sampleRate(int v)    { this.sampleRate = v;    return this; }

        /** Sets the total number of samples per channel (0 = unknown). */
        public Builder totalSamples(long v) { this.totalSamples = v;  return this; }

        /**
         * Convenience: compute {@code totalSamples} from a duration.
         *
         * @param seconds non-negative duration in seconds
         */
        public Builder duration(double seconds) {
            if (seconds < 0) throw new IllegalArgumentException("duration must be >= 0");
            this.totalSamples = Math.round(seconds * sampleRate);
            return this;
        }

        /** Builds the immutable {@link FlacAudioFormat}. */
        public FlacAudioFormat build() {
            return new FlacAudioFormat(channels, bitsPerSample, sampleRate, totalSamples);
        }
    }
}
