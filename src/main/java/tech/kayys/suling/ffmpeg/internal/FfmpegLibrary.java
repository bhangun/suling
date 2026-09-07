package tech.kayys.suling.ffmpeg.internal;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Optional FFM bootstrap for FFmpeg libraries.
 */
public final class FfmpegLibrary {
    private static final Arena GLOBAL = Arena.global();
    private static final Linker LINKER = Linker.nativeLinker();
    private static final Map<String, LoadedLibrary> LIBRARIES = loadLibraries();

    private FfmpegLibrary() {
    }

    public static boolean isAvailable() {
        return hasLibrary("avcodec") && hasLibrary("avformat") && hasLibrary("avutil");
    }

    public static boolean hasLibrary(String component) {
        LoadedLibrary loaded = LIBRARIES.get(component);
        return loaded != null && loaded.lookup != null;
    }

    public static String loadSource(String component) {
        LoadedLibrary loaded = LIBRARIES.get(component);
        return loaded == null || loaded.source == null ? "not loaded" : loaded.source;
    }

    public static boolean hasSymbol(String component, String symbol) {
        LoadedLibrary loaded = LIBRARIES.get(component);
        return loaded != null && loaded.lookup != null && loaded.lookup.find(symbol).isPresent();
    }

    public static Optional<MethodHandle> downcallOpt(String component, String symbol, FunctionDescriptor descriptor) {
        LoadedLibrary loaded = LIBRARIES.get(component);
        if (loaded == null || loaded.lookup == null) {
            return Optional.empty();
        }
        return loaded.lookup.find(symbol).map(address -> LINKER.downcallHandle(address, descriptor));
    }

    public static String version(String component) {
        String symbol = switch (component) {
            case "avcodec" -> "avcodec_version";
            case "avformat" -> "avformat_version";
            case "avutil" -> "avutil_version";
            case "swresample" -> "swresample_version";
            default -> "";
        };
        if (symbol.isBlank()) {
            return "unknown";
        }
        Optional<MethodHandle> handle = downcallOpt(component, symbol, FunctionDescriptor.of(ValueLayout.JAVA_INT));
        if (handle.isEmpty()) {
            return "unknown";
        }
        try {
            int packed = (int) handle.get().invokeExact();
            int major = (packed >>> 16) & 0xff;
            int minor = (packed >>> 8) & 0xff;
            int micro = packed & 0xff;
            return major + "." + minor + "." + micro;
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static Map<String, LoadedLibrary> loadLibraries() {
        Map<String, LoadedLibrary> libraries = new LinkedHashMap<>();
        libraries.put("avutil", load("avutil", "ffmpeg.avutil.path", new String[] {
                "libavutil.dylib", "libavutil.60.dylib", "libavutil.so", "libavutil.so.60", "avutil",
                "/opt/homebrew/lib/libavutil.dylib", "/usr/local/lib/libavutil.dylib"
        }));
        libraries.put("avcodec", load("avcodec", "ffmpeg.avcodec.path", new String[] {
                "libavcodec.dylib", "libavcodec.62.dylib", "libavcodec.so", "libavcodec.so.62", "avcodec",
                "/opt/homebrew/lib/libavcodec.dylib", "/usr/local/lib/libavcodec.dylib"
        }));
        libraries.put("avformat", load("avformat", "ffmpeg.avformat.path", new String[] {
                "libavformat.dylib", "libavformat.62.dylib", "libavformat.so", "libavformat.so.62", "avformat",
                "/opt/homebrew/lib/libavformat.dylib", "/usr/local/lib/libavformat.dylib"
        }));
        libraries.put("swresample", load("swresample", "ffmpeg.swresample.path", new String[] {
                "libswresample.dylib", "libswresample.6.dylib", "libswresample.so", "libswresample.so.6", "swresample",
                "/opt/homebrew/lib/libswresample.dylib", "/usr/local/lib/libswresample.dylib"
        }));
        return Map.copyOf(libraries);
    }

    private static LoadedLibrary load(String component, String property, String[] candidates) {
        String explicit = System.getProperty(property);
        if (explicit != null && !explicit.isBlank()) {
            try {
                return new LoadedLibrary(SymbolLookup.libraryLookup(Path.of(explicit), GLOBAL), "explicit path: " + explicit);
            } catch (IllegalArgumentException | UnsatisfiedLinkError ignored) {
                return new LoadedLibrary(null, "failed explicit path: " + explicit);
            }
        }

        for (String candidate : candidates) {
            try {
                SymbolLookup lookup = candidate.contains("/") || candidate.contains("\\")
                        ? SymbolLookup.libraryLookup(Path.of(candidate), GLOBAL)
                        : SymbolLookup.libraryLookup(candidate, GLOBAL);
                return new LoadedLibrary(lookup, "auto-detected: " + candidate);
            } catch (IllegalArgumentException | UnsatisfiedLinkError ignored) {
                // Try the next candidate.
            }
        }

        SymbolLookup defaultLookup = LINKER.defaultLookup();
        String probe = switch (component) {
            case "avcodec" -> "avcodec_version";
            case "avformat" -> "avformat_version";
            case "avutil" -> "avutil_version";
            case "swresample" -> "swresample_version";
            default -> "";
        };
        if (!probe.isBlank() && defaultLookup.find(probe).isPresent()) {
            return new LoadedLibrary(defaultLookup, "native linker default lookup");
        }
        return new LoadedLibrary(null, "not loaded");
    }

    private record LoadedLibrary(SymbolLookup lookup, String source) {
    }
}
