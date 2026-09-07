# Changelog

All notable changes to flac-ffm are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [1.5.1] – 2025-Q4

### Added

**New types**
- `FlacAudioFormat` — immutable record encapsulating channels, bits-per-sample, sample rate, and total samples. Includes `builder()`, pre-defined constants (`CD_QUALITY`, `STUDIO_48K`, `HIRES_96K`), `durationSeconds()`, `byteRate()`, and `toBuilder()`.
- `FlacException` hierarchy — `FlacInitException` (carries `initStatus`), `FlacEncodingException`, `FlacDecodingException`, `FlacMetadataException`. All extend `FlacException extends RuntimeException`.
- `FlacInputStream` — `java.io.InputStream` that decodes a FLAC file to a continuous stream of raw little-endian PCM bytes.
- `FlacOutputStream` — `java.io.OutputStream` that encodes raw PCM bytes to FLAC and writes compressed output to a delegate stream.
- `module-info.java` — JPMS module descriptor (`org.xiph.flac`).
- `package-info.java` — Javadoc for all seven packages.

**New methods – `FlacStreamDecoder`**
- `setMetadataIgnoreApplication(byte[])` — suppress APPLICATION metadata by 4-byte ID.
- `isOpen()` — query whether the decoder has been closed.
- `checkOpen()` guard on every public method; subsequent calls after `close()` throw `IllegalStateException`.
- Double-close safety — second `close()` is silently ignored.
- `initFile` / `initStream` / `initOggStream` / `initOggFile` now throw `FlacException.FlacInitException` on failure instead of returning a raw status code.

**New methods – `FlacStreamEncoder`**
- `applyFormat(FlacAudioFormat)` — set channels, bits-per-sample, sample rate, and total-samples estimate in one call.
- `setMetadata(MemorySegment[])` — inject metadata blocks (tags, seektable, picture) before `init*`.
- `initOggStream(ReadCallback, WriteCallback, SeekCallback, TellCallback, MetadataCallback)` — Ogg-FLAC stream encoding.
- `isOpen()` — query whether the encoder has been closed.
- Double-close safety and `checkOpen()` on every method.
- `init*` methods now throw `FlacException.FlacInitException` on failure.
- `processInterleaved` validates null input and array divisibility, throws `FlacException.FlacEncodingException` on encode failure.

**New methods – `FlacMetadata.StreamInfo`**
- `durationSeconds()` — computed duration, or `NaN` when total samples are unknown.
- `md5Hex()` — 32-character lowercase hex string.
- `toAudioFormat()` — converts to `FlacAudioFormat`.

**New methods – `FlacMetadata` containers**
- `SimpleIterator`, `Chain`, `ChainIterator` — double-close safety, `checkOpen()` on all methods.
- `Chain.read()` and `Chain.write()` now throw `FlacException.FlacMetadataException` on failure.

**New methods – `FlacPcmUtils`**
- `channelBufferView(buffers, channel, blocksize)` — zero-copy `IntBuffer` wrapping native memory.
- `extractChannelInt32(buffers, channel, blocksize)` — per-channel heap copy.
- `extractInterleavedInt32Into(frame, buffers, dest, offset)` — fill into an existing array.
- `extractInterleavedShortInto(frame, buffers, bitsPerSample, dest)` — fill into a `ShortBuffer`.
- `extractInterleavedBytes16(frame, buffers, bitsPerSample, bigEndian)` — WAV/AIFF-compatible `ByteBuffer`.
- `getNumberType(frame)`, `getChannelAssignment(frame)` — frame header accessors.

**New methods – `FlacFormat.MetadataType`**
- `name(int)` — returns the canonical constant name (e.g. `"STREAMINFO"`) for a metadata type integer.

**New methods – `FlacLibrary`**
- `loadSource()` — human-readable description of how libFLAC was located.
- `hasSymbol(String)` — optional feature detection without throwing.

**Build**
- Sources JAR (`maven-source-plugin`).
- Javadoc JAR (`maven-javadoc-plugin`) with `--enable-preview`.
- `maven-enforcer-plugin`: requires JDK 25+ and Maven 3.9+.
- JUnit upgraded to 5.11.0 with `junit-jupiter-params`.
- `-Xlint:all` compiler flag.
- `-Pintegration` Maven profile for tests requiring libFLAC.

**Tests (new — 8 files, 50+ cases)**
- `FlacAudioFormatTest` — 30 pure-Java unit tests covering validation, builder, derived properties.
- `FlacExceptionTest` — 10 unit tests covering the exception hierarchy.
- `FlacFormatConstantsTest` — 12 unit tests covering constants and layouts.
- `IntegrationTestBase` — shared base with conditional skip, sine-wave generator, sample-file writer.
- `FlacStreamDecoderTest` — 18 integration tests (lifecycle, config, decode, seek, metadata, PCM utils).
- `FlacStreamEncoderTest` — 15 integration tests (lifecycle, applyFormat, file/stream encoding, verify).
- `FlacMetadataTest` — 20 integration tests (Level 0/1/2, double-close, lifecycle, object factory).
- `RoundTripTest` — lossless round-trip tests across 6 audio formats (parametrized).

### Fixed
- `FlacLibrary.C_BOOL` was `JAVA_BYTE`; corrected to `JAVA_INT` (`FLAC__bool` is `int` in C).
- `FlacMetadata.FlacMetadataException` moved to `FlacException.FlacMetadataException`; old name kept as `@Deprecated` alias for source compatibility.

### Deprecated
- `FlacMetadata.FlacMetadataException` — use `FlacException.FlacMetadataException`. Will be removed in 1.6.0.

---

## [1.5.0] – 2024-Q4

### Added
- Initial release.
- Full FLAC stream decoder bindings (`FlacStreamDecoder`, `StreamDecoderH`).
- Full FLAC stream encoder bindings (`FlacStreamEncoder`, `StreamEncoderH`).
- Metadata Level 0/1/2 (`FlacMetadata`, `MetadataH`).
- Format constants, struct layouts, and VarHandles (`FlacFormat`).
- PCM extraction utilities (`FlacPcmUtils`).
- Native library bootstrap (`FlacLibrary`).
- Eight usage examples (`FlacUsageExamples`).
