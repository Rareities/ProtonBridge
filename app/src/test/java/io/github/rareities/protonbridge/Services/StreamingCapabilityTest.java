package io.github.rareities.protonbridge.Services;

import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class StreamingCapabilityTest {
    @Test public void anonymousHttpAndWebdavReceiveIndependentCapabilities() {
        String first = StreamingService.resolveBaseUrl(null, null, null, StreamingService.SERVE_HTTP);
        assertTrue(first.matches("[A-Za-z0-9_-]{22}"));
        assertNotEquals(first, StreamingService.resolveBaseUrl(null, "", "", StreamingService.SERVE_WEBDAV));
        assertNotNull(StreamingService.resolveBaseUrl(null, null, null, -1)); // Unknown protocols use HTTP.
    }
    @Test public void preservesExplicitEndpointsAndAuthenticatedRoots() {
        assertEquals("selected-token", StreamingService.resolveBaseUrl("selected-token", null, null, StreamingService.SERVE_HTTP));
        assertNull(StreamingService.resolveBaseUrl(null, "user", "password", StreamingService.SERVE_HTTP));
        assertNotNull(StreamingService.resolveBaseUrl(null, "user", "", StreamingService.SERVE_WEBDAV));
        assertNull(StreamingService.resolveBaseUrl(null, null, null, StreamingService.SERVE_FTP));
    }
}
