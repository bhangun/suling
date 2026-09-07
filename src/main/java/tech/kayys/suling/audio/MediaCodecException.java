package tech.kayys.suling.audio;

/**
 * Runtime exception used by Suling's codec-neutral media layer.
 */
public class MediaCodecException extends RuntimeException {
    public MediaCodecException(String message) {
        super(message);
    }

    public MediaCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
