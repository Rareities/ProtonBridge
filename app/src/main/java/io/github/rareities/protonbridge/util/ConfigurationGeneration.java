package io.github.rareities.protonbridge.util;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Local approval epoch, deliberately excluded from exports and rollback snapshots. */
public final class ConfigurationGeneration {
    private ConfigurationGeneration() {}
    private static File file(Context context) { return new File(context.getFilesDir(), "configuration-generation"); }
    public static String read(Context context) throws IOException {
        requireLock();
        File source = file(context);
        if (!source.exists() && !new File(source + ".bak").exists()) return "initial";
        try (java.io.InputStream input = new AtomicFile(source).openRead()) {
            byte[] bytes = new byte[37];
            int count = 0, n;
            while (count < bytes.length && (n = input.read(bytes, count, bytes.length - count)) != -1) count += n;
            if (count != 36) throw new IOException("Invalid configuration generation");
            String generation = new String(bytes, 0, count, StandardCharsets.US_ASCII);
            if (!UUID.fromString(generation).toString().equals(generation)) throw new IOException("Invalid configuration generation");
            return generation;
        } catch (IllegalArgumentException error) { throw new IOException("Invalid configuration generation", error); }
    }
    public static void advance(Context context) throws IOException {
        requireLock();
        AtomicFile target = new AtomicFile(file(context));
        byte[] generation = UUID.randomUUID().toString().getBytes(StandardCharsets.US_ASCII);
        FileOutputStream stream = target.startWrite();
        try {
            stream.write(generation);
            stream.getFD().sync();
            target.finishWrite(stream);
        } catch (IOException error) { target.failWrite(stream); throw error; }
        DurableFiles.syncDirectory(context.getFilesDir());
        try (java.io.ByteArrayInputStream expected = new java.io.ByteArrayInputStream(generation)) {
            DurableFiles.verifyContents(target, expected, generation.length);
        }
    }
    private static void requireLock() {
        if (!ConfigurationExclusion.LOCK.isWriteLockedByCurrentThread())
            throw new IllegalStateException("Configuration identity requires preparation ownership");
    }
}
