package tech.kayys.suling.internal;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Central FFM bootstrap for libFLAC.
 *
 * <p>This class is responsible for exactly one thing: finding and loading
 * {@code libFLAC} into the JVM process, then exposing the resulting
 * {@link SymbolLookup} and {@link Linker} to the rest of the library.
 *
 * <h2>Library resolution order</h2>
 * <ol>
 *   <li>The path in the JVM system property {@code flac.library.path}
 *       (if set), interpreted as an absolute or relative file path.</li>
 *   <li>{@code libFLAC.so.12} – default on modern Linux (libFLAC 1.4+).</li>
 *   <li>{@code libFLAC.so.8}  – older Linux / some embedded systems.</li>
 *   <li>{@code libFLAC}       – portable bare name (macOS, Windows with DLL).</li>
 *   <li>{@code FLAC}          – Windows MSYS2 / vcpkg convention.</li>
 *   <li>The JVM's native linker default lookup as a final fallback.</li>
 * </ol>
 *
 * <h2>Thread safety</h2>
 * All public methods are safe to call from any thread after class initialisation.
 *
 * @since 0.1.0
 */
public final class FlacLibrary {

    private static final Logger LOG = Logger.getLogger(FlacLibrary.class.getName());

    private static final Arena GLOBAL = Arena.global();
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP;
    private static final String LOAD_SOURCE;
    private static final String LIBRARY_VERSION;
    private static final int LIBRARY_VERSION_MAJOR;
    private static final int LIBRARY_VERSION_MINOR;
    private static final int LIBRARY_VERSION_PATCH;

    static {
        String customPath = System.getProperty("flac.library.path");
        SymbolLookup lookup = null;
        String source = null;

        if (customPath != null) {
            try {
                lookup = SymbolLookup.libraryLookup(Path.of(customPath), GLOBAL);
                source = "explicit path: " + customPath;
            } catch (IllegalArgumentException | UnsatisfiedLinkError e) {
                LOG.warning("flac.library.path set but could not load '" + customPath + "': " + e.getMessage());
            }
        }

        if (lookup == null) {
            String[] candidates = {
                    "libFLAC.so.12",
                    "libFLAC.so.8",
                    "libFLAC.dylib",
                    "libFLAC.14.dylib",
                    "libFLAC",
                    "FLAC",
                    "/opt/homebrew/lib/libFLAC.dylib",
                    "/opt/homebrew/lib/libFLAC.14.dylib",
                    "/usr/local/lib/libFLAC.dylib",
                    "/usr/local/lib/libFLAC.14.dylib"
            };
            for (String name : candidates) {
                Optional<SymbolLookup> opt = tryLoad(name);
                if (opt.isPresent()) {
                    lookup = opt.get();
                    source = "auto-detected: " + name;
                    break;
                }
            }
        }

        if (lookup == null) {
            lookup = LINKER.defaultLookup();
            source = "native linker default lookup";
        }

        LOOKUP = lookup;
        LOAD_SOURCE = source;
        
        // Detect library version
        String version = "unknown";
        int major = 0, minor = 0, patch = 0;
        try {
            // Try to get version string from FLAC__VERSION_STRING symbol
            Optional<MemorySegment> versionSeg = LOOKUP.find("FLAC__VERSION_STRING");
            if (versionSeg.isPresent()) {
                MemorySegment versionPtr = versionSeg.get()
                        .reinterpret(ValueLayout.ADDRESS.byteSize())
                        .get(ValueLayout.ADDRESS, 0)
                        .reinterpret(64);
                version = versionPtr.getString(0);
                // Parse version from format "1.4.3" or similar
                String[] parts = version.split("\\.");
                if (parts.length >= 3) {
                    major = Integer.parseInt(parts[0]);
                    minor = Integer.parseInt(parts[1]);
                    patch = Integer.parseInt(parts[2].split("-")[0]); // Handle "-rc1" etc.
                }
            }
        } catch (Exception e) {
            LOG.warning("Could not detect libFLAC version: " + e.getMessage());
        }
        
        LIBRARY_VERSION = version;
        LIBRARY_VERSION_MAJOR = major;
        LIBRARY_VERSION_MINOR = minor;
        LIBRARY_VERSION_PATCH = patch;
        
        LOG.info("libFLAC loaded via " + LOAD_SOURCE + " (version: " + version + ")");
    }

    private static Optional<SymbolLookup> tryLoad(String name) {
        try {
            if (name.contains("/") || name.contains("\\")) {
                return Optional.of(SymbolLookup.libraryLookup(Path.of(name), GLOBAL));
            }
            return Optional.of(SymbolLookup.libraryLookup(name, GLOBAL));
        } catch (IllegalArgumentException | UnsatisfiedLinkError ignored) {
            return Optional.empty();
        }
    }

