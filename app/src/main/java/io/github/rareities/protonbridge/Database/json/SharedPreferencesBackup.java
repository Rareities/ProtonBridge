package io.github.rareities.protonbridge.Database.json;

import static io.github.rareities.protonbridge.util.ActivityHelper.DARK;
import static io.github.rareities.protonbridge.util.ActivityHelper.FOLLOW_SYSTEM;
import static io.github.rareities.protonbridge.util.ActivityHelper.LIGHT;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.rareities.protonbridge.R;

public class SharedPreferencesBackup {

    public static String export(Context context) throws JSONException {

        //General Settings
        java.util.Map<String, ?> snapshot = PreferenceManager.getDefaultSharedPreferences(context).getAll();
        boolean showThumbnails = value(snapshot, context.getString(R.string.pref_key_show_thumbnails), false);
        boolean isWifiOnly = value(snapshot, context.getString(R.string.pref_key_wifi_only_transfers), false);
        boolean allowWhileIdle = value(snapshot, context.getString(R.string.shared_preferences_allow_sync_trigger_while_idle), false);
        boolean useProxy = value(snapshot, context.getString(R.string.pref_key_use_proxy), false);
        String proxyProtocol = value(snapshot, context.getString(R.string.pref_key_proxy_protocol), "http");
        String proxyHost = value(snapshot, context.getString(R.string.pref_key_proxy_host), "localhost");
        int proxyPort = value(snapshot, context.getString(R.string.pref_key_proxy_port), 8080);
        String proxyUser = value(snapshot, context.getString(R.string.pref_key_proxy_username), "");
        String transfers = value(snapshot, context.getString(R.string.pref_key_transfers), "4");

        // File Access
        boolean safEnabled = value(snapshot, context.getString(R.string.pref_key_enable_saf), false);
        boolean refreshLaEnabled = value(snapshot, context.getString(R.string.pref_key_refresh_local_aliases), true);
        boolean vcpEnabled = value(snapshot, context.getString(R.string.pref_key_enable_vcp), false);
        boolean vcpDeclareLocal = value(snapshot, context.getString(R.string.pref_key_vcp_declare_local), true);
        boolean vcpGrantAll = false; // Retained only for older archive schema compatibility.

        // Look and Feel
        int oldTheme = value(snapshot, context.getString(R.string.pref_key_theme_old), FOLLOW_SYSTEM);
        String newTheme = value(snapshot, context.getString(R.string.pref_key_theme), String.valueOf(oldTheme));
        boolean isWrapFilenames = value(snapshot, context.getString(R.string.pref_key_wrap_filenames), true);

        // Notifications
        boolean appUpdates = value(snapshot, context.getString(R.string.pref_key_app_updates), false);

        // Logging
        boolean useLogs = value(snapshot, context.getString(R.string.pref_key_logs), false);


        JSONObject main = new JSONObject();

        main.put("showThumbnails", showThumbnails);
        main.put("isWifiOnly", isWifiOnly);
        main.put("allowWhileIdle", allowWhileIdle);
        main.put("useProxy", useProxy);
        main.put("proxyProtocol", proxyProtocol);
        main.put("proxyHost", proxyHost);
        main.put("proxyPort", proxyPort);
        main.put("proxyUser", proxyUser);
        main.put("transfers", transfers);
        main.put("safEnabled", safEnabled);
        main.put("refreshLaEnabled", refreshLaEnabled);
        main.put("vcpEnabled", vcpEnabled);
        main.put("vcpDeclareLocal", vcpDeclareLocal);
        main.put("vcpGrantAll", vcpGrantAll);
        main.put("isDarkTheme", newTheme);
        main.put("isWrapFilenames", isWrapFilenames);
        main.put("appUpdates", appUpdates);
        main.put("useLogs", useLogs);

        return main.toString();
    }

    @SuppressWarnings("unchecked")
    private static <T> T value(java.util.Map<String, ?> snapshot, String key, T fallback) {
        Object current = snapshot.get(key);
        if (current == null) return fallback;
        if (!fallback.getClass().isInstance(current)) throw new IllegalArgumentException("Invalid preference type");
        return (T) current;
    }

