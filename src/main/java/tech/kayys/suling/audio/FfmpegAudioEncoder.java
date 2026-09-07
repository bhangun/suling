package tech.kayys.suling.audio;

import tech.kayys.suling.ffmpeg.FfmpegLibraryCheck;
import tech.kayys.suling.ffmpeg.internal.FfmpegLibrary;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * FFmpeg-backed audio encoder for compressed formats.
 *
 * <p>The first implemented target is MP3 through {@code libmp3lame}. The
 * implementation intentionally writes raw MP3 packets directly, so it does not
 * depend on a container muxer yet.
 */
public final class FfmpegAudioEncoder implements AudioEncoder {
    private static final int AV_SAMPLE_FMT_S16P = 6;
    private static final int AVERROR_EOF = -541478725;
    private static final int AVERROR_EAGAIN_DARWIN = -35;
    private static final int AVERROR_EAGAIN_LINUX = -11;

    private static final long AV_FRAME_DATA_OFFSET = 0;
    private static final long AV_FRAME_NB_SAMPLES_OFFSET = 112;
    private static final long AV_FRAME_FORMAT_OFFSET = 116;
    private static final long AV_FRAME_PTS_OFFSET = 136;
    private static final long AV_FRAME_SAMPLE_RATE_OFFSET = 180;
    private static final long AV_FRAME_CH_LAYOUT_OFFSET = 384;

    private static final long AV_CODEC_CONTEXT_BIT_RATE_OFFSET = 56;
    private static final long AV_CODEC_CONTEXT_TIME_BASE_OFFSET = 84;
    private static final long AV_CODEC_CONTEXT_SAMPLE_RATE_OFFSET = 344;
    private static final long AV_CODEC_CONTEXT_SAMPLE_FMT_OFFSET = 348;
    private static final long AV_CODEC_CONTEXT_CH_LAYOUT_OFFSET = 352;
    private static final long AV_CODEC_CONTEXT_FRAME_SIZE_OFFSET = 376;

    private static final long AV_PACKET_DATA_OFFSET = 24;
    private static final long AV_PACKET_SIZE_OFFSET = 32;

    public FfmpegAudioEncoder() {
    }

    @Override
    public String name() {
        return "suling-ffmpeg-ffm";
    }

    @Override
    public Set<String> formats() {
        return Set.of("mp3");
    }

    @Override
    public EncodedMedia encode(PcmAudio audio, AudioEncodeOptions options) throws IOException {
        String format = AudioEncodeOptions.normalizeFormat(options.format());
        if (!"mp3".equals(format)) {
            throw new MediaCodecException("FFmpeg backend currently supports MP3 output only.");
        }
        return encodeMp3(audio, options);
    }

