package tech.kayys.suling.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Small PCM processing stage shared by encoders and model runners.
 */
final class PcmAudioProcessor {
    private PcmAudioProcessor() {
    }

    static PcmAudio process(PcmAudio audio, AudioProcessingOptions options) {
        if (audio == null) {
            throw new NullPointerException("audio");
        }
        AudioProcessingOptions effective = options == null ? AudioProcessingOptions.none() : options;
        if (!effective.enabled()) {
            return audio;
        }
        if (audio.format().sampleFormat() != PcmSampleFormat.S16_LE) {
            throw new MediaCodecException("PCM processing currently supports S16_LE only");
        }

        int channels = audio.format().channels();
        int frames = Math.toIntExact(audio.format().frames());
        if (channels <= 0 || frames <= 0) {
            return audio;
        }

        double[] samples = readInterleavedS16(audio.data());
        double peakBefore = peak(samples);
        int originalFrames = frames;
        int trimStartFrames = 0;
        int trimEndFrames = 0;

        if (effective.removeDcOffset()) {
            removeDcOffset(samples, frames, channels);
        }
        if (effective.trimSilence()) {
            TrimmedSamples trimmed = trimSilence(
                    samples,
                    frames,
                    channels,
                    audio.format().sampleRate(),
                    effective.trimSilenceThresholdDbfs(),
                    effective.trimSilencePaddingSeconds());
            samples = trimmed.samples();
            frames = trimmed.frames();
            trimStartFrames = trimmed.startFrames();
            trimEndFrames = trimmed.endFrames();
        }
        applyFades(samples, frames, channels, audio.format().sampleRate(), effective);
        applyGain(samples, dbToLinear(effective.gainDb()));

        double normalizeGainDb = 0.0;
        if (effective.peakNormalizeDbfs() != null) {
            double peak = peak(samples);
            if (peak > 0.0) {
                double target = 32767.0 * dbToLinear(effective.peakNormalizeDbfs());
                double requestedScale = target / peak;
                double maxScale = dbToLinear(effective.maxNormalizeGainDb());
                double scale = Math.min(requestedScale, maxScale);
                applyGain(samples, scale);
                normalizeGainDb = linearToDb(scale);
            }
        }

        double peakAfter = peak(samples);
        byte[] processed = writeInterleavedS16(samples);
        Map<String, String> metadata = new LinkedHashMap<>(audio.metadata());
        metadata.put("audio_processing", "true");
        metadata.put("audio_processing_remove_dc", String.valueOf(effective.removeDcOffset()));
        metadata.put("audio_processing_gain_db", formatDb(effective.gainDb()));
        metadata.put("audio_processing_normalize_gain_db", formatDb(normalizeGainDb));
        metadata.put("audio_processing_peak_before_dbfs", formatDb(amplitudeToDbfs(peakBefore)));
        metadata.put("audio_processing_peak_after_dbfs", formatDb(amplitudeToDbfs(peakAfter)));
        metadata.put("audio_processing_fade_in_ms", formatMs(effective.fadeInSeconds()));
        metadata.put("audio_processing_fade_out_ms", formatMs(effective.fadeOutSeconds()));
        metadata.put("audio_processing_trim_silence", String.valueOf(effective.trimSilence()));
        if (effective.trimSilence()) {
            metadata.put("audio_processing_trim_threshold_dbfs", formatDb(effective.trimSilenceThresholdDbfs()));
            metadata.put("audio_processing_trim_padding_ms", formatMs(effective.trimSilencePaddingSeconds()));
            metadata.put("audio_processing_trim_start_ms", formatFrameMs(trimStartFrames, audio.format().sampleRate()));
            metadata.put("audio_processing_trim_end_ms", formatFrameMs(trimEndFrames, audio.format().sampleRate()));
            metadata.put("audio_processing_trim_removed_ms",
                    formatFrameMs(originalFrames - frames, audio.format().sampleRate()));
        }
        if (effective.peakNormalizeDbfs() != null) {
            metadata.put("audio_processing_peak_target_dbfs", formatDb(effective.peakNormalizeDbfs()));
        }
        return new PcmAudio(processed, audio.format().withFrames(frames), metadata);
    }

