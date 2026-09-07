package tech.kayys.suling;

import tech.kayys.suling.internal.FlacLibrary;

/**
 * Utility class for checking libFLAC library availability and version compatibility.
 *
 * <p>This class provides methods to verify that a compatible version of libFLAC
 * is available before attempting to use the encoder/decoder APIs. This is especially
 * useful for applications that need to provide helpful error messages or fallback
 * behavior when libFLAC is not installed or is too old.</p>
 *
 * <h2>Example usage</h2>
 * <pre>{@code
 * // Check if libFLAC is available and meets minimum requirements
 * if (!FlacLibraryCheck.isAvailable()) {
 *     System.err.println("libFLAC not found. Please install libFLAC 1.4+");
 *     System.err.println("  Debian/Ubuntu: sudo apt install libflac-dev");
 *     System.err.println("  macOS: brew install flac");
 *     System.err.println("  Fedora/RHEL: sudo dnf install flac-devel");
 *     return;
 * }
 *
 * // Check minimum version
 * if (!FlacLibraryCheck.isVersionAtLeast(1, 4, 0)) {
 *     System.err.println("libFLAC 1.4.0+ required, found: " + FlacLibraryCheck.getVersion());
 *     return;
 * }
 *
 * // Safe to use the encoder/decoder APIs
 * try (var enc = new FlacStreamEncoder()) {
 *     // ... encode audio ...
 * }
 * }</pre>
 *
 * <h2>Diagnostic information</h2>
 * <pre>{@code
 * System.out.println("libFLAC version: " + FlacLibraryCheck.getVersion());
 * System.out.println("Loaded from: " + FlacLibraryCheck.getLoadSource());
 * System.out.println("Version details: " + FlacLibraryCheck.getVersionMajor() + "."
 *     + FlacLibraryCheck.getVersionMinor() + "." + FlacLibraryCheck.getVersionPatch());
 * }</pre>
 *
 * @since 0.1.0
 */
public final class FlacLibraryCheck {

    private FlacLibraryCheck() {
        // Utility class - prevent instantiation
    }

    /**
     * Returns {@code true} if libFLAC was successfully loaded.
     *
     * <p>This method attempts to load libFLAC if it hasn't been loaded yet.
     * If loading fails, the failure is cached and all subsequent calls return
     * {@code false}.</p>
     *
     * @return true if libFLAC is available and ready to use
     */
    public static boolean isAvailable() {
        try {
            // Force class initialization if not already loaded
            FlacLibrary.lookup();
            return FlacLibrary.hasSymbol("FLAC__stream_encoder_new")
                    && FlacLibrary.hasSymbol("FLAC__stream_decoder_new")
                    && FlacLibrary.hasSymbol("FLAC__stream_encoder_process_interleaved");
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            return false;
        }
    }

    /**
     * Returns the version string of the loaded libFLAC library.
     *
     * @return version string in "major.minor.patch" format, or "unknown" if not loaded
     * @see #isAvailable()
     */
    public static String getVersion() {
        return isAvailable() ? FlacLibrary.libraryVersion() : "not loaded";
    }

    /**
     * Returns the major version number of the loaded libFLAC library.
     *
     * @return major version number, or 0 if not loaded
     */
    public static int getVersionMajor() {
        return isAvailable() ? FlacLibrary.libraryVersionMajor() : 0;
    }

    /**
     * Returns the minor version number of the loaded libFLAC library.
     *
     * @return minor version number, or 0 if not loaded
     */
    public static int getVersionMinor() {
        return isAvailable() ? FlacLibrary.libraryVersionMinor() : 0;
    }

    /**
     * Returns the patch version number of the loaded libFLAC library.
     *
     * @return patch version number, or 0 if not loaded
     */
    public static int getVersionPatch() {
        return isAvailable() ? FlacLibrary.libraryVersionPatch() : 0;
    }

    /**
     * Checks if the loaded libFLAC version meets or exceeds the specified minimum.
     *
     * @param minMajor minimum major version
     * @param minMinor minimum minor version
     * @param minPatch minimum patch version
     * @return true if libFLAC is available and version >= minimum
     * @see #isAvailable()
     */
    public static boolean isVersionAtLeast(int minMajor, int minMinor, int minPatch) {
        if (!isAvailable()) {
            return false;
        }
        return FlacLibrary.isLibraryVersionAtLeast(minMajor, minMinor, minPatch);
    }

    /**
     * Returns a description of how libFLAC was located and loaded.
     *
     * @return load source description, or "not loaded" if unavailable
     * @see #isAvailable()
     */
    public static String getLoadSource() {
        return isAvailable() ? FlacLibrary.loadSource() : "not loaded";
    }

    /**
     * Returns detailed diagnostic information about the libFLAC library.
     *
     * @return multi-line string with version and load information
     */
    public static String getDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("libFLAC Diagnostics\n");
        sb.append("===================\n");

        if (isAvailable()) {
            sb.append("Status: Available\n");
            sb.append("Version: ").append(getVersion()).append("\n");
            sb.append("Load source: ").append(getLoadSource()).append("\n");
            sb.append("Version breakdown: ").append(getVersionMajor())
                    .append(".").append(getVersionMinor())
                    .append(".").append(getVersionPatch()).append("\n");

            // Check for common symbols
            sb.append("\nSymbol availability:\n");
            checkSymbol(sb, "FLAC__stream_decoder_new");
            checkSymbol(sb, "FLAC__stream_encoder_new");
            checkSymbol(sb, "FLAC__stream_encoder_process_interleaved");
            checkSymbol(sb, "FLAC__metadata_chain_new");
        } else {
            sb.append("Status: NOT AVAILABLE\n");
            sb.append("\nTroubleshooting:\n");
            sb.append("1. Ensure libFLAC 1.4+ is installed on your system\n");
            sb.append("2. Set -Dflac.library.path=/path/to/libFLAC if needed\n");
            sb.append("3. Check that the library is compatible with your OS/architecture\n");
        }

        return sb.toString();
    }

    private static void checkSymbol(StringBuilder sb, String symbol) {
        boolean available = FlacLibrary.hasSymbol(symbol);
        sb.append("  ").append(symbol).append(": ")
                .append(available ? "OK" : "MISSING").append("\n");
    }
}
