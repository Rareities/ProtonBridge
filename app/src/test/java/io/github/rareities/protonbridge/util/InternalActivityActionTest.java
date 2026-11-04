package io.github.rareities.protonbridge.util;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class InternalActivityActionTest {
    @Test public void externalActionCannotAuthorizeItself() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("internal_activity_action", Context.MODE_PRIVATE).edit().clear().commit();
        Intent external = new Intent("MAIN_ACTIVITY_START_EXPORT");
        assertFalse(InternalActivityAction.isAuthorized(context, external));
        assertFalse(context.getSharedPreferences("internal_activity_action", Context.MODE_PRIVATE).contains("capability"));
        Intent internal = InternalActivityAction.authorize(context, new Intent("MAIN_ACTIVITY_START_EXPORT"));
        assertTrue(InternalActivityAction.isAuthorized(context, internal));
        assertFalse(InternalActivityAction.isAuthorized(context, external));
        Intent second = InternalActivityAction.authorize(context, new Intent("MAIN_ACTIVITY_START_IMPORT"));
        assertTrue(InternalActivityAction.isAuthorized(context, internal));
        assertTrue(InternalActivityAction.isAuthorized(context, second));
        context.getSharedPreferences("internal_activity_action", Context.MODE_PRIVATE).edit().clear().commit();
        assertFalse(InternalActivityAction.isAuthorized(context, internal));
    }
}