    public static void importJson(String json, Context context) throws JSONException {
        if (!prepare(json, context).commit()) throw new JSONException("Could not persist restored preferences");
    }

    public static void validateJson(String json, Context context) throws JSONException {
        prepare(json, context);
    }

    private static SharedPreferences.Editor prepare(String json, Context context) throws JSONException {
        if (json.length() > 256 * 1024) throw new JSONException("Preferences backup is too large");
        JSONObject jsonObject = new JSONObject(json);

        // Imported network values must be valid before any preferences are committed.
        // Authenticated full backups and rollback snapshots retain their explicit proxy policy.
        try {
            io.github.rareities.protonbridge.util.NetworkValues.proxy(
                    jsonObject.getString("proxyProtocol"), jsonObject.getString("proxyHost"),
                    jsonObject.getInt("proxyPort"), jsonObject.getString("proxyUser"), "");
        } catch (IllegalArgumentException invalid) { throw new JSONException("Invalid proxy configuration in backup"); }
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        //General Settings
        editor.putBoolean(context.getString(R.string.pref_key_show_thumbnails), jsonObject.getBoolean("showThumbnails"));
        editor.putBoolean(context.getString(R.string.pref_key_wifi_only_transfers), jsonObject.getBoolean("isWifiOnly"));
        editor.putBoolean(context.getString(R.string.pref_key_use_proxy), jsonObject.getBoolean("useProxy"));
        editor.putBoolean(context.getString(R.string.shared_preferences_allow_sync_trigger_while_idle), jsonObject.optBoolean("allowWhileIdle", false));
        editor.putString(context.getString(R.string.pref_key_proxy_protocol), jsonObject.getString("proxyProtocol"));
        editor.putString(context.getString(R.string.pref_key_proxy_host), jsonObject.getString("proxyHost"));
        editor.putInt(context.getString(R.string.pref_key_proxy_port), jsonObject.getInt("proxyPort"));
        editor.putString(context.getString(R.string.pref_key_proxy_username), jsonObject.getString("proxyUser"));
        editor.putString(context.getString(R.string.pref_key_transfers), jsonObject.optString("transfers", "4"));

        // File Access
        editor.putBoolean(context.getString(R.string.pref_key_enable_saf), jsonObject.getBoolean("safEnabled"));
        editor.putBoolean(context.getString(R.string.pref_key_refresh_local_aliases), jsonObject.getBoolean("refreshLaEnabled"));
        editor.putBoolean(context.getString(R.string.pref_key_enable_vcp), jsonObject.getBoolean("vcpEnabled"));
        editor.putBoolean(context.getString(R.string.pref_key_vcp_declare_local), jsonObject.getBoolean("vcpDeclareLocal"));
        editor.putBoolean(context.getString(R.string.pref_key_vcp_grant_all), false);

        // Look and Feel
        // The type changed. So we try to use boolean first, and if it fails we use the proper int
        Object darkTheme = jsonObject.get("isDarkTheme");
        int valueForTheme;
        if (darkTheme instanceof String) {
            valueForTheme = Integer.parseInt((String) darkTheme);
        } else {
           if((boolean) darkTheme) {
               valueForTheme = DARK;
           } else {
               valueForTheme = LIGHT;
           }
            editor.putString(context.getString(R.string.pref_key_theme), String.valueOf(valueForTheme));
        }

        editor.putString(context.getString(R.string.pref_key_theme), String.valueOf(valueForTheme));

        editor.putBoolean(context.getString(R.string.pref_key_wrap_filenames), jsonObject.getBoolean("isWrapFilenames"));

        // Notifications
        editor.putBoolean(context.getString(R.string.pref_key_app_updates), jsonObject.getBoolean("appUpdates"));

        // Logging
        editor.putBoolean(context.getString(R.string.pref_key_logs), jsonObject.getBoolean("useLogs"));

        return editor;
    }

}
