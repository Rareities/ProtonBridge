package io.github.x0b.safdav;

import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class SafDestinationTest {
    @Test public void validatesDestinationAuthorityAndDecodedPathBeforeSlicing() {
        assertEquals("/primary/DCIM/photo.jpg", SafDAVServer.mappedDestination("http://localhost:40404/primary/DCIM/photo.jpg"));
        for (String value : new String[]{"http://evil.example:40404/primary/photo", "http://localhost:40405/primary/photo",
                "http://localhost:40404.evil.example/primary/photo", "http://user@localhost:40404/primary/photo",
                "http://localhost:40404/primary/%2e%2e/private", "http://localhost:40404/primary/photo?different=yes",
                "http://localhost:40404/primary/photo#fragment", "short", ""})
            assertNull(value, SafDAVServer.mappedDestination(value));
        assertNull(SafDAVServer.mappedDestination(null));
    }
}
