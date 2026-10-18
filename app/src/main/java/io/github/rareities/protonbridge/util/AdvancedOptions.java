package io.github.rareities.protonbridge.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** One flag per line, normalized into one argv token; never evaluated by a shell. */
public final class AdvancedOptions {
    private AdvancedOptions() {}

    private static final Pattern NAME = Pattern.compile("--[a-z][a-z0-9-]*");
    private static final Set<String> REPEATABLE = new HashSet<>(Arrays.asList("include", "exclude", "filter"));
    private static final Set<String> REQUIRES_VALUE = new HashSet<>(Arrays.asList(
            "bwlimit", "tpslimit", "tpslimit-burst", "retries", "low-level-retries",
            "timeout", "contimeout", "max-transfer", "max-duration", "min-size",
            "max-size", "filter", "include", "exclude", "suffix", "modify-window",
            "checkers", "max-connections", "buffer-size", "conflict-resolve"));
    private static final Set<String> BOOLEAN = new HashSet<>(Arrays.asList("fast-list", "ignore-case", "check-first", "recover", "resilient"));
    private static final Set<String> OWNED = new HashSet<>(Arrays.asList(
            "resync", "resync-mode", "workdir", "config", "force", "max-delete", "max-delete-size",
            "compare", "checksum", "require-checksum", "ignore-checksum", "ignore-listing-checksum",
            "ignore-size", "ignore-errors", "ignore-existing", "size-only", "dry-run", "interactive",
            "delete-excluded", "delete-before", "delete-during", "delete-after", "delete-only",
            "conflict-loser", "check-sync", "no-slow-hash", "slow-hash-sync-only",
            "download-hash", "max-lock", "remove-empty-dirs", "local-no-check-updated",
            "local-no-set-modtime", "no-check-certificate", "insecure", "use-json-log", "help", "version", "bind", "addr",
            "backup-dir", "backup-dir1", "backup-dir2", "suffix", "suffix-keep-extension",
            "config-dir", "cache-dir", "cache-chunk-path", "cache-db-path", "temp-dir",
            "files-from", "files-from-raw", "include-from", "exclude-from", "filter-from", "filters-file",
            "exclude-if-present", "min-age", "max-age", "max-depth", "metadata-mapper", "exec",
            "rc", "rc-serve", "cpuprofile", "memprofile", "transfers", "stats", "stats-log-level",
            "log-level", "verbose", "quiet", "check-access", "check-filename", "no-check-dest", "no-traverse",
            "protondrive-original-file-size", "protondrive-replace-existing-draft", "protondrive-enable-caching",
            "protondrive-2fa", "protondrive-app-version", "local-links", "copy-links", "links", "sftp-skip-links",
            "compare-dest", "copy-dest", "local-encoding", "protondrive-encoding", "multi-thread-streams"
    ));

    public static String restriction(String name) {
        if (name.endsWith("-endpoint") || name.endsWith("-url") || name.endsWith("-host") ||
                name.endsWith("-port") || name.endsWith("-remote") || name.endsWith("-upstreams") ||
                name.contains("header") || name.equals("ca-cert") || name.equals("client-cert")) {
            return "Configure connection destinations and trust in the remote editor, not task flags: --" + name;
        }
        if (OWNED.contains(name) || (name.startsWith("no-") && OWNED.contains(name.substring(3))) ||
                name.startsWith("rc-") || name.startsWith("dump") ||
                name.startsWith("log-") || name.startsWith("stats-") || name.startsWith("header") ||
                name.startsWith("use-server-modtime")) {
            return "Use the task controls for --" + name + "; this option affects managed state, output or safety.";
        }
        if (name.contains("password") || name.endsWith("pass") || name.contains("token") ||
                name.contains("secret") || name.contains("credential") || name.contains("key") ||
                name.contains("username") || name.contains("auth") || name.contains("command") ||
                name.equals("sftp-ssh") || name.endsWith("-insecure") || name.contains("no-check-certificate")) {
            return "Configure credentials and connection security in the remote editor, not in job flags: --" + name;
        }
        return null;
    }

