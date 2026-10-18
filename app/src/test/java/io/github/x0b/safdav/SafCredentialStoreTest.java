package io.github.x0b.safdav;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.IOException;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SafCredentialStoreTest {
    private SharedPreferences preferences;
    private SecretKey key;
    @Before public void setup() throws Exception {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("saf-secret-test", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        KeyGenerator generator = KeyGenerator.getInstance("AES"); generator.init(256); key = generator.generateKey();
    }
    private SafCredentialStore store(String packageName) { return new SafCredentialStore(preferences, packageName, create -> key); }

    @Test public void migratesExistingPasswordWithoutRotatingIt() throws Exception {
        preferences.edit().putString(SafCredentialStore.PASSWORD, "existing-private-password").putString(SafCredentialStore.USER, "dav").commit();
        assertEquals("existing-private-password", store("test.package").password());
        String record = preferences.getString(SafCredentialStore.PASSWORD, "");
        assertTrue(record.startsWith("ks_gcm_v1:"));
        assertFalse(record.contains("existing-private-password"));
        assertEquals("existing-private-password", store("test.package").password());
    }

    @Test public void keyLossAndWrongPackageNeverRegenerateOrOverwrite() throws Exception {
        assertFalse(store("test.package").password().isEmpty());
        String record = preferences.getString(SafCredentialStore.PASSWORD, "");
        assertThrows(IOException.class, () -> store("different.package").password());
        SafCredentialStore lost = new SafCredentialStore(preferences, "test.package", create -> {
            assertFalse("Never create a replacement key to decrypt a record", create);
            throw new IOException("key unavailable");
        });
        assertThrows(IOException.class, lost::password);
        assertEquals(record, preferences.getString(SafCredentialStore.PASSWORD, ""));
    }

    @Test public void rejectsMalformedRecordAndCiphertextTampering() throws Exception {
        store("test.package").password();
        String record = preferences.getString(SafCredentialStore.PASSWORD, "");
        int split = record.lastIndexOf(':');
        String cipher = record.substring(split + 1);
        preferences.edit().putString(SafCredentialStore.PASSWORD, record.substring(0, split + 1) +
                (cipher.charAt(0) == 'A' ? 'B' : 'A') + cipher.substring(1)).commit();
        assertThrows(IOException.class, () -> store("test.package").password());
        preferences.edit().putString(SafCredentialStore.PASSWORD, "ks_gcm_v1:broken").commit();
        assertThrows(IOException.class, () -> store("test.package").password());
    }
    @Test public void failedCommitCannotBeBypassedThroughChangedPreferenceMemory() throws Exception {
        java.util.Map<String, String> memory = new java.util.HashMap<>();
        java.util.Map<String, String> disk = new java.util.HashMap<>();
        memory.put(SafCredentialStore.PASSWORD, "still-on-disk"); disk.putAll(memory);
        java.util.concurrent.atomic.AtomicBoolean writable = new java.util.concurrent.atomic.AtomicBoolean(false);
        SharedPreferences simulated = (SharedPreferences) java.lang.reflect.Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class[]{SharedPreferences.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getString")) return memory.getOrDefault((String) args[0], (String) args[1]);
                    if (method.getName().equals("edit")) {
                        java.util.Map<String, String> pending = new java.util.HashMap<>();
                        return java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                                new Class[]{SharedPreferences.Editor.class}, (editor, operation, values) -> {
                                    if (operation.getName().equals("putString")) { pending.put((String) values[0], (String) values[1]); return editor; }
                                    if (operation.getName().equals("commit")) {
                                        memory.putAll(pending); // Android changes memory even on a failed disk write.
                                        if (writable.get()) { disk.clear(); disk.putAll(memory); }
                                        return writable.get();
                                    }
                                    throw new UnsupportedOperationException(operation.getName());
                                });
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        assertThrows(IOException.class, () -> new SafCredentialStore(simulated, "test.package", create -> key).password());
        assertTrue(memory.get(SafCredentialStore.PASSWORD).startsWith("ks_gcm_v1:"));
        assertEquals("still-on-disk", disk.get(SafCredentialStore.PASSWORD));
        // A fresh wrapper must still fail, rather than accepting only the in-memory encrypted record.
        assertThrows(IOException.class, () -> new SafCredentialStore(simulated, "test.package", create -> key).password());
        writable.set(true);
        assertEquals("still-on-disk", new SafCredentialStore(simulated, "test.package", create -> key).password());
        assertTrue(disk.get(SafCredentialStore.PASSWORD).startsWith("ks_gcm_v1:"));
    }

    @Test public void wrappingFailureDoesNotFallBackToPlaintext() {
        preferences.edit().putString(SafCredentialStore.PASSWORD, "legacy-secret").commit();
        SafCredentialStore unavailable = new SafCredentialStore(preferences, "test.package", create -> {
            throw new IOException("Keystore unavailable");
        });
        assertThrows(IOException.class, unavailable::password);
        assertEquals("legacy-secret", preferences.getString(SafCredentialStore.PASSWORD, ""));
    }

    @Test public void unavailableCredentialFailsBrowsingAndDisablesSafWithoutCrashingUi() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        SharedPreferences defaults = androidx.preference.PreferenceManager.getDefaultSharedPreferences(app);
        String enabled = app.getString(io.github.rareities.protonbridge.R.string.pref_key_enable_saf);
        defaults.edit().putString(SafCredentialStore.PASSWORD, "ks_gcm_v1:broken")
                .putString(SafCredentialStore.USER, "dav").putBoolean(enabled, true).commit();
        for (String field : new String[]{"password", "user", "davServer"}) {
            java.lang.reflect.Field cached = SafAccessProvider.class.getDeclaredField(field);
            cached.setAccessible(true); cached.set(null, null);
        }
        assertThrows(IOException.class, () -> SafAccessProvider.getServer(app));
        io.github.rareities.protonbridge.RemoteConfig.RemoteConfigHelper.enableSaf(app);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertFalse(defaults.getBoolean(enabled, true));
        assertEquals("ks_gcm_v1:broken", defaults.getString(SafCredentialStore.PASSWORD, ""));
    }

}
