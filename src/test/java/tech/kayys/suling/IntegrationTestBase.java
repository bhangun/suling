package tech.kayys.suling;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import tech.kayys.suling.encoder.FlacStreamEncoder;
import tech.kayys.suling.encoder.StreamEncoderH;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Base class for integration tests that require libFLAC to be installed.
 *
 * <p>Tests extending this class are skipped when
 * {@code -Dflac.test.integration=false} (the default), and run when
 * {@code -Dflac.test.integration=true} or when {@code -Pintegration} is active.
 *
 * <p>A small FLAC file ({@link #SAMPLE_FLAC}) is generated in a temporary
 * directory in {@link #setUp()} and is available to all subclass test methods.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class IntegrationTestBase {

    /** Skip marker: set to true if libFLAC could not be loaded. */
    private static boolean SKIP = false;

    /** Standard CD-quality test format. */
    protected static final FlacAudioFormat FORMAT = FlacAudioFormat.CD_QUALITY;

    /** Sample rate for the test file. */
    protected static final int SAMPLE_RATE = 44_100;

    /** Number of channels. */
    protected static final int CHANNELS = 2;

    /** Bits per sample. */
    protected static final int BITS_PER_SAMPLE = 16;

    /** Total number of PCM sample frames in the test file (1 second). */
    protected static final int NUM_FRAMES = SAMPLE_RATE;

    /** Interleaved PCM data for the test file (440 Hz sine wave). */
    protected static final int[] TEST_PCM;

    /** Path to the test FLAC file created in setUp(). */
    protected Path SAMPLE_FLAC;

    /** Temporary directory for test artifacts. */
    protected Path tempDir;

    static {
        TEST_PCM = generateSine(440.0, SAMPLE_RATE, CHANNELS, NUM_FRAMES);
    }

    @BeforeAll
    void setUp() throws IOException {
        // Check if integration tests are enabled
        String prop = System.getProperty("flac.test.integration", "false");
        if (!Boolean.parseBoolean(prop)) {
            SKIP = true;
            return;
        }

        // Check libFLAC availability
        try {
            // Touch FlacLibrary to trigger static init
            tech.kayys.suling.internal.FlacLibrary.loadSource();
        } catch (Throwable t) {
            SKIP = true;
            return;
        }

        tempDir = Files.createTempDirectory("flac-ffm-test-");
        SAMPLE_FLAC = tempDir.resolve("sample.flac");
        writeSampleFile(SAMPLE_FLAC);
    }

    protected void assumeIntegration() {
        org.junit.jupiter.api.Assumptions.assumeTrue(!SKIP,
                "Integration tests disabled (set -Dflac.test.integration=true or -Pintegration)");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Generates an interleaved int32 sine wave at {@code hz} Hz.
     */
    public static int[] generateSine(double hz, int sampleRate, int channels, int frames) {
        int[] pcm = new int[frames * channels];
        for (int i = 0; i < frames; i++) {
            short s = (short) (Short.MAX_VALUE * Math.sin(2 * Math.PI * hz * i / sampleRate));
            for (int ch = 0; ch < channels; ch++) {
                pcm[i * channels + ch] = s;
            }
        }
        return pcm;
    }

    /**
     * Encodes {@link #TEST_PCM} to the given path using default settings.
     */
    protected static void writeSampleFile(Path dest) {
        try (var enc = new FlacStreamEncoder()) {
            enc.applyFormat(FORMAT)
               .setCompressionLevel(5)
               .setVerify(false);

            int status = enc.initFile(dest, null);
            if (status != StreamEncoderH.INIT_STATUS_OK)
                throw new RuntimeException("Failed to init encoder: " + status);

            enc.processInterleaved(TEST_PCM);
            enc.finish();
        }
    }
}