    public static boolean isMp3EncodingAvailable() {
        if (!hasRequiredMp3Libraries()) {
            return false;
        }
        try {
            return findEncoder("libmp3lame").address() != 0L;
        } catch (LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private EncodedMedia encodeMp3(PcmAudio audio, AudioEncodeOptions options) throws IOException {
        if (!hasRequiredMp3Libraries()) {
            throw new MediaCodecException("MP3 output requires FFmpeg avcodec and avutil libraries. "
                    + FfmpegLibraryCheck.getDiagnostics());
        }
        if (audio.format().sampleFormat() != PcmSampleFormat.S16_LE) {
            throw new MediaCodecException("MP3 encoder currently supports PCM S16_LE only");
        }
        if (audio.format().channels() < 1 || audio.format().channels() > 2) {
            throw new MediaCodecException("MP3 encoder currently supports mono or stereo PCM");
        }

        MemorySegment codec;
        try {
            codec = findEncoder("libmp3lame");
        } catch (LinkageError | RuntimeException e) {
            throw new MediaCodecException("MP3 output requires FFmpeg avcodec encode symbols. "
                    + FfmpegLibraryCheck.getDiagnostics(), e);
        }
        if (codec.address() == 0L) {
            throw new MediaCodecException("FFmpeg is available, but the libmp3lame encoder is not. "
                    + FfmpegLibraryCheck.getDiagnostics());
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ctx = invokeAddress(Api.MH_AVCODEC_ALLOC_CONTEXT3, codec).reinterpret(4096);
            if (ctx.address() == 0L) {
                throw new MediaCodecException("avcodec_alloc_context3 returned NULL");
            }
            MemorySegment frame = MemorySegment.NULL;
            MemorySegment packet = MemorySegment.NULL;
            MemorySegment ctxRef = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment frameRef = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment packetRef = arena.allocate(ValueLayout.ADDRESS);
            ctxRef.set(ValueLayout.ADDRESS, 0, ctx);
            try {
                configureMp3Context(ctx, audio.format(), options);
                checkNonNegative(invokeInt(Api.MH_AVCODEC_OPEN2, ctx, codec, MemorySegment.NULL), "avcodec_open2");

                int frameSize = ctx.get(ValueLayout.JAVA_INT, AV_CODEC_CONTEXT_FRAME_SIZE_OFFSET);
                if (frameSize <= 0) {
                    frameSize = 1152;
                }

                frame = invokeAddress(Api.MH_AV_FRAME_ALLOC).reinterpret(1024);
                if (frame.address() == 0L) {
                    throw new MediaCodecException("av_frame_alloc returned NULL");
                }
                frameRef.set(ValueLayout.ADDRESS, 0, frame);
                configureAudioFrame(frame, audio.format(), frameSize);
                checkNonNegative(invokeInt(Api.MH_AV_FRAME_GET_BUFFER, frame, 0), "av_frame_get_buffer");

                packet = invokeAddress(Api.MH_AV_PACKET_ALLOC).reinterpret(128);
                if (packet.address() == 0L) {
                    throw new MediaCodecException("av_packet_alloc returned NULL");
                }
                packetRef.set(ValueLayout.ADDRESS, 0, packet);

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                encodeFrames(ctx, frame, packet, audio, frameSize, out);
                drainEncoder(ctx, packet, out);

                Map<String, String> metadata = new LinkedHashMap<>();
                metadata.put("audio_format", "mp3");
                metadata.put("audio_mime", "audio/mpeg");
                metadata.put("audio_metadata_embedded", "false");
                metadata.put("audio_lossless", "false");
                metadata.put("audio_bitrate_kbps", String.valueOf(options.bitrateKbps() <= 0 ? 192 : options.bitrateKbps()));
                metadata.put("ffmpeg_versions", FfmpegLibraryCheck.versionSummary());
                metadata.put("ffmpeg_encoder", "libmp3lame");
                return new EncodedMedia(out.toByteArray(), "mp3", "audio/mpeg", metadata);
            } finally {
                if (packet.address() != 0L) {
                    invokeVoid(Api.MH_AV_PACKET_FREE, packetRef);
                }
                if (frame.address() != 0L) {
                    invokeVoid(Api.MH_AV_FRAME_FREE, frameRef);
                }
                if (ctx.address() != 0L) {
                    invokeVoid(Api.MH_AVCODEC_FREE_CONTEXT, ctxRef);
                }
            }
        }
    }

    private static boolean hasRequiredMp3Libraries() {
        return FfmpegLibrary.hasLibrary("avcodec") && FfmpegLibrary.hasLibrary("avutil");
    }

    private static void configureMp3Context(
            MemorySegment ctx,
            PcmAudioFormat format,
            AudioEncodeOptions options) {
        int bitrateKbps = options.bitrateKbps() <= 0 ? 192 : options.bitrateKbps();
        ctx.set(ValueLayout.JAVA_LONG, AV_CODEC_CONTEXT_BIT_RATE_OFFSET, bitrateKbps * 1000L);
        ctx.set(ValueLayout.JAVA_INT, AV_CODEC_CONTEXT_TIME_BASE_OFFSET, 1);
        ctx.set(ValueLayout.JAVA_INT, AV_CODEC_CONTEXT_TIME_BASE_OFFSET + Integer.BYTES, format.sampleRate());
        ctx.set(ValueLayout.JAVA_INT, AV_CODEC_CONTEXT_SAMPLE_RATE_OFFSET, format.sampleRate());
        ctx.set(ValueLayout.JAVA_INT, AV_CODEC_CONTEXT_SAMPLE_FMT_OFFSET, AV_SAMPLE_FMT_S16P);
        invokeVoid(Api.MH_AV_CHANNEL_LAYOUT_DEFAULT,
                ctx.asSlice(AV_CODEC_CONTEXT_CH_LAYOUT_OFFSET, 24),
                format.channels());
    }

    private static void configureAudioFrame(MemorySegment frame, PcmAudioFormat format, int frameSize) {
        frame.set(ValueLayout.JAVA_INT, AV_FRAME_NB_SAMPLES_OFFSET, frameSize);
        frame.set(ValueLayout.JAVA_INT, AV_FRAME_FORMAT_OFFSET, AV_SAMPLE_FMT_S16P);
        frame.set(ValueLayout.JAVA_INT, AV_FRAME_SAMPLE_RATE_OFFSET, format.sampleRate());
        invokeVoid(Api.MH_AV_CHANNEL_LAYOUT_DEFAULT,
                frame.asSlice(AV_FRAME_CH_LAYOUT_OFFSET, 24),
                format.channels());
    }

    private static void encodeFrames(
            MemorySegment ctx,
            MemorySegment frame,
            MemorySegment packet,
            PcmAudio audio,
            int frameSize,
            ByteArrayOutputStream out) throws IOException {
        byte[] pcm = audio.data();
        int channels = audio.format().channels();
        int totalFrames = Math.toIntExact(audio.format().frames());
        ByteBuffer source = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        int submitted = 0;
        while (submitted < totalFrames) {
            int samples = Math.min(frameSize, totalFrames - submitted);
            frame.set(ValueLayout.JAVA_INT, AV_FRAME_NB_SAMPLES_OFFSET, samples);
            frame.set(ValueLayout.JAVA_LONG, AV_FRAME_PTS_OFFSET, submitted);
            checkNonNegative(invokeInt(Api.MH_AV_FRAME_MAKE_WRITABLE, frame), "av_frame_make_writable");
            copyInterleavedS16ToPlanarFrame(source, frame, channels, submitted, samples);
            checkNonNegative(invokeInt(Api.MH_AVCODEC_SEND_FRAME, ctx, frame), "avcodec_send_frame");
            receivePackets(ctx, packet, out, false);
            submitted += samples;
        }
    }

    private static void copyInterleavedS16ToPlanarFrame(
            ByteBuffer source,
            MemorySegment frame,
            int channels,
            int startFrame,
            int samples) {
        for (int channel = 0; channel < channels; channel++) {
            MemorySegment plane = frame.get(ValueLayout.ADDRESS, AV_FRAME_DATA_OFFSET + channel * ValueLayout.ADDRESS.byteSize())
                    .reinterpret((long) samples * Short.BYTES);
            for (int sample = 0; sample < samples; sample++) {
                int sourceOffset = ((startFrame + sample) * channels + channel) * Short.BYTES;
                plane.setAtIndex(ValueLayout.JAVA_SHORT, sample, source.getShort(sourceOffset));
            }
        }
    }

    private static void drainEncoder(
            MemorySegment ctx,
            MemorySegment packet,
            ByteArrayOutputStream out) throws IOException {
        checkNonNegative(invokeInt(Api.MH_AVCODEC_SEND_FRAME, ctx, MemorySegment.NULL), "avcodec_send_frame(NULL)");
        receivePackets(ctx, packet, out, true);
    }

    private static void receivePackets(
            MemorySegment ctx,
            MemorySegment packet,
            ByteArrayOutputStream out,
            boolean draining) throws IOException {
        while (true) {
            int ret = invokeInt(Api.MH_AVCODEC_RECEIVE_PACKET, ctx, packet);
            if (ret == 0) {
                int size = packet.get(ValueLayout.JAVA_INT, AV_PACKET_SIZE_OFFSET);
                if (size > 0) {
                    MemorySegment data = packet.get(ValueLayout.ADDRESS, AV_PACKET_DATA_OFFSET).reinterpret(size);
                    byte[] chunk = new byte[size];
                    MemorySegment.ofArray(chunk).copyFrom(data);
                    out.write(chunk);
                }
                invokeVoid(Api.MH_AV_PACKET_UNREF, packet);
                continue;
            }
            if (isAgain(ret) || ret == AVERROR_EOF) {
                return;
            }
            throw new MediaCodecException("avcodec_receive_packet failed: " + errorString(ret));
        }
    }

    private static boolean isAgain(int ret) {
        return ret == AVERROR_EAGAIN_DARWIN || ret == AVERROR_EAGAIN_LINUX;
    }

    private static void checkNonNegative(int ret, String operation) {
        if (ret < 0) {
            throw new MediaCodecException(operation + " failed: " + errorString(ret));
        }
    }

    private static MemorySegment findEncoder(String name) {
        try (Arena arena = Arena.ofConfined()) {
            return invokeAddress(Api.MH_AVCODEC_FIND_ENCODER_BY_NAME, arena.allocateFrom(name));
        }
    }

    private static String errorString(int code) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(256);
            int ret = invokeInt(Api.MH_AV_STRERROR, code, buffer, 256L);
            if (ret < 0) {
                return "FFmpeg error " + code;
            }
            return buffer.getString(0) + " (" + code + ")";
        }
    }

