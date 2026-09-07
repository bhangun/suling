/**
 * Java Foreign Function &amp; Memory (FFM) bindings for libFLAC.
 *
 * <p>This module provides complete, zero-JNI Java bindings for the
 * <a href="https://github.com/xiph/flac">libFLAC</a> library using the
 * Foreign Function &amp; Memory API introduced in JDK 22 and stabilised in JDK 22+.
 *
 * <h2>Package overview</h2>
 * <ul>
 *   <li>{@link tech.kayys.suling} — top-level types: {@code FlacAudioFormat},
 *       {@code FlacException} hierarchy, {@code FlacLibraryCheck}, {@code FlacUsageExamples}</li>
 *   <li>{@link tech.kayys.suling.decoder} — {@code FlacStreamDecoder}, raw {@code StreamDecoderH},
 *       {@code FlacPcmUtils}</li>
 *   <li>{@link tech.kayys.suling.encoder} — {@code FlacStreamEncoder}, raw {@code StreamEncoderH}</li>
 *   <li>{@link tech.kayys.suling.metadata} — {@code FlacMetadata} (levels 0/1/2), raw {@code MetadataH}</li>
 *   <li>{@link tech.kayys.suling.format} — {@code FlacFormat} constants, layouts, {@code VarHandle}s</li>
 *   <li>{@link tech.kayys.suling.io} — {@code FlacInputStream}, {@code FlacOutputStream}</li>
 *   <li>{@link tech.kayys.suling.internal} — {@code FlacLibrary} (native linker bootstrap)</li>
 * </ul>
 *
 * <h2>Requirements</h2>
 * <ul>
 *   <li>JDK 25 or later</li>
 *   <li>libFLAC 1.4 or later installed on the system</li>
 *   <li>JVM flag {@code --enable-native-access=tech.kayys.suling} (or {@code ALL-UNNAMED})</li>
 * </ul>
 *
 * <h2>Disclaimer</h2>
 * <p>This is an independent implementation and is not affiliated with or endorsed by
 * the <a href="https://xiph.org">Xiph.Org Foundation</a> or the official FLAC project.
 * FLAC is a trademark of the Xiph.Org Foundation.
 */
module tech.kayys.suling {

    // -----------------------------------------------------------------------
    // Public API exports
    // -----------------------------------------------------------------------
    exports tech.kayys.suling;
    exports tech.kayys.suling.decoder;
    exports tech.kayys.suling.encoder;
    exports tech.kayys.suling.metadata;
    exports tech.kayys.suling.format;
    exports tech.kayys.suling.io;
    exports tech.kayys.suling.audio;
    exports tech.kayys.suling.ffmpeg;

    // -----------------------------------------------------------------------
    // Internal package – not part of the public API
    // -----------------------------------------------------------------------
    exports tech.kayys.suling.internal;  // accessible for advanced users; not guaranteed stable
    exports tech.kayys.suling.ffmpeg.internal;  // accessible for advanced users; not guaranteed stable

    // -----------------------------------------------------------------------
    // JDK dependencies
    // -----------------------------------------------------------------------
    requires java.base;
    requires java.logging;

    // Native access is required for the Foreign Function & Memory API.
    // Declared here so the module system knows; the JVM flag
    // --enable-native-access=tech.kayys.suling (or ALL-UNNAMED) must still be present.
}
