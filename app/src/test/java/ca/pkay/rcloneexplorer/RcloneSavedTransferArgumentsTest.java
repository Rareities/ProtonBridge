package ca.pkay.rcloneexplorer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RcloneSavedTransferArgumentsTest {

    @Test
    public void savedTransferArgumentsPlaceOptionsBeforePathSeparator() {
        List<String> options = Arrays.asList("--transfers", "4", "--stats=1s");

        ArrayList<String> arguments = Rclone.savedTransferArguments(
                "sync",
                options,
                "--remote-source",
                "--remote-destination"
        );

        assertEquals(
                Arrays.asList(
                        "sync",
                        "--transfers",
                        "4",
                        "--stats=1s",
                        "--",
                        "--remote-source",
                        "--remote-destination"
                ),
                arguments
        );
    }

    @Test
    public void protonBisyncChecksHashesAndCapsDeletionsOnNormalRuns() {
        List<String> arguments = Rclone.protonBisyncArguments("/local", "proton:folder", "/private", null, "4");
        assertEquals(Arrays.asList("bisync", "--workdir", "/private", "--checksum", "--require-checksum",
                "--compare", "size,checksum", "--transfers", "4", "--retries", "1", "--max-delete", "10",
                "--max-lock", "15m", "--", "/local", "proton:folder"), arguments);
        assertFalse(arguments.contains("--resync"));
    }

    @Test
    public void protonBisyncResyncBindsAuthenticatedConsentAndKeepsOperandsLiteral() {
        String identity = "0123456789abcdef".repeat(4);
        List<String> arguments = Rclone.protonBisyncArguments("/local", "proton:--danger", "/private", identity, "4");
        assertEquals(Arrays.asList("--resync", "--resync-mode", "path1", "--proton-identity", identity,
                "--", "/local", "proton:--danger"), arguments.subList(arguments.size() - 8, arguments.size()));
    }
}
