package io.github.rareities.protonbridge.util;

import java.io.File;
import java.io.IOException;

/** Treat provider display names as a single filename, never as a filesystem path. */
public final class PrivateFileStaging {
    private PrivateFileStaging() {}

    public static File target(File directory, String displayName) throws IOException {
        if (displayName == null || displayName.isEmpty() || displayName.equals(".") || displayName.equals("..")
                || displayName.indexOf('/') >= 0 || displayName.indexOf('\\') >= 0 || displayName.indexOf('\0') >= 0) {
            throw new IOException("Invalid shared filename");
        }
        File root = directory.getCanonicalFile();
        File target = new File(root, displayName).getCanonicalFile();
        if (!root.equals(target.getParentFile())) throw new IOException("Filename escapes private staging");
        return target;
    }

    public static File directory(File cache, String purpose) throws IOException {
        File root = new File(cache, purpose);
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create private staging");
        File reservation = File.createTempFile("item-", ".stage", root);
        if (!reservation.delete() || !reservation.mkdir()) throw new IOException("Cannot reserve private staging");
        return reservation;
    }

    public static void requireOutside(File source, File privateRoot) throws IOException {
        String path = source.getCanonicalPath();
        String root = privateRoot.getCanonicalPath();
        if (path.equals(root) || path.startsWith(root + File.separator))
            throw new IOException("Cannot share an app-private file URI");
    }
}
