package tech.kayys.suling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FlacException} and its sub-types.
 * No libFLAC dependency required.
 */
class FlacExceptionTest {

    @Test
    void baseExceptionMessage() {
        var ex = new FlacException("test message");
        assertEquals("test message", ex.getMessage());
    }

    @Test
    void baseExceptionWithCause() {
        var cause = new RuntimeException("cause");
        var ex = new FlacException("parent", cause);
        assertSame(cause, ex.getCause());
    }

    @Test
    void initException_embedsStatus() {
        var ex = new FlacException.FlacInitException("init failed", 3);
        assertEquals(3, ex.initStatus());
        assertTrue(ex.getMessage().contains("init_status=3"));
    }

    @Test
    void initException_isFlacException() {
        var ex = new FlacException.FlacInitException("x", 0);
        assertInstanceOf(FlacException.class, ex);
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void encodingException_message() {
        var ex = new FlacException.FlacEncodingException("enc error");
        assertEquals("enc error", ex.getMessage());
        assertInstanceOf(FlacException.class, ex);
    }

    @Test
    void decodingException_message() {
        var ex = new FlacException.FlacDecodingException("dec error");
        assertEquals("dec error", ex.getMessage());
        assertInstanceOf(FlacException.class, ex);
    }

    @Test
    void metadataException_message() {
        var ex = new FlacException.FlacMetadataException("meta error");
        assertEquals("meta error", ex.getMessage());
        assertInstanceOf(FlacException.class, ex);
    }

    @Test
    void catchByBaseType() {
        FlacException caught = null;
        try {
            throw new FlacException.FlacEncodingException("enc");
        } catch (FlacException e) {
            caught = e;
        }
        assertNotNull(caught);
        assertInstanceOf(FlacException.FlacEncodingException.class, caught);
    }

    @Test
    void catchByRuntimeException() {
        assertDoesNotThrow(() -> {
            try {
                throw new FlacException.FlacDecodingException("dec");
            } catch (RuntimeException ignored) {}
        });
    }
}
