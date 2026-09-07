package tech.kayys.suling.audio;

/**
 * Codec-neutral description of interleaved linear PCM audio.
 *
 * @param channels number of interleaved channels
 * @param sampleRate sample rate in hertz
 * @param sampleFormat PCM sample representation
 * @param frames sample frames per channel, or {@code 0} when unknown
 */
public record PcmAudioFormat(
        int channels,
        int sampleRate,
        PcmSampleFormat sampleFormat,
        long frames) {

    public PcmAudioFormat {
        if (channels < 1 || channels > 64) {
            throw new IllegalArgumentException("channels must be between 1 and 64");
        }
        if (sampleRate < 1 || sampleRate > 1_048_575) {
            throw new IllegalArgumentException("sampleRate must be positive and FLAC-compatible");
        }
        if (sampleFormat == null) {
            sampleFormat = PcmSampleFormat.S16_LE;
        }
        if (frames < 0) {
            throw new IllegalArgumentException("frames must be >= 0");
        }
    }

    public int bitsPerSample() {
        return sampleFormat.bitsPerSample();
    }

    public int bytesPerSample() {
        return sampleFormat.bytesPerSample();
    }

    public int frameSizeBytes() {
        return channels * bytesPerSample();
    }

    public long byteRate() {
        return (long) sampleRate * frameSizeBytes();
    }

    public double durationSeconds() {
        return frames == 0 ? Double.NaN : frames / (double) sampleRate;
    }

    public PcmAudioFormat withFrames(long newFrames) {
        return new PcmAudioFormat(channels, sampleRate, sampleFormat, newFrames);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int channels = 2;
        private int sampleRate = 48_000;
        private PcmSampleFormat sampleFormat = PcmSampleFormat.S16_LE;
        private long frames = 0;

        public Builder channels(int value) {
            this.channels = value;
            return this;
        }

        public Builder sampleRate(int value) {
            this.sampleRate = value;
            return this;
        }

        public Builder sampleFormat(PcmSampleFormat value) {
            this.sampleFormat = value;
            return this;
        }

        public Builder frames(long value) {
            this.frames = value;
            return this;
        }

        public Builder durationSeconds(double value) {
            if (Double.isNaN(value) || value < 0.0) {
                throw new IllegalArgumentException("durationSeconds must be >= 0");
            }
            this.frames = Math.round(value * sampleRate);
            return this;
        }

        public PcmAudioFormat build() {
            return new PcmAudioFormat(channels, sampleRate, sampleFormat, frames);
        }
    }
}
