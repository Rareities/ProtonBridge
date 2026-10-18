package io.github.x0b.safdav;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import androidx.preference.PreferenceManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Migrates the local SAF listener password without a plaintext or key-loss fallback. */
final class SafCredentialStore {
    static final String PASSWORD = "io.github.x0b.safdav.safDavPass";
    static final String USER = "io.github.x0b.safdav.safDavUser";
    private static final String PREFIX = "ks_gcm_v1:";
    private static final String ALIAS = "protonbridge.safdav.v1";
    interface Keys { SecretKey get(boolean create) throws Exception; }
    private final SharedPreferences preferences;
    private final byte[] aad;
    private final Keys keys;

    SafCredentialStore(SharedPreferences preferences, String packageName, Keys keys) {
        this.preferences = preferences;
        this.aad = (packageName + "\0" + PASSWORD).getBytes(StandardCharsets.UTF_8);
        this.keys = keys;
    }

    static SafCredentialStore create(Context context) {
        return new SafCredentialStore(PreferenceManager.getDefaultSharedPreferences(context), context.getPackageName(), create -> {
            if (android.os.Build.VERSION.SDK_INT < 23)
                throw new IOException("Protected SAF credentials require Android 6 or later");
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            SecretKey key = (SecretKey) store.getKey(ALIAS, null);
            if (key != null) return key;
            if (!create) throw new IOException("SAF credential key is unavailable");
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            return generator.generateKey();
        });
    }

    String password() throws IOException {
        String stored = preferences.getString(PASSWORD, null);
        if (stored != null && stored.startsWith(PREFIX)) {
            String plaintext = decrypt(stored);
            // A failed earlier commit may have changed memory without changing disk.
            // Require an acknowledged persistence attempt even on the protected path.
            persist(stored);
            return plaintext;
        }
        String plaintext = stored;
        if (plaintext == null || plaintext.isEmpty()) {
            byte[] random = new byte[32];
            new java.security.SecureRandom().nextBytes(random);
            plaintext = Base64.encodeToString(random, Base64.NO_WRAP | Base64.URL_SAFE);
        }
        // Publish the plaintext to callers only after encrypted persistence succeeds.
        String encrypted = encrypt(plaintext);
        persist(encrypted);
        return plaintext;
    }

    private void persist(String encrypted) throws IOException {
        if (!preferences.edit().putString(PASSWORD, encrypted).putString(USER, user()).commit())
            throw new IOException("Cannot protect SAF credentials");
    }

    String user() { return preferences.getString(USER, "dav"); }

    private String encrypt(String plaintext) throws IOException {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(true));
            cipher.updateAAD(aad);
            return PREFIX + Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" +
                    Base64.encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        } catch (Exception error) { throw new IOException("Cannot protect SAF credentials", error); }
    }

    private String decrypt(String record) throws IOException {
        try {
            if (record.length() > 16384) throw new IOException("Invalid protected SAF credential");
            String[] parts = record.substring(PREFIX.length()).split(":", -1);
            if (parts.length != 2) throw new IOException("Invalid protected SAF credential");
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            if (iv.length != 12) throw new IOException("Invalid SAF credential nonce");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keys.get(false), new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad);
            return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception error) { throw new IOException("Cannot unlock SAF credentials", error); }
    }
}