    private static double[] readInterleavedS16(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        double[] samples = new double[data.length / Short.BYTES];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = buffer.getShort(i * Short.BYTES);
        }
        return samples;
    }

    private static byte[] writeInterleavedS16(double[] samples) {
        ByteBuffer buffer = ByteBuffer.allocate(samples.length * Short.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (double sample : samples) {
            int pcm = (int) Math.round(Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample)));
            buffer.putShort((short) pcm);
        }
        return buffer.array();
    }

    private static void removeDcOffset(double[] samples, int frames, int channels) {
        double[] means = new double[channels];
        for (int frame = 0; frame < frames; frame++) {
            int offset = frame * channels;
            for (int channel = 0; channel < channels; channel++) {
                means[channel] += samples[offset + channel];
            }
        }
        for (int channel = 0; channel < channels; channel++) {
            means[channel] /= frames;
        }
        for (int frame = 0; frame < frames; frame++) {
            int offset = frame * channels;
            for (int channel = 0; channel < channels; channel++) {
                samples[offset + channel] -= means[channel];
            }
        }
    }

    private static TrimmedSamples trimSilence(
            double[] samples,
            int frames,
            int channels,
            int sampleRate,
            double thresholdDbfs,
            double paddingSeconds) {
        if (frames <= 0 || channels <= 0) {
            return new TrimmedSamples(samples, frames, 0, 0);
        }

        double threshold = 32767.0 * dbToLinear(thresholdDbfs);
        int firstAudible = -1;
        int lastAudible = -1;
        for (int frame = 0; frame < frames; frame++) {
            if (framePeak(samples, frame, channels) > threshold) {
                if (firstAudible < 0) {
                    firstAudible = frame;
                }
                lastAudible = frame;
            }
        }
        if (firstAudible < 0 || lastAudible < firstAudible) {
            return new TrimmedSamples(samples, frames, 0, 0);
        }

        int paddingFrames = Math.max(0, (int) Math.round(paddingSeconds * sampleRate));
        int start = Math.max(0, firstAudible - paddingFrames);
        int endExclusive = Math.min(frames, lastAudible + paddingFrames + 1);
        if (start == 0 && endExclusive == frames) {
            return new TrimmedSamples(samples, frames, 0, 0);
        }

        int newFrames = Math.max(0, endExclusive - start);
        double[] trimmed = new double[newFrames * channels];
        System.arraycopy(samples, start * channels, trimmed, 0, trimmed.length);
        return new TrimmedSamples(trimmed, newFrames, start, frames - endExclusive);
    }

    private static double framePeak(double[] samples, int frame, int channels) {
        int offset = frame * channels;
        double peak = 0.0;
        for (int channel = 0; channel < channels; channel++) {
            peak = Math.max(peak, Math.abs(samples[offset + channel]));
        }
        return peak;
    }

    private static void applyFades(
            double[] samples,
            int frames,
            int channels,
            int sampleRate,
            AudioProcessingOptions options) {
        int fadeInFrames = Math.min(frames, Math.max(0, (int) Math.round(options.fadeInSeconds() * sampleRate)));
        int fadeOutFrames = Math.min(frames, Math.max(0, (int) Math.round(options.fadeOutSeconds() * sampleRate)));
        for (int frame = 0; frame < fadeInFrames; frame++) {
            double gain = fadeGain(frame, fadeInFrames);
            applyFrameGain(samples, frame, channels, gain);
        }
        for (int frame = Math.max(0, frames - fadeOutFrames); frame < frames; frame++) {
            int fadeIndex = frames - frame - 1;
            double gain = fadeGain(fadeIndex, fadeOutFrames);
            applyFrameGain(samples, frame, channels, gain);
        }
    }

    private static double fadeGain(int index, int length) {
        if (length <= 1) {
            return 0.0;
        }
        double position = Math.max(0.0, Math.min(1.0, index / (double) (length - 1)));
        return Math.sin(position * Math.PI * 0.5);
    }

    private static void applyFrameGain(double[] samples, int frame, int channels, double gain) {
        int offset = frame * channels;
        for (int channel = 0; channel < channels; channel++) {
            samples[offset + channel] *= gain;
        }
    }

    private static void applyGain(double[] samples, double gain) {
        if (Math.abs(gain - 1.0) < 1.0e-12) {
            return;
        }
        for (int i = 0; i < samples.length; i++) {
            samples[i] *= gain;
        }
    }

    private static double peak(double[] samples) {
        double peak = 0.0;
        for (double sample : samples) {
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }

    private static double dbToLinear(double db) {
        return Math.pow(10.0, db / 20.0);
    }

    private static double linearToDb(double value) {
        return value <= 0.0 ? Double.NEGATIVE_INFINITY : 20.0 * Math.log10(value);
    }

    private static double amplitudeToDbfs(double amplitude) {
        return amplitude <= 0.0 ? Double.NEGATIVE_INFINITY : 20.0 * Math.log10(amplitude / 32767.0);
    }

    private static String formatDb(double value) {
        if (!Double.isFinite(value)) {
            return String.valueOf(value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatMs(double seconds) {
        return String.format(Locale.ROOT, "%.1f", seconds * 1000.0);
    }

    private static String formatFrameMs(int frames, int sampleRate) {
        return String.format(Locale.ROOT, "%.1f", frames * 1000.0 / Math.max(1, sampleRate));
    }

    private record TrimmedSamples(double[] samples, int frames, int startFrames, int endFrames) {
    }
}
