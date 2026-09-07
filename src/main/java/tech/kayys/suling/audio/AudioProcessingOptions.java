package tech.kayys.suling.audio;

/**
 * Codec-neutral PCM post-processing controls.
 */
public record AudioProcessingOptions(
        boolean enabled,
        boolean removeDcOffset,
        double fadeInSeconds,
        double fadeOutSeconds,
        double gainDb,
        Double peakNormalizeDbfs,
        double maxNormalizeGainDb,
        boolean trimSilence,
        double trimSilenceThresholdDbfs,
        double trimSilencePaddingSeconds) {

    public AudioProcessingOptions {
        if (!Double.isFinite(fadeInSeconds) || fadeInSeconds < 0.0) {
            throw new IllegalArgumentException("fadeInSeconds must be finite and >= 0");
        }
        if (!Double.isFinite(fadeOutSeconds) || fadeOutSeconds < 0.0) {
            throw new IllegalArgumentException("fadeOutSeconds must be finite and >= 0");
        }
        if (!Double.isFinite(gainDb)) {
            throw new IllegalArgumentException("gainDb must be finite");
        }
        if (peakNormalizeDbfs != null && (!Double.isFinite(peakNormalizeDbfs) || peakNormalizeDbfs > 0.0)) {
            throw new IllegalArgumentException("peakNormalizeDbfs must be finite and <= 0 dBFS");
        }
        if (!Double.isFinite(maxNormalizeGainDb) || maxNormalizeGainDb < 0.0) {
            throw new IllegalArgumentException("maxNormalizeGainDb must be finite and >= 0");
        }
        if (!Double.isFinite(trimSilenceThresholdDbfs) || trimSilenceThresholdDbfs >= 0.0) {
            throw new IllegalArgumentException("trimSilenceThresholdDbfs must be finite and < 0 dBFS");
        }
        if (!Double.isFinite(trimSilencePaddingSeconds) || trimSilencePaddingSeconds < 0.0) {
            throw new IllegalArgumentException("trimSilencePaddingSeconds must be finite and >= 0");
        }
    }

    public static AudioProcessingOptions none() {
        return builder().enabled(false).build();
    }

    public static AudioProcessingOptions speechPolish() {
        return builder()
                .removeDcOffset(true)
                .fadeInSeconds(0.003)
                .fadeOutSeconds(0.012)
                .peakNormalizeDbfs(-3.0)
                .maxNormalizeGainDb(9.0)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean enabled = true;
        private boolean removeDcOffset;
        private double fadeInSeconds;
        private double fadeOutSeconds;
        private double gainDb;
        private Double peakNormalizeDbfs;
        private double maxNormalizeGainDb = 9.0;
        private boolean trimSilence;
        private double trimSilenceThresholdDbfs = -48.0;
        private double trimSilencePaddingSeconds = 0.025;

        public Builder enabled(boolean value) {
            this.enabled = value;
            return this;
        }

        public Builder removeDcOffset(boolean value) {
            this.removeDcOffset = value;
            return this;
        }

        public Builder fadeInSeconds(double value) {
            this.fadeInSeconds = value;
            return this;
        }

        public Builder fadeOutSeconds(double value) {
            this.fadeOutSeconds = value;
            return this;
        }

        public Builder gainDb(double value) {
            this.gainDb = value;
            return this;
        }

        public Builder peakNormalizeDbfs(Double value) {
            this.peakNormalizeDbfs = value;
            return this;
        }

        public Builder maxNormalizeGainDb(double value) {
            this.maxNormalizeGainDb = value;
            return this;
        }

        public Builder trimSilence(boolean value) {
            this.trimSilence = value;
            return this;
        }

        public Builder trimSilenceThresholdDbfs(double value) {
            this.trimSilenceThresholdDbfs = value;
            return this;
        }

        public Builder trimSilencePaddingSeconds(double value) {
            this.trimSilencePaddingSeconds = value;
            return this;
        }

        public AudioProcessingOptions build() {
            return new AudioProcessingOptions(
                    enabled,
                    removeDcOffset,
                    fadeInSeconds,
                    fadeOutSeconds,
                    gainDb,
                    peakNormalizeDbfs,
                    maxNormalizeGainDb,
                    trimSilence,
                    trimSilenceThresholdDbfs,
                    trimSilencePaddingSeconds);
        }
    }
}
