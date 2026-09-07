package tech.kayys.suling;

import tech.kayys.suling.decoder.FlacPcmUtils;
import tech.kayys.suling.decoder.FlacStreamDecoder;
import tech.kayys.suling.decoder.StreamDecoderH;
import tech.kayys.suling.encoder.FlacStreamEncoder;
import tech.kayys.suling.encoder.StreamEncoderH;
import tech.kayys.suling.format.FlacFormat;
import tech.kayys.suling.internal.FlacLibrary;
import tech.kayys.suling.metadata.FlacMetadata;
import tech.kayys.suling.metadata.MetadataH;

import java.lang.foreign.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runnable usage examples for the FLAC FFM bindings.
 *
 * <p>Each static method is a self-contained demonstration. The {@link #main(String[])}
 * method runs a complete encode→decode→verify self-test.
 *
 * <h2>Running</h2>
 * <pre>{@code
 * java --enable-preview --enable-native-access=ALL-UNNAMED \
 *      -jar flac-ffm-1.5.1.jar
 * }</pre>
 */
public final class FlacUsageExamples {

    private FlacUsageExamples() {}

    // -----------------------------------------------------------------------
    // Example 1 – Level 0: read stream info (no full decode needed)
    // -----------------------------------------------------------------------

    /**
     * Reads and prints the STREAMINFO block from a FLAC file.
     */
    public static FlacMetadata.StreamInfo exampleGetStreamInfo(Path flacFile) {
        System.out.println("=== Level-0 Stream Info: " + flacFile.getFileName() + " ===");
        FlacMetadata.StreamInfo info = FlacMetadata.getStreamInfo(flacFile);
        System.out.printf("  Sample rate    : %,d Hz%n",  info.sampleRate());
        System.out.printf("  Channels       : %d%n",      info.channels());
        System.out.printf("  Bits/sample    : %d%n",      info.bitsPerSample());
        System.out.printf("  Total samples  : %,d%n",     info.totalSamples());
        System.out.printf("  Duration       : %.2f s%n",  info.durationSeconds());
        System.out.printf("  MD5            : %s%n",      info.md5Hex());
        return info;
    }

    // -----------------------------------------------------------------------
    // Example 2 – Decode a FLAC file to interleaved int32 PCM
    // -----------------------------------------------------------------------

    /**
     * Decodes a FLAC file and returns all decoded PCM blocks.
     *
     * @return list of interleaved int32 blocks, one per FLAC frame
     */
    public static List<int[]> exampleDecodeFile(Path flacFile) {
        List<int[]> allFrames = new ArrayList<>();
        AtomicInteger totalSamples = new AtomicInteger(0);

        try (FlacStreamDecoder decoder = new FlacStreamDecoder()) {
            decoder.setMd5Checking(true);
            decoder.setMetadataRespond(FlacFormat.MetadataType.STREAMINFO);
            decoder.setMetadataRespond(FlacFormat.MetadataType.VORBIS_COMMENT);

            decoder.initFile(
                    flacFile,
                    // WriteCallback – delivers decoded audio frame by frame
                    (frame, buffers) -> {
                        int[] pcm = FlacPcmUtils.extractInterleavedInt32(frame, buffers);
                        allFrames.add(pcm);
                        totalSamples.addAndGet(FlacPcmUtils.getBlocksize(frame));
                        return StreamDecoderH.WRITE_STATUS_CONTINUE;
                    },
                    // MetadataCallback
                    meta -> {
                        int type = meta.get(ValueLayout.JAVA_INT, 0);
                        System.out.printf("  [Metadata] block type %d (%s)%n",
                                type, metadataTypeName(type));
                    },
                    // ErrorCallback
                    err -> System.err.println("  [Decoder error] status=" + err)
            );

            boolean ok = decoder.processUntilEndOfStream();
            System.out.printf("  Decoded %,d frames (%,d samples), state=%s%n",
                    allFrames.size(), totalSamples.get(), decoder.getStateString());
            if (!ok) System.err.println("  Warning: processUntilEndOfStream returned false");
        }

        return allFrames;
    }

    // -----------------------------------------------------------------------
    // Example 3 – Encode PCM to a FLAC file using FlacAudioFormat
    // -----------------------------------------------------------------------

    /**
     * Encodes interleaved int32 PCM to a FLAC file.
     */
    public static void exampleEncodeFile(Path outputFile, FlacAudioFormat fmt, int[] interleavedPcm) {
        try (FlacStreamEncoder encoder = new FlacStreamEncoder()) {
            encoder.applyFormat(fmt)
                   .setCompressionLevel(5)
                   .setVerify(true);

            encoder.initFile(outputFile, (bytesWritten, samplesWritten, frames, totalEst) ->
                    System.out.printf("  Progress: %,d bytes, %,d frames%n", bytesWritten, frames));

            boolean ok = encoder.processInterleaved(interleavedPcm);
            if (!ok) throw new FlacException.FlacEncodingException(
                    "processInterleaved failed: " + encoder.getStateString());

            boolean md5ok = encoder.finish();
            System.out.println("  Encoding finished. MD5 verified: " + md5ok);
        }
    }

    // -----------------------------------------------------------------------
    // Example 4 – Encode to an in-memory byte array via write callback
    // -----------------------------------------------------------------------

    /**
     * Encodes PCM to a FLAC byte array without writing to disk.
     */
    public static byte[] exampleEncodeToBytes(FlacAudioFormat fmt, int[] interleavedPcm) {
        List<byte[]> chunks = new ArrayList<>();

        try (FlacStreamEncoder encoder = new FlacStreamEncoder()) {
            encoder.applyFormat(fmt).setCompressionLevel(5);

            encoder.initStream(
                    // WriteCallback – collect FLAC data into chunks
                    (buf, size, samples, frame) -> {
                        byte[] chunk = new byte[(int) size];
                        MemorySegment.ofArray(chunk).copyFrom(buf.reinterpret(size));
                        chunks.add(chunk);
                        return StreamEncoderH.WRITE_STATUS_OK;
                    },
                    null,   // no seek callback → non-seekable stream
                    null,
                    null
            );

            encoder.processInterleaved(interleavedPcm);
            encoder.finish();
        }

        // Concatenate chunks
        int total = chunks.stream().mapToInt(c -> c.length).sum();
        byte[] result = new byte[total];
        int pos = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, result, pos, chunk.length);
            pos += chunk.length;
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Example 5 – Level 2 metadata: add/update a Vorbis comment tag
    // -----------------------------------------------------------------------

    /**
     * Adds or replaces a Vorbis comment (tag) in a FLAC file in-place.
     *
     * @param flacFile  target file (modified in-place)
     * @param fieldName tag name, e.g. {@code "TITLE"} (case-insensitive)
     * @param value     new tag value
     */
    public static void exampleSetVorbisTag(Path flacFile, String fieldName, String value) {
        try (FlacMetadata.Chain chain = new FlacMetadata.Chain()) {
            chain.read(flacFile);

            boolean found = false;
            try (FlacMetadata.ChainIterator it = chain.iterator()) {
                do {
                    if (it.getBlockType() == FlacFormat.MetadataType.VORBIS_COMMENT) {
                        try (Arena arena = Arena.ofConfined()) {
                            MemorySegment block = it.getBlock(); // owned by chain
                            MemorySegment fn    = arena.allocateFrom(fieldName.toUpperCase());

                            // Remove any existing entries for this field
                            MetadataH.vcRemoveAllMatching(block, fn);

                            // Build and append the new entry
                            // FLAC__StreamMetadata_VorbisComment_Entry: { uint32_t length; FLAC__byte *entry; }
                            MemorySegment entryStruct = arena.allocate(
                                    ValueLayout.ADDRESS.byteSize() + Integer.BYTES);
                            MemorySegment name = arena.allocateFrom(fieldName.toUpperCase());
                            MemorySegment val  = arena.allocateFrom(value);
                            MetadataH.vcEntryFromNameValuePair(entryStruct, name, val);
                            MetadataH.vcAppend(block, entryStruct, true);
                        }
                        found = true;
                        break;
                    }
                } while (it.next());
            }

            if (!found) {
                System.err.println("  No VORBIS_COMMENT block found – tag not written.");
                return;
            }

            chain.mergePadding();
            chain.write(true, true);
            System.out.println("  Tag written: " + fieldName + "=" + value);
        }
    }

    // -----------------------------------------------------------------------
    // Example 6 – Seek to a specific sample offset, then decode
    // -----------------------------------------------------------------------

    /**
     * Decodes a few frames starting from an absolute sample offset.
     */
    public static void exampleSeekAndDecode(Path flacFile, long sampleOffset) {
        System.out.printf("=== Seek to sample %,d ===%n", sampleOffset);

        try (FlacStreamDecoder decoder = new FlacStreamDecoder()) {
            decoder.setMd5Checking(false); // MD5 verification requires full decode

            decoder.initFile(flacFile,
                    (frame, buffers) -> {
                        long sn = FlacPcmUtils.getSampleNumber(frame);
                        System.out.printf("  Frame @ sample %,d, blocksize=%d%n",
                                sn, FlacPcmUtils.getBlocksize(frame));
                        return StreamDecoderH.WRITE_STATUS_CONTINUE;
                    },
                    null,
                    err -> System.err.println("  Error: " + err));

            decoder.processUntilEndOfMetadata();

            boolean seeked = decoder.seekAbsolute(sampleOffset);
            System.out.println("  Seek succeeded: " + seeked);

            if (seeked) {
                // Decode up to 3 frames starting from the seek point
                for (int i = 0; i < 3 && decoder.processSingle(); i++) { /* drive loop */ }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Example 7 – Level 1: list all metadata blocks
    // -----------------------------------------------------------------------

    /**
     * Lists all metadata blocks in a FLAC file using the Level 1 simple iterator.
     */
    public static void exampleListMetadata(Path flacFile) {
        System.out.println("=== Metadata Blocks ===");
        try (FlacMetadata.SimpleIterator it = new FlacMetadata.SimpleIterator(flacFile, true)) {
            do {
                int  type   = it.getBlockType();
                int  length = it.getBlockLength();
                long offset = it.getBlockOffset();
                System.out.printf("  [offset=%6d  len=%6d]  type=%-2d  %s%n",
                        offset, length, type, metadataTypeName(type));
            } while (it.next());
        }
    }

    // -----------------------------------------------------------------------
    // Example 8 – ByteBuffer extraction (16-bit WAV-compatible)
    // -----------------------------------------------------------------------

    /**
     * Decodes a FLAC file and shows how to extract PCM as a little-endian
     * {@link java.nio.ByteBuffer} suitable for writing WAV data.
     */
    public static void exampleDecodeToByteBuffer(Path flacFile) {
        System.out.println("=== Decode to ByteBuffer (16-bit LE) ===");
        AtomicInteger totalBytes = new AtomicInteger(0);

        try (FlacStreamDecoder decoder = new FlacStreamDecoder()) {
            decoder.initFile(flacFile,
                    (frame, buffers) -> {
                        int bps = FlacPcmUtils.getBitsPerSample(frame);
                        java.nio.ByteBuffer bb =
                                FlacPcmUtils.extractInterleavedBytes16(frame, buffers, bps, false);
                        totalBytes.addAndGet(bb.limit());
                        // In a real app: audioOutputStream.write(bb.array(), 0, bb.limit());
                        return StreamDecoderH.WRITE_STATUS_CONTINUE;
                    },
                    null,
                    err -> {});
            decoder.processUntilEndOfStream();
        }
        System.out.printf("  Total 16-bit PCM bytes extracted: %,d%n", totalBytes.get());
    }

    // -----------------------------------------------------------------------
    // Self-test (main)
    // -----------------------------------------------------------------------

    /**
     * Runs an encode→decode→verify self-test and prints results to stdout.
     *
     * <p>Requires {@code libFLAC} to be installed on the system.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        System.out.println("=== FLAC FFM Binding v1.5.1 – Self-Test ===");
        System.out.printf("JVM            : %s%n", System.getProperty("java.vm.version"));
        System.out.printf("libFLAC loaded : %s%n", FlacLibrary.loadSource());

        // ---- Generate 1-second 440 Hz stereo sine wave at CD quality ----
        var fmt       = FlacAudioFormat.CD_QUALITY;
        int numFrames = fmt.sampleRate(); // 1 second
        int[] pcm     = new int[numFrames * fmt.channels()];
        for (int i = 0; i < numFrames; i++) {
            short s = (short) (Short.MAX_VALUE * Math.sin(2 * Math.PI * 440 * i / fmt.sampleRate()));
            pcm[i * fmt.channels()]     = s; // L
            pcm[i * fmt.channels() + 1] = s; // R
        }
        System.out.printf("Generated %,d sample frames (%.2f s, 440 Hz stereo sine)%n",
                numFrames, (double) numFrames / fmt.sampleRate());

        // ---- Encode to a temp file ----
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"), "flac_ffm_self_test.flac");
        System.out.println("\n--- Encoding ---");
        System.out.printf("Output: %s%n", tmp);
        exampleEncodeFile(tmp, fmt, pcm);

        // ---- Stream info ----
        System.out.println("\n--- Stream Info ---");
        FlacMetadata.StreamInfo info = exampleGetStreamInfo(tmp);

        // ---- Decode and verify ----
        System.out.println("\n--- Decoding ---");
        List<int[]> decoded = exampleDecodeFile(tmp);
        int totalDecoded = decoded.stream().mapToInt(a -> a.length).sum() / fmt.channels();
        System.out.printf("  Total decoded frames: %,d (expected %,d)%n", totalDecoded, numFrames);

        if (totalDecoded != numFrames) {
            System.err.println("  FAIL: frame count mismatch!");
            System.exit(1);
        }

        // ---- Lossless verification ----
        int[] decodedFlat = decoded.stream()
                .reduce(new int[0], (a, b) -> { int[] c = new int[a.length+b.length]; System.arraycopy(a,0,c,0,a.length); System.arraycopy(b,0,c,a.length,b.length); return c; });
        int mismatches = 0;
        for (int i = 0; i < Math.min(pcm.length, decodedFlat.length); i++) {
            if (pcm[i] != decodedFlat[i]) mismatches++;
        }
        if (mismatches > 0) {
            System.err.printf("  FAIL: %,d lossless mismatches!%n", mismatches);
            System.exit(1);
        }
        System.out.println("  Lossless verification: PASS");

        // ---- Metadata listing ----
        System.out.println("\n--- Metadata ---");
        exampleListMetadata(tmp);

        // ---- Seek test ----
        System.out.println("\n--- Seek Test ---");
        exampleSeekAndDecode(tmp, fmt.sampleRate() / 2);

        // ---- ByteBuffer extraction ----
        System.out.println("\n--- ByteBuffer Extraction ---");
        exampleDecodeToByteBuffer(tmp);

        System.out.println("\n=== Self-Test PASSED ===");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String metadataTypeName(int type) {
        return switch (type) {
            case FlacFormat.MetadataType.STREAMINFO     -> "STREAMINFO";
            case FlacFormat.MetadataType.PADDING        -> "PADDING";
            case FlacFormat.MetadataType.APPLICATION    -> "APPLICATION";
            case FlacFormat.MetadataType.SEEKTABLE      -> "SEEKTABLE";
            case FlacFormat.MetadataType.VORBIS_COMMENT -> "VORBIS_COMMENT";
            case FlacFormat.MetadataType.CUESHEET       -> "CUESHEET";
            case FlacFormat.MetadataType.PICTURE        -> "PICTURE";
            default -> "UNKNOWN(" + type + ")";
        };
    }
}
