package io.github.rareities.protonbridge.util;

import java.util.regex.Pattern;

/** Mirrors rclone fs/fspath.CheckConfigName; Unicode letters/numbers remain supported. */
public final class RemoteNamePolicy {
    private static final Pattern NAME = Pattern.compile(
            "[A-Za-z0-9_\\p{L}\\p{N}.+@]+(?:[ -]+[A-Za-z0-9_\\p{L}\\p{N}.+@-]+)*");
    private RemoteNamePolicy() {}
    public static boolean isValid(String name) { return name != null && NAME.matcher(name).matches(); }
}
