package io.github.rareities.protonbridge.util;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class AdvancedOptionsTest {
    @Test public void taskFlagsCannotRedirectStoredCredentialsOrReplaceTransportTrust() {
        for (String flag : new String[]{"--s3-endpoint=https://other.invalid", "--webdav-url=https://other.invalid",
                "--sftp-host=other.invalid", "--alias-remote=other:", "--crypt-remote=other:",
                "--ca-cert=/tmp/unreviewed.pem", "--webdav-headers=Authorization,Bearer-secret"}) {
            assertThrows(flag, IllegalArgumentException.class, () -> AdvancedOptions.parse(flag));
        }
    }
    @Test public void preservesSpacesUnicodeAndEqualsInsideOneArgument() {
        assertEquals(Arrays.asList("--bwlimit=8M", "--include=folder with spaces/**", "--exclude=\u4f60\u597d=old/**"),
                AdvancedOptions.parse("--bwlimit=8M\n--include=folder with spaces/**\n--exclude=\u4f60\u597d=old/**"));
    }

    @Test public void acceptsSpaceSeparatedStringValuesWithoutShellEvaluation() {
        assertEquals(Arrays.asList("--conflict-resolve=newer", "--bwlimit=8M", "--include=folder with spaces/**", "--exclude=folder=old/**"),
                AdvancedOptions.parse("--conflict-resolve newer\n--bwlimit 8M\n--include folder with spaces/**\n--exclude folder=old/**"));
        assertEquals(Arrays.asList("--include=--config=/tmp/not-an-argument"), AdvancedOptions.parse("--include --config=/tmp/not-an-argument"));
        assertThrows(IllegalArgumentException.class, () -> AdvancedOptions.parse("--config /tmp/other"));
        assertThrows(IllegalArgumentException.class, () -> AdvancedOptions.parse("--bwlimit 1M\n--bwlimit=2M"));
    }

    @Test public void recoveryFlagsAreNativeBooleansAndDoNotImplyResync() {
        assertEquals(Arrays.asList("--recover", "--resilient=true"),
                AdvancedOptions.parse("--recover\n--resilient true"));
        assertTrue(AdvancedOptions.recoveryEnabled("--recover"));
        assertTrue(AdvancedOptions.recoveryEnabled("--resilient=true"));
        assertFalse(AdvancedOptions.recoveryEnabled("--recover=false\n--resilient=false"));
        for (String input : Arrays.asList("--recover=yes", "--resilient=1", "--recovery", "--reselient"))
            assertThrows(input, IllegalArgumentException.class, () -> AdvancedOptions.parse(input));
    }

    @Test public void repeatableFiltersKeepTheirOrder() {
        assertEquals(Arrays.asList("--filter=+ A/**", "--filter=- B/**"), AdvancedOptions.parse("--filter=+ A/**\n--filter=- B/**"));
    }

    @Test public void rejectsOverridesAndSecretsEvenWhenSetFalse() {
        for (String name : Arrays.asList("resync", "resync-mode", "workdir", "config", "force", "require-checksum",
                "ignore-errors", "ignore-size", "delete-excluded", "rc-no-auth", "dump", "password-command",
                "protondrive-password", "protondrive-original-file-size", "protondrive-replace-existing-draft",
                "protondrive-enable-caching", "copy-links", "local-encoding", "metadata-mapper")) {
            assertThrows("Must reject " + name, IllegalArgumentException.class, () -> AdvancedOptions.parse("--" + name + "=false"));
        }
    }

    @Test public void rejectsPositionalsSubcommandsShortcutsAndDuplicateScalars() {
        for (String input : Arrays.asList("delete", "remote:path", "-1", "--", "--x=a\u0000b", "--bwlimit=1M\n--bwlimit=9M")) {
            assertThrows(input, IllegalArgumentException.class, () -> AdvancedOptions.parse(input));
        }
    }

    @Test public void requiresValuesAndValidatesKnownFlagTypes() {
        for (String input : Arrays.asList("--bwlimit", "--bwlimit=", "--conflict-resolve", "--conflict-resolve banana", "--filter", "--retries=-1",
                "--retries=oops", "--tpslimit=-1", "--tpslimit=NaN", "--fast-list=yes",
                "--no-force=true", "--no-require-checksum=true")) {
            assertThrows(input, IllegalArgumentException.class, () -> AdvancedOptions.parse(input));
        }
        assertEquals(Arrays.asList("--retries=3", "--fast-list=false", "--filter=+ A/**", "--filter=- B/**"),
                AdvancedOptions.parse("--retries=3\n--fast-list=false\n--filter=+ A/**\n--filter=- B/**"));
    }
}
