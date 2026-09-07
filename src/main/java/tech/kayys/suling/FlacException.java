package tech.kayys.suling;

/**
 * Root unchecked exception for the FLAC FFM binding library.
 *
 * <p>All exceptions thrown by this library extend {@code FlacException}
 * so callers can catch the entire hierarchy with a single clause when desired.
 *
 * <h2>Exception hierarchy</h2>
 * <pre>
 * FlacException
 * ├── FlacInitException      – encoder/decoder failed to initialise
 * ├── FlacEncodingException  – error during PCM encoding
 * ├── FlacDecodingException  – error during FLAC decoding
 * └── FlacMetadataException  – error reading or writing metadata
 * </pre>
 *
 * @since 0.1.0
 */
public class FlacException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Constructs with a detail message. */
    public FlacException(String message) {
        super(message);
    }

    /** Constructs with a detail message and cause. */
    public FlacException(String message, Throwable cause) {
        super(message, cause);
    }

    // -----------------------------------------------------------------------
    // Specialised sub-types
    // -----------------------------------------------------------------------

    /**
     * Thrown when a decoder or encoder fails to initialise.
     * The numeric {@code initStatus} value is the raw
     * {@code FLAC__StreamDecoder_init_status} or
     * {@code FLAC__StreamEncoder_init_status} returned by libFLAC.
     */
    public static final class FlacInitException extends FlacException {
        private static final long serialVersionUID = 1L;
        private final int initStatus;

        public FlacInitException(String message, int initStatus) {
            super(message + " (init_status=" + initStatus + ")");
            this.initStatus = initStatus;
        }

        /** The raw libFLAC init_status code. */
        public int initStatus() { return initStatus; }
    }

    /**
     * Thrown when encoding fails mid-stream.
     */
    public static final class FlacEncodingException extends FlacException {
        private static final long serialVersionUID = 1L;

        public FlacEncodingException(String message) { super(message); }
        public FlacEncodingException(String message, Throwable cause) { super(message, cause); }
    }

    /**
     * Thrown when decoding fails mid-stream.
     */
    public static final class FlacDecodingException extends FlacException {
        private static final long serialVersionUID = 1L;

        public FlacDecodingException(String message) { super(message); }
        public FlacDecodingException(String message, Throwable cause) { super(message, cause); }
    }

    /**
     * Thrown when a metadata read or write operation fails.
     */
    public static final class FlacMetadataException extends FlacException {
        private static final long serialVersionUID = 1L;

        public FlacMetadataException(String message) { super(message); }
        public FlacMetadataException(String message, Throwable cause) { super(message, cause); }
    }
}
