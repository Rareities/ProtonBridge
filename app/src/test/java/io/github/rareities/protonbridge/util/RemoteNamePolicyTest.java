package io.github.rareities.protonbridge.util;
import org.junit.Test;
import static org.junit.Assert.*;

public class RemoteNamePolicyTest {
    @Test public void mirrorsRcloneConfigNamesIncludingUnicodeAndEmail() {
        for (String name : new String[]{"remote", ".", "..", "remote--", "rem ote", "user@example.com", "user+junkmail@example.com", "blåbær", "chữ Quốc ngữ", "_cache", "远程１２"})
            assertTrue(name, RemoteNamePolicy.isValid(name));
        for (String name : new String[]{"", "remote-", "-remote", " remote", "remote ", "remote:", "rem/ote", "rem\\ote", "[remote", "*", "remote\n", "remote\t", "e\u0301"})
            assertFalse(name, RemoteNamePolicy.isValid(name));
        assertFalse(RemoteNamePolicy.isValid(null));
    }
}