    private static MemorySegment invokeAddress(MethodHandle handle, Object... args) {
        try {
            return (MemorySegment) handle.invokeWithArguments(args);
        } catch (Throwable e) {
            throw new MediaCodecException("FFmpeg FFM call failed", e);
        }
    }

    private static int invokeInt(MethodHandle handle, Object... args) {
        try {
            return (int) handle.invokeWithArguments(args);
        } catch (Throwable e) {
            throw new MediaCodecException("FFmpeg FFM call failed", e);
        }
    }

    private static void invokeVoid(MethodHandle handle, Object... args) {
        try {
            handle.invokeWithArguments(args);
        } catch (Throwable e) {
            throw new MediaCodecException("FFmpeg FFM call failed", e);
        }
    }

    private static final class Api {
        private static final MethodHandle MH_AVCODEC_FIND_ENCODER_BY_NAME = require(
                "avcodec", "avcodec_find_encoder_by_name", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        private static final MethodHandle MH_AVCODEC_ALLOC_CONTEXT3 = require(
                "avcodec", "avcodec_alloc_context3", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        private static final MethodHandle MH_AVCODEC_FREE_CONTEXT = require(
                "avcodec", "avcodec_free_context", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        private static final MethodHandle MH_AVCODEC_OPEN2 = require(
                "avcodec", "avcodec_open2",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        private static final MethodHandle MH_AVCODEC_SEND_FRAME = require(
                "avcodec", "avcodec_send_frame",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        private static final MethodHandle MH_AVCODEC_RECEIVE_PACKET = require(
                "avcodec", "avcodec_receive_packet",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        private static final MethodHandle MH_AV_PACKET_ALLOC = require(
                "avcodec", "av_packet_alloc", FunctionDescriptor.of(ValueLayout.ADDRESS));
        private static final MethodHandle MH_AV_PACKET_FREE = require(
                "avcodec", "av_packet_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        private static final MethodHandle MH_AV_PACKET_UNREF = require(
                "avcodec", "av_packet_unref", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

        private static final MethodHandle MH_AV_FRAME_ALLOC = require(
                "avutil", "av_frame_alloc", FunctionDescriptor.of(ValueLayout.ADDRESS));
        private static final MethodHandle MH_AV_FRAME_FREE = require(
                "avutil", "av_frame_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        private static final MethodHandle MH_AV_FRAME_GET_BUFFER = require(
                "avutil", "av_frame_get_buffer",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        private static final MethodHandle MH_AV_FRAME_MAKE_WRITABLE = require(
                "avutil", "av_frame_make_writable", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        private static final MethodHandle MH_AV_CHANNEL_LAYOUT_DEFAULT = require(
                "avutil", "av_channel_layout_default", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        private static final MethodHandle MH_AV_STRERROR = require(
                "avutil", "av_strerror",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

        private Api() {
        }

        private static MethodHandle require(String component, String symbol, FunctionDescriptor descriptor) {
            return FfmpegLibrary.downcallOpt(component, symbol, descriptor)
                    .orElseThrow(() -> new UnsatisfiedLinkError(
                            "FFmpeg symbol not found: " + component + ":" + symbol));
        }
    }
}
