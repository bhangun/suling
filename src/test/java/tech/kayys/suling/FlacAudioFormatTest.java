package tech.kayys.suling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FlacAudioFormat} — no libFLAC dependency required.
 */
class FlacAudioFormatTest {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    @Test
    void cdQualityConstant() {
        var fmt = FlacAudioFormat.CD_QUALITY;
        assertEquals(2,      fmt.channels());
        assertEquals(16,     fmt.bitsPerSample());
        assertEquals(44_100, fmt.sampleRate());
        assertEquals(0,      fmt.totalSamples());
    }

    @Test
    void studio48kConstant() {
        var fmt = FlacAudioFormat.STUDIO_48K;
        assertEquals(2,      fmt.channels());
        assertEquals(24,     fmt.bitsPerSample());
        assertEquals(48_000, fmt.sampleRate());
    }

    @Test
    void hires96kConstant() {
        var fmt = FlacAudioFormat.HIRES_96K;
        assertEquals(2,      fmt.channels());
        assertEquals(24,     fmt.bitsPerSample());
        assertEquals(96_000, fmt.sampleRate());
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    @Test
    void builderDefaultsProduceCdQuality() {
        var fmt = FlacAudioFormat.builder().build();
        assertEquals(FlacAudioFormat.CD_QUALITY, fmt);
    }

    @Test
    void builderRoundtrip() {
        var fmt = FlacAudioFormat.builder()
                .channels(1)
                .bitsPerSample(24)
                .sampleRate(48_000)
                .totalSamples(48_000L * 120)
                .build();
        assertEquals(1,      fmt.channels());
        assertEquals(24,     fmt.bitsPerSample());
        assertEquals(48_000, fmt.sampleRate());
        assertEquals(48_000L * 120, fmt.totalSamples());
    }

    @Test
    void builderDurationSetsCorrectTotalSamples() {
        // Build with sampleRate first so duration() can compute totalSamples correctly
        var fmt = FlacAudioFormat.builder()
                .sampleRate(44_100)
                .duration(10.0)
                .build();
        // duration(10.0) at 44100 Hz → totalSamples = round(441000) = 441000
        assertEquals(441_000L, fmt.totalSamples());
    }

    @Test
    void toBuilderPreservesAllFields() {
        var original = new FlacAudioFormat(6, 24, 96_000, 100_000L);
        var copy = original.toBuilder().build();
        assertEquals(original, copy);
    }

    // -----------------------------------------------------------------------
    // Derived properties
    // -----------------------------------------------------------------------

    @Test
    void durationSeconds_knownTotalSamples() {
        var fmt = new FlacAudioFormat(2, 16, 44_100, 44_100L);
        assertEquals(1.0, fmt.durationSeconds(), 1e-9);
    }

    @Test
    void durationSeconds_unknownTotalSamples_returnsNaN() {
        var fmt = new FlacAudioFormat(2, 16, 44_100, 0);
        assertTrue(Double.isNaN(fmt.durationSeconds()));
    }

    @Test
    void byteRate_stereo16bit44100() {
        // 2 ch * 2 bytes * 44100 Hz = 176400
        var fmt = FlacAudioFormat.CD_QUALITY;
        assertEquals(176_400L, fmt.byteRate());
    }

    @Test
    void byteRate_mono24bit48k() {
        var fmt = new FlacAudioFormat(1, 24, 48_000, 0);
        assertEquals(1L * 3 * 48_000, fmt.byteRate());
    }

    // -----------------------------------------------------------------------
    // Validation – channels
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 6, 8})
    void validChannels(int ch) {
        assertDoesNotThrow(() -> new FlacAudioFormat(ch, 16, 44_100, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 9, 100})
    void invalidChannels_throws(int ch) {
        assertThrows(IllegalArgumentException.class,
                () -> new FlacAudioFormat(ch, 16, 44_100, 0));
    }

    // -----------------------------------------------------------------------
    // Validation – bitsPerSample
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {4, 8, 16, 20, 24, 32})
    void validBitsPerSample(int bps) {
        assertDoesNotThrow(() -> new FlacAudioFormat(2, bps, 44_100, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 33, 64})
    void invalidBitsPerSample_throws(int bps) {
        assertThrows(IllegalArgumentException.class,
                () -> new FlacAudioFormat(2, bps, 44_100, 0));
    }

    // -----------------------------------------------------------------------
    // Validation – sampleRate
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({"8000", "11025", "22050", "44100", "48000", "96000", "192000", "1048575"})
    void validSampleRates(int sr) {
        assertDoesNotThrow(() -> new FlacAudioFormat(2, 16, sr, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 1_048_576})
    void invalidSampleRate_throws(int sr) {
        assertThrows(IllegalArgumentException.class,
                () -> new FlacAudioFormat(2, 16, sr, 0));
    }

    // -----------------------------------------------------------------------
    // Validation – totalSamples
    // -----------------------------------------------------------------------

    @Test
    void negativeTotalSamples_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new FlacAudioFormat(2, 16, 44_100, -1));
    }

    @Test
    void zeroTotalSamples_ok() {
        assertDoesNotThrow(() -> new FlacAudioFormat(2, 16, 44_100, 0));
    }

    // -----------------------------------------------------------------------
    // Builder – duration validation
    // -----------------------------------------------------------------------

    @Test
    void builderNegativeDuration_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> FlacAudioFormat.builder().sampleRate(44_100).duration(-1.0));
    }

    // -----------------------------------------------------------------------
    // Record equality & hashCode
    // -----------------------------------------------------------------------

    @Test
    void recordEquality() {
        var a = new FlacAudioFormat(2, 16, 44_100, 0);
        var b = new FlacAudioFormat(2, 16, 44_100, 0);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void recordInequality_differentChannels() {
        var a = new FlacAudioFormat(2, 16, 44_100, 0);
        var b = new FlacAudioFormat(1, 16, 44_100, 0);
        assertNotEquals(a, b);
    }
}
