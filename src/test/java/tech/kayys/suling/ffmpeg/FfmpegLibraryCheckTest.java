package tech.kayys.suling.ffmpeg;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FfmpegLibraryCheckTest {
    @Test
    void diagnosticsAreAlwaysAvailable() {
        String diagnostics = FfmpegLibraryCheck.getDiagnostics();
        assertNotNull(diagnostics);
        assertFalse(diagnostics.isBlank());
    }
}
