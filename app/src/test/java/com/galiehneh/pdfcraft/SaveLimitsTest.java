package com.galiehneh.pdfcraft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Guards the save-session limits the JS bridge enforces (see AndroidBridge). */
public class SaveLimitsTest {
    @Test
    public void limitsAreSane() {
        assertEquals(1L << 30, AndroidBridge.MAX_SESSION_BYTES);
        assertEquals(4, AndroidBridge.MAX_SESSIONS);
        assertEquals(4 * 1024 * 1024, AndroidBridge.MAX_CHUNK_BASE64_CHARS);
    }

    @Test
    public void singleChunkCannotExceedSessionLimit() {
        long maxDecoded = AndroidBridge.MAX_CHUNK_BASE64_CHARS / 4L * 3L;
        assertTrue(maxDecoded < AndroidBridge.MAX_SESSION_BYTES);
    }
}