    private FlacLibrary() {}

    public static Linker linker() { return LINKER; }
    public static SymbolLookup lookup() { return LOOKUP; }
    public static Arena globalArena() { return GLOBAL; }

    /**
     * Returns a human-readable description of how libFLAC was located.
     * Useful for diagnostic logging.
     */
    public static String loadSource() { return LOAD_SOURCE; }

    /**
     * Returns the version string of the loaded libFLAC library (e.g., "1.4.3"),
     * or "unknown" if the version could not be determined.
     *
     * @return version string in "major.minor.patch" format
     * @since 0.1.0
     */
    public static String libraryVersion() { return LIBRARY_VERSION; }

    /**
     * Returns the major version number of the loaded libFLAC library.
     *
     * @return major version number, or 0 if unknown
     * @since 0.1.0
     */
    public static int libraryVersionMajor() { return LIBRARY_VERSION_MAJOR; }

    /**
     * Returns the minor version number of the loaded libFLAC library.
     *
     * @return minor version number, or 0 if unknown
     * @since 0.1.0
     */
    public static int libraryVersionMinor() { return LIBRARY_VERSION_MINOR; }

    /**
     * Returns the patch version number of the loaded libFLAC library.
     *
     * @return patch version number, or 0 if unknown
     * @since 0.1.0
     */
    public static int libraryVersionPatch() { return LIBRARY_VERSION_PATCH; }

    /**
     * Checks if the loaded libFLAC version meets the minimum requirements.
     *
     * @param minMajor minimum major version required
     * @param minMinor minimum minor version required
     * @param minPatch minimum patch version required
     * @return true if the loaded version is >= the minimum required
     * @since 0.1.0
     */
    public static boolean isLibraryVersionAtLeast(int minMajor, int minMinor, int minPatch) {
        if (LIBRARY_VERSION_MAJOR < minMajor) return false;
        if (LIBRARY_VERSION_MAJOR > minMajor) return true;
        if (LIBRARY_VERSION_MINOR < minMinor) return false;
        if (LIBRARY_VERSION_MINOR > minMinor) return true;
        return LIBRARY_VERSION_PATCH >= minPatch;
    }

    /**
     * Returns {@code true} if the named symbol exists in the loaded library.
     * Useful for optional feature detection (e.g. newer libFLAC additions).
     */
    public static boolean hasSymbol(String symbol) {
        return LOOKUP.find(symbol).isPresent();
    }

    /**
     * Looks up {@code symbol} and returns its native address.
     *
     * @throws UnsatisfiedLinkError if the symbol is absent
     */
    public static MemorySegment findOrThrow(String symbol) {
        return LOOKUP.find(symbol)
                .orElseThrow(() -> new UnsatisfiedLinkError(
                        "libFLAC symbol not found: '" + symbol
                        + "'. Loaded via: " + LOAD_SOURCE));
    }

    /**
     * Creates a downcall {@link MethodHandle} for {@code symbol}.
     *
     * @throws UnsatisfiedLinkError if the symbol is absent
     */
    public static MethodHandle downcall(String symbol, FunctionDescriptor fd) {
        return LINKER.downcallHandle(findOrThrow(symbol), fd);
    }

    /**
     * Creates a downcall handle for {@code symbol} if present; otherwise empty.
     */
    public static Optional<MethodHandle> downcallOpt(String symbol, FunctionDescriptor fd) {
        return LOOKUP.find(symbol).map(addr -> LINKER.downcallHandle(addr, fd));
    }

    // -----------------------------------------------------------------------
    // ValueLayout type aliases (mirrors libFLAC C ABI types)
    // -----------------------------------------------------------------------

    /**
     * {@code FLAC__bool} — libFLAC defines this as {@code int} (32-bit), not C99 bool.
     * Correct mapping is JAVA_INT, not JAVA_BYTE.
     */
    public static final ValueLayout.OfInt  C_BOOL   = ValueLayout.JAVA_INT;
    public static final ValueLayout.OfInt  C_INT    = ValueLayout.JAVA_INT;
    public static final ValueLayout.OfInt  C_UINT   = ValueLayout.JAVA_INT;
    public static final ValueLayout.OfLong C_LONG   = ValueLayout.JAVA_LONG;
    public static final ValueLayout.OfLong C_UINT64 = ValueLayout.JAVA_LONG;
    public static final ValueLayout        C_PTR    = ValueLayout.ADDRESS;
    public static final ValueLayout.OfByte C_BYTE   = ValueLayout.JAVA_BYTE;
}