    public static List<String> parse(String input) {
        if (input.length() > 32768) throw new IllegalArgumentException("Advanced options are limited to 32 KiB");
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String[] lines = input.split("\\r?\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            int separator = 0;
            while (separator < line.length() && line.charAt(separator) != '=' &&
                    line.charAt(separator) != ' ' && line.charAt(separator) != '\t') separator++;
            String key = line.substring(0, separator);
            String value = separator == line.length() ? null :
                    (line.charAt(separator) == '=' ? line.substring(separator + 1) : line.substring(separator).trim());
            line = key + (value == null ? "" : "=" + value);
            if (!NAME.matcher(key).matches()) throw new IllegalArgumentException("Line " + (i + 1) + ": use --flag, --flag value or --flag=value");
            for (int c = 0; c < line.length(); c++) {
                if (Character.isISOControl(line.charAt(c))) throw new IllegalArgumentException("Control character in flag on line " + (i + 1));
            }
            String name = key.substring(2);
            if ("recovery".equals(name) || "reselient".equals(name))
                throw new IllegalArgumentException("Use rclone --recover and --resilient (not --recovery or --reselient)");
            String reason = restriction(name);
            if (reason != null) throw new IllegalArgumentException(reason);
            if (value != null && (Pattern.compile("(?i)[a-z][a-z0-9+.-]*://[^/\\s]+@").matcher(value).find() ||
                    Pattern.compile("(?i)(?:password|access_token|refresh_token|api_key|secret)=").matcher(value).find()))
                throw new IllegalArgumentException("Credentials belong in the remote editor, not in task option values");
            if (REQUIRES_VALUE.contains(name) && (value == null || value.isEmpty()))
                throw new IllegalArgumentException("Line " + (i + 1) + ": --" + name + " requires a value, for example --" + name + " value");
            if ("conflict-resolve".equals(name) && !Arrays.asList("none", "path1", "path2", "newer", "older", "larger", "smaller").contains(value))
                throw new IllegalArgumentException("--conflict-resolve must be none, path1, path2, newer, older, larger or smaller");
            if (BOOLEAN.contains(name) && value != null && !"true".equals(value) && !"false".equals(value))
                throw new IllegalArgumentException("Line " + (i + 1) + ": --" + name + " must be true or false");
            if ("retries".equals(name) || "low-level-retries".equals(name)) {
                try {
                    if (Integer.parseInt(value) < 0) throw new NumberFormatException();
                } catch (NumberFormatException invalid) {
                    throw new IllegalArgumentException("Line " + (i + 1) + ": --" + name + " must be a non-negative integer");
                }
            }
            if ("tpslimit-burst".equals(name)) {
                try {
                    if (Integer.parseInt(value) < 0) throw new NumberFormatException();
                } catch (NumberFormatException invalid) {
                    throw new IllegalArgumentException("Line " + (i + 1) + ": --" + name + " must be a non-negative integer");
                }
            }
            if ("tpslimit".equals(name)) {
                try {
                    double number = Double.parseDouble(value);
                    if (Double.isNaN(number) || Double.isInfinite(number) || number < 0) throw new NumberFormatException();
                } catch (NumberFormatException invalid) {
                    throw new IllegalArgumentException("Line " + (i + 1) + ": --" + name + " must be a non-negative finite number");
                }
            }
            if (!seen.add(name) && !REPEATABLE.contains(name)) throw new IllegalArgumentException("Duplicate scalar option: --" + name);
            if (line.length() > 2048) throw new IllegalArgumentException("Flag too long on line " + (i + 1));
            result.add(line);
        }
        if (result.size() > 64) throw new IllegalArgumentException("Maximum 64 advanced options per task");
        return result;
    }

    /** Recovery uses the saved native listings; it never implies a resync. */
    public static boolean recoveryEnabled(String input) {
        for (String flag : parse(input)) {
            if (flag.equals("--recover") || flag.equals("--recover=true") ||
                    flag.equals("--resilient") || flag.equals("--resilient=true")) return true;
        }
        return false;
    }

    /** Rclone warns that an undersized connection cap can deadlock a transfer. */
    public static void validateConnectionBudget(String input, int transfers) {
        if (transfers < 1) throw new IllegalArgumentException("Transfer count must be positive");
        long checkers = 8, connections = 0;
        boolean checkFirst = false;
        for (String flag : parse(input)) {
            String value = flag.contains("=") ? flag.substring(flag.indexOf('=') + 1) : "true";
            try {
                if (flag.startsWith("--checkers=")) {
                    checkers = Long.parseLong(value);
                    if (checkers < 1 || checkers > Integer.MAX_VALUE) throw new NumberFormatException();
                } else if (flag.startsWith("--max-connections=")) {
                    connections = Long.parseLong(value);
                    if (connections < 0 || connections > Integer.MAX_VALUE) throw new NumberFormatException();
                } else if (flag.equals("--check-first") || flag.startsWith("--check-first=")) {
                    checkFirst = Boolean.parseBoolean(value);
                }
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("Use a positive checker count and a non-negative connection limit");
            }
        }
        long required = (checkFirst ? Math.max(transfers, checkers) : transfers + checkers) + 1;
        if (connections > 0 && connections < required)
            throw new IllegalArgumentException("The selected transfer/checker settings require --max-connections=" + required +
                    " or greater. Use 0 for no connection cap.");
    }
}
