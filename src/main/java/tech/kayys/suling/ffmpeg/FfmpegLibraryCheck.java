package tech.kayys.suling.ffmpeg;

import tech.kayys.suling.ffmpeg.internal.FfmpegLibrary;

/**
 * Diagnostics for Suling's optional FFmpeg FFM bridge.
 */
public final class FfmpegLibraryCheck {
    private FfmpegLibraryCheck() {
    }

    public static boolean isAvailable() {
        return FfmpegLibrary.isAvailable();
    }

    public static String versionSummary() {
        return "avcodec " + getAvcodecVersion()
                + ", avformat " + getAvformatVersion()
                + ", avutil " + getAvutilVersion()
                + (FfmpegLibrary.hasLibrary("swresample") ? ", swresample " + getSwresampleVersion() : "");
    }

    public static String getAvcodecVersion() {
        return FfmpegLibrary.hasLibrary("avcodec") ? FfmpegLibrary.version("avcodec") : "not loaded";
    }

    public static String getAvformatVersion() {
        return FfmpegLibrary.hasLibrary("avformat") ? FfmpegLibrary.version("avformat") : "not loaded";
    }

    public static String getAvutilVersion() {
        return FfmpegLibrary.hasLibrary("avutil") ? FfmpegLibrary.version("avutil") : "not loaded";
    }

    public static String getSwresampleVersion() {
        return FfmpegLibrary.hasLibrary("swresample") ? FfmpegLibrary.version("swresample") : "not loaded";
    }

    public static String getDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("FFmpeg FFM Bridge Diagnostics\n");
        sb.append("=============================\n");
        sb.append("Status: ").append(isAvailable() ? "Available" : "NOT AVAILABLE").append("\n");
        component(sb, "avutil", "avutil_version");
        component(sb, "avcodec", "avcodec_version");
        component(sb, "avformat", "avformat_version");
        component(sb, "swresample", "swresample_version");
        if (isAvailable()) {
            sb.append("\nEncoding backend: MP3 via libavcodec/libmp3lame is available when the encoder is present.\n");
        } else {
            sb.append("\nTroubleshooting:\n");
            sb.append("1. Install FFmpeg development libraries\n");
            sb.append("2. Set ffmpeg.avcodec.path, ffmpeg.avformat.path, ffmpeg.avutil.path if needed\n");
        }
        return sb.toString();
    }

    private static void component(StringBuilder sb, String component, String symbol) {
        boolean loaded = FfmpegLibrary.hasLibrary(component);
        sb.append(component).append(": ")
                .append(loaded ? "OK" : "MISSING")
                .append(" (")
                .append(FfmpegLibrary.loadSource(component))
                .append(", version ")
                .append(loaded ? FfmpegLibrary.version(component) : "not loaded")
                .append(", ")
                .append(symbol)
                .append("=")
                .append(FfmpegLibrary.hasSymbol(component, symbol) ? "OK" : "MISSING")
                .append(")\n");
    }
}
