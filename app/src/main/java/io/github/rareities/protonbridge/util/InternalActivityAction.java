package io.github.rareities.protonbridge.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** Installation-scoped capability for private actions on the exported launcher activity. */
public final class InternalActivityAction {
    private static final String PREFS = "internal_activity_action";
    private static final String EXTRA = "protonbridge.internal_action_capability";
    private InternalActivityAction() {}

    public static synchronized Intent authorize(Context context, Intent intent) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String capability = preferences.getString("capability", null);
        if (capability == null) {
            byte[] entropy = new byte[32];
            new SecureRandom().nextBytes(entropy);
            StringBuilder value = new StringBuilder(64);
            for (byte part : entropy) value.append(String.format(java.util.Locale.ROOT, "%02x", part & 0xff));
            capability = value.toString();
        }
        // Check even an existing value: failed SharedPreferences commits can change only memory.
        if (!preferences.edit().putString("capability", capability).commit())
            throw new IllegalStateException("Cannot authorize internal activity action");
        return intent.putExtra(EXTRA, capability);
    }

    public static boolean isAuthorized(Context context, Intent intent) {
        if (intent == null) return false;
        String supplied = intent.getStringExtra(EXTRA);
        String expected = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("capability", null);
        return supplied != null && supplied.length() == 64 && expected != null &&
                MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }
}
