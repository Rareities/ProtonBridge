package io.github.x0b.safdav;

import android.content.Context;
import io.github.x0b.safdav.file.SafConstants;

import java.io.IOException;

/**
 * Saf Emulation Server
 */
public class SafAccessProvider {

    private static SafDAVServer davServer;
    private static SafDirectServer directServer;
    private static String user;
    private static String password;

    public static synchronized SafDAVServer getServer(Context context) throws IOException {
        if(null == davServer) {
            initAuth(context);
            davServer = new SafDAVServer(SafConstants.SAF_REMOTE_PORT, user, password, context);
            davServer.start();
        }
        return davServer;
    }

    private static synchronized void initAuth(Context context) throws IOException {
        if (password != null && user != null) return;
        SafCredentialStore credentials = SafCredentialStore.create(context);
        String unlocked = credentials.password();
        user = credentials.user();
        password = unlocked;
    }

    public static SafDirectServer getDirectServer(Context context) {
        if(null == directServer) {
            directServer = new SafDirectServer(context);
        }
        return directServer;
    }

    public static String getUser(Context context) {
        try { initAuth(context); }
        catch (IOException error) { throw new IllegalStateException("SAF credentials are unavailable", error); }
        return user;
    }

    public static String getPassword(Context context) {
        try { initAuth(context); }
        catch (IOException error) { throw new IllegalStateException("SAF credentials are unavailable", error); }
        return password;
    }
}
