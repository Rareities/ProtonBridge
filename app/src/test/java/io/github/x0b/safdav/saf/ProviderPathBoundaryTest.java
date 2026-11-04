package io.github.x0b.safdav.saf;

import android.app.Application;
import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class ProviderPathBoundaryTest {
    @Test public void requiresPermissionRootOrDescendantNotSubstringOrSibling() {
        String granted = "content://com.android.externalstorage.documents/tree/primary:DCIM";
        assertTrue(ProviderPaths.isWithinPermission(granted, granted));
        assertTrue(ProviderPaths.isWithinPermission(granted + "/photo.jpg", granted));
        assertFalse(ProviderPaths.isWithinPermission(granted + "-private/photo.jpg", granted));
        assertFalse(ProviderPaths.isWithinPermission("prefix/" + granted, granted));
        assertTrue(ProviderPaths.isWithinPermission(granted, "content://com.android.externalstorage.documents/tree/primary:"));
    }
    @Test public void mappedPathsRejectTraversalAndEmptyInput() {
        for (String path : new String[]{"", "relative", "/primary/../private", "/primary/./private", "/primary\\private", "/primary\0private", "/primary//private"})
            assertFalse(path, ProviderPaths.isSafeMappedPath(path));
        assertFalse(ProviderPaths.isSafeMappedPath(null));
        assertTrue(ProviderPaths.isSafeMappedPath("/primary/DCIM/photo.jpg"));
    }
    @Test public void thirdPartyLiteralPercentNamesAreNotDecodedAsPaths() {
        Uri root = Uri.parse("content://example.provider/tree/root");
        String mapped = ProviderPaths.getPathForUri(root);
        ProviderPaths paths = new ProviderPaths(RuntimeEnvironment.getApplication());
        Uri child = paths.getUriByMappedPath("/" + mapped + "%2e%2e");
        assertEquals("%2e%2e", child.getLastPathSegment());
        assertTrue(child.getEncodedPath().endsWith("%252e%252e"));
    }
}
