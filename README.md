# Suling

**Java media codec adapters built on Foreign Function & Memory (FFM), starting with complete [libFLAC](https://github.com/xiph/flac) bindings — zero JNI.**

[![Java 25](https://img.shields.io/badge/Java-25%2B-blue)](https://openjdk.org/projects/jdk/25/)
[![libFLAC 1.4+](https://img.shields.io/badge/libFLAC-1.4%2B-green)](https://xiph.org/flac/)
[![License: BSD-3-Clause](https://img.shields.io/badge/License-BSD%203--Clause-orange)](LICENSE)

> **Suling** (Sundanese: ᮞᮥᮜ᮪ᮒ᮪) is a traditional bamboo flute from Indonesia. This library brings the beauty of lossless audio to Java through modern FFM APIs.

---

## ✨ Features

- **Zero JNI** — built entirely on the JDK 25 Foreign Function & Memory (FFM) API
- **Full API coverage** — encoder, decoder, and all three metadata levels (0/1/2)
- **Production-quality** — double-close guards, lifecycle validation, structured exceptions
- **Fluent builder API** — `FlacAudioFormat`, `FlacStreamEncoder` chaining
- **Zero-copy PCM views** — `FlacPcmUtils.channelBufferView()` wraps native memory directly
- **Version checking** — `FlacLibraryCheck` for runtime libFLAC detection and diagnostics
- **Codec-neutral audio facade** — `Suling.encode(...)` routes PCM to WAV/FLAC backends
- **FFmpeg bridge diagnostics** — optional FFM discovery for libavcodec/libavformat/libavutil, ready for future MP3/Opus/AAC/video backends
- **Typed exceptions** — `FlacInitException`, `FlacEncodingException`, `FlacDecodingException`, `FlacMetadataException`
- **Comprehensive tests** — unit tests (pure Java) + integration tests (requires libFLAC)
- **Sources & Javadoc JARs** — generated at build time

---

## 📋 Requirements

| Requirement | Version |
|---|---|
| JDK | 25+ (FFM API is stable) |
| Maven | 3.9+ |
| libFLAC | 1.4+ (libFLAC.so.12 on Linux) |

### Installing libFLAC

```bash
# Debian / Ubuntu
sudo apt install libflac-dev

# Fedora / RHEL
sudo dnf install flac-devel

# macOS (Homebrew)
brew install flac

# Windows (vcpkg)
vcpkg install flac:x64-windows
```

---

## 🛠️ Build

```bash
# Compile and package (unit tests only, no libFLAC needed)
mvn package

# With integration tests (requires libFLAC installed)
mvn test -Pintegration

# Full build: sources + Javadoc JARs
mvn verify -Pintegration
```

---

## 🚀 Quick Start

### Codec-neutral Audio API

Use the `tech.kayys.suling.audio` facade when callers should not care which
backend writes the final file:

```java
PcmAudio pcm = PcmAudio.fromInterleavedS16(
        pcmBytes,
        2,
        48_000,
        frames,
        Map.of("INAM", "hello"));

EncodedMedia wav = Suling.encode(pcm, AudioEncodeOptions.wav());
EncodedMedia flac = Suling.encode(pcm, AudioEncodeOptions.flac());
EncodedMedia mp3 = Suling.encode(pcm, AudioEncodeOptions.builder()
        .format("mp3")
        .bitrateKbps(192)
        .build());
```

WAV is pure Java. FLAC uses the libFLAC FFM backend. FFmpeg libraries are
discovered through FFM diagnostics; MP3 is backed by FFmpeg/libmp3lame. Opus,
AAC, and video backends are intended to plug into the same facade.

### 1. Check Library Availability (NEW!)

Before using the encoder/decoder, verify that libFLAC is available:

```java
if (!FlacLibraryCheck.isAvailable()) {
    System.err.println("libFLAC not found!");
    System.err.println(FlacLibraryCheck.getDiagnostics());
    return;
}

System.out.println("Using libFLAC version: " + FlacLibraryCheck.getVersion());
```

### 2. Encode PCM to FLAC

```java
var fmt = FlacAudioFormat.builder()
        .channels(2)
        .bitsPerSample(16)
        .sampleRate(44_100)
        .totalSamples(numFrames)
        .build();

try (var enc = new FlacStreamEncoder()) {
    enc.applyFormat(fmt)
       .setCompressionLevel(5)  // 0 (fastest) – 8 (best compression)
       .setVerify(true);         // cross-check with internal decoder

    enc.initFile(Path.of("output.flac"), progressCallback);
    enc.processInterleaved(interleavedPcm);  // int[], length = frames * channels
    enc.finish();
}
```

### 3. Decode a FLAC file

```java
try (var dec = new FlacStreamDecoder()) {
    dec.setMd5Checking(true);
    dec.setMetadataRespondAll();

    dec.initFile(Path.of("song.flac"),
        (frame, buffers) -> {
            // Extract interleaved int32 PCM
            int[] pcm = FlacPcmUtils.extractInterleavedInt32(frame, buffers);
            // … process audio …
            return StreamDecoderH.WRITE_STATUS_CONTINUE;
        },
        meta -> { /* metadata block */ },
        err  -> System.err.println("Decode error: " + err));

    dec.processUntilEndOfStream();
}
```

### 4. Read stream info (Level 0 — no full decode)

```java
FlacMetadata.StreamInfo info = FlacMetadata.getStreamInfo(Path.of("song.flac"));
System.out.printf("%d Hz / %d-bit / %d ch  (%.1f s)%n",
        info.sampleRate(), info.bitsPerSample(), info.channels(),
        info.durationSeconds());
System.out.println("MD5: " + info.md5Hex());

// Convert to FlacAudioFormat for re-encoding
FlacAudioFormat fmt = info.toAudioFormat();
```

### 5. Encode to an in-memory byte array

```java
byte[] flacData = FlacUsageExamples.exampleEncodeToBytes(
        FlacAudioFormat.CD_QUALITY, interleavedPcm);
// flacData contains a valid .flac stream starting with "fLaC"
```

### 6. Edit Vorbis comment tags (Level 2)

```java
try (var chain = new FlacMetadata.Chain()) {
    chain.read(Path.of("song.flac"));
    try (var it = chain.iterator()) {
        do {
            if (it.getBlockType() == FlacFormat.MetadataType.VORBIS_COMMENT) {
                MemorySegment block = it.getBlock(); // owned by chain
                // Use MetadataH.vcAppend / vcRemoveAllMatching etc.
            }
        } while (it.next());
    }
    chain.mergePadding();
    chain.write(true, true);  // usePadding=true, preserveStats=true
}
```

### 7. Zero-copy PCM view inside a callback

```java
(frame, buffers) -> {
    int blocksize = FlacPcmUtils.getBlocksize(frame);
    // Direct IntBuffer wrapping native memory — NO heap allocation
    IntBuffer ch0 = FlacPcmUtils.channelBufferView(buffers, 0, blocksize);
    // Process ch0 samples directly here; do NOT escape the buffer
    return StreamDecoderH.WRITE_STATUS_CONTINUE;
}
```

### 8. Extract 16-bit WAV-compatible bytes

```java
(frame, buffers) -> {
    int bps = FlacPcmUtils.getBitsPerSample(frame);
    ByteBuffer wav = FlacPcmUtils.extractInterleavedBytes16(frame, buffers, bps, false);
    outputStream.write(wav.array(), 0, wav.limit());
    return StreamDecoderH.WRITE_STATUS_CONTINUE;
}
```

---

## 📦 Package Overview

```
tech.kayys.suling
├── FlacAudioFormat            Immutable audio format record (channels, bps, rate, total)
├── FlacException              Root exception + sub-types:
│   ├── FlacInitException        encoder/decoder initialisation failed
│   ├── FlacEncodingException    mid-stream encode error
│   ├── FlacDecodingException    mid-stream decode error
│   └── FlacMetadataException    metadata read/write error
├── FlacLibraryCheck           NEW: Version checking and diagnostics
├── FlacUsageExamples          8 runnable examples + self-test main()
│
├── decoder/
│   ├── FlacStreamDecoder      High-level AutoCloseable decoder wrapper
│   ├── StreamDecoderH         Raw FFM downcall handles for FLAC__stream_decoder_*
│   └── FlacPcmUtils           PCM extraction utilities (zero-copy views, arrays, ByteBuffers)
│
├── encoder/
│   ├── FlacStreamEncoder      High-level AutoCloseable encoder wrapper (fluent API)
│   └── StreamEncoderH         Raw FFM downcall handles for FLAC__stream_encoder_*
│
├── metadata/
│   ├── FlacMetadata           Level 0/1/2 metadata API (SimpleIterator, Chain, ChainIterator)
│   └── MetadataH              Raw FFM downcall handles for FLAC__metadata_*
│
├── format/
│   └── FlacFormat             All format constants, enums, MemoryLayouts, VarHandles
│
├── io/
│   ├── FlacInputStream        java.io.InputStream that decodes FLAC to PCM bytes
│   └── FlacOutputStream       java.io.OutputStream that encodes PCM to FLAC
│
└── internal/
    └── FlacLibrary            Native library loader, Linker, SymbolLookup, C type aliases
```

---

## 🔍 Library Resolution

`FlacLibrary` tries to load libFLAC in this order:

1. **`-Dflac.library.path=/path/to/libFLAC.so`** — explicit path
2. `libFLAC.so.12` — modern Linux (libFLAC 1.4+)
3. `libFLAC.so.8` — older Linux
4. `libFLAC` — macOS / Windows with bare name
5. `FLAC` — Windows MSYS2 / vcpkg
6. Native linker default lookup — OS dynamic-linker cache

Diagnostic info: `FlacLibrary.loadSource()` returns a human-readable description.

### Version Detection (NEW!)

The library now automatically detects and reports the version of libFLAC:

```java
// Check version
System.out.println("libFLAC version: " + FlacLibraryCheck.getVersion());
System.out.println("Loaded from: " + FlacLibraryCheck.getLoadSource());

// Require minimum version
if (!FlacLibraryCheck.isVersionAtLeast(1, 4, 0)) {
    throw new IllegalStateException("libFLAC 1.4.0+ required");
}

// Get detailed diagnostics
System.out.println(FlacLibraryCheck.getDiagnostics());
```

---

## 🧪 Running the Self-Test

```bash
java --enable-preview \
     --enable-native-access=ALL-UNNAMED \
     -jar target/suling-0.1.0.jar
```

The self-test generates a 1-second 440 Hz stereo sine wave, encodes it, decodes it,
and verifies that every sample matches exactly (lossless round-trip).

---

## 🧪 Running Tests

```bash
# Unit tests only (pure Java — no libFLAC required)
mvn test

# Unit + integration tests (requires libFLAC installed)
mvn test -Pintegration

# Or explicitly:
mvn test -Dflac.test.integration=true
```

---

## 🏗️ Architecture Notes

### Two-layer design

Every native API surface is split into two layers:

| Layer | Class | Purpose |
|---|---|---|
| **Raw** | `StreamDecoderH`, `StreamEncoderH`, `MetadataH` | Exact downcall handles; static methods; no lifecycle management |
| **High-level** | `FlacStreamDecoder`, `FlacStreamEncoder`, `FlacMetadata.*` | `AutoCloseable`; functional callbacks; fluent API; exception translation |

Use the raw layer only when you need a function not exposed by the high-level layer.

### Upcall stub lifetime

Each `FlacStreamDecoder` / `FlacStreamEncoder` instance creates a **confined `Arena`**
that owns all upcall stubs for that instance. When the decoder/encoder is closed,
the arena is closed and all stubs are freed atomically.

### Thread safety

No high-level class is thread-safe. Use one decoder/encoder per thread, or add
external synchronisation. The `FlacLibrary` singleton (static initializer) is
thread-safe after class loading.

### `C_BOOL` mapping

`FLAC__bool` is defined in the C headers as `int` (not C99 `_Bool`). The binding
correctly maps it to `ValueLayout.JAVA_INT` (`C_BOOL = JAVA_INT`), not `JAVA_BYTE`.

---

## 📝 Real-World Examples

### Example 1: Audio File Converter (FLAC → WAV)

```java
public class FlacToWavConverter {
    public static void convert(Path flacFile, Path wavFile) throws IOException {
        try (var dec = new FlacStreamDecoder()) {
            dec.setMetadataRespondAll();
            
            // Get stream info first
            AtomicReference<FlacAudioFormat> fmtRef = new AtomicReference<>();
            
            dec.initFile(flacFile,
                (frame, buffers) -> {
                    int[] pcm = FlacPcmUtils.extractInterleavedInt32(frame, buffers);
                    // Write PCM to WAV file (implement your WAV writer)
                    writeWavChunk(pcm, fmtRef.get());
                    return StreamDecoderH.WRITE_STATUS_CONTINUE;
                },
                meta -> {
                    if (meta.isStreamInfo()) {
                        fmtRef.set(meta.getStreamInfo().toAudioFormat());
                    }
                },
                err -> { throw new RuntimeException("Decode error: " + err); });
            
            dec.processUntilEndOfStream();
        }
    }
}
```

### Example 2: Batch FLAC Encoder with Progress

```java
public class BatchEncoder {
    public static void encodeAll(Path inputDir, Path outputDir) throws IOException {
        var pcmFiles = Files.list(inputDir)
                .filter(p -> p.toString().endsWith(".pcm"))
                .toList();
        
        for (int i = 0; i < pcmFiles.size(); i++) {
            Path input = pcmFiles.get(i);
            Path output = outputDir.resolve(input.getFileName().toString() + ".flac");
            
            System.out.printf("[%d/%d] Encoding %s...%n", i + 1, pcmFiles.size(), input);
            
            try (var enc = new FlacStreamEncoder()) {
                enc.applyFormat(FlacAudioFormat.CD_QUALITY)
                   .setCompressionLevel(5)
                   .initFile(output, progress -> {
                       int pct = (int)(progress * 100);
                       System.out.print("\r  Progress: " + pct + "%");
                   });
                
                int[] pcm = readPcmFile(input);
                enc.processInterleaved(pcm);
                enc.finish();
                
                System.out.println(" ✓");
            }
        }
    }
}
```

### Example 3: FLAC Metadata Reader

```java
public class FlacMetadataReader {
    public static void printMetadata(Path flacFile) {
        if (!FlacLibraryCheck.isAvailable()) {
            System.err.println("libFLAC not available!");
            System.err.println(FlacLibraryCheck.getDiagnostics());
            return;
        }
        
        try (var chain = new FlacMetadata.Chain()) {
            chain.read(flacFile);
            
            try (var it = chain.iterator()) {
                do {
                    int type = it.getBlockType();
                    System.out.println("Block type: " + type);
                    
                    if (type == FlacFormat.MetadataType.STREAMINFO) {
                        var info = FlacMetadata.getStreamInfo(flacFile);
                        System.out.println("  Sample rate: " + info.sampleRate());
                        System.out.println("  Channels: " + info.channels());
                        System.out.println("  Bits/sample: " + info.bitsPerSample());
                        System.out.println("  Duration: " + info.durationSeconds() + "s");
                        System.out.println("  MD5: " + info.md5Hex());
                    }
                } while (it.next());
            }
        } catch (Exception e) {
            System.err.println("Error reading metadata: " + e.getMessage());
        }
    }
}
```

### Example 4: Streaming Audio Processor

```java
public class StreamingProcessor {
    public void processAudioStream(InputStream source, OutputStream sink) throws IOException {
        try (var dec = new FlacStreamDecoder()) {
            dec.initStream(
                (buf, bytes) -> {
                    // Read from source stream
                    int n = source.read(buf.reinterpret(bytes.get(ValueLayout.JAVA_LONG, 0))
                            .toArray(ValueLayout.JAVA_BYTE));
                    if (n == -1) {
                        bytes.set(ValueLayout.JAVA_LONG, 0, 0);
                        return StreamDecoderH.READ_STATUS_END_OF_STREAM;
                    }
                    bytes.set(ValueLayout.JAVA_LONG, 0, n);
                    return StreamDecoderH.READ_STATUS_CONTINUE;
                },
                null, null, null,
                (frame, buffers) -> {
                    // Process audio in real-time
                    int[] pcm = FlacPcmUtils.extractInterleavedInt32(frame, buffers);
                    applyAudioEffect(pcm);
                    return StreamDecoderH.WRITE_STATUS_CONTINUE;
                },
                meta -> { /* Handle metadata */ },
                err -> { System.err.println("Error: " + err); });
            
            dec.processUntilEndOfStream();
        }
    }
}
```

---

## 📚 Changelog

### 0.1.0

**Improvements over previous version:**

- **Package rebranding** — renamed from `org.xiph.flac` to `tech.kayys.suling` to avoid confusion with official Xiph.Org bindings
- **`FlacLibraryCheck`** — NEW utility class for version checking, availability detection, and diagnostics
- **Version detection** — automatic detection of libFLAC version at runtime with `isVersionAtLeast()` checks
- **Enhanced diagnostics** — `getDiagnostics()` provides comprehensive troubleshooting information
- **Better error messages** — improved exception messages with context and suggestions
- **Real-world examples** — added practical usage examples for common scenarios
- **Improved documentation** — comprehensive JavaDoc and README updates

**Backward compatibility note:** If you're upgrading from a version using `org.xiph.flac` packages, update your imports to `tech.kayys.suling.*`.

### 1.5.1

Previous release with full encoder, decoder, and metadata coverage. See CHANGELOG.md for details.

---

## ⚖️ Disclaimer

This is an independent implementation and is **not affiliated with or endorsed by** the [Xiph.Org Foundation](https://xiph.org) or the official FLAC project. FLAC is a trademark of the Xiph.Org Foundation.

---

## 📄 License

BSD-3-Clause License. See [LICENSE](LICENSE) for details.

This library uses libFLAC, which is licensed under the BSD-3-Clause license by the Xiph.Org Foundation.
