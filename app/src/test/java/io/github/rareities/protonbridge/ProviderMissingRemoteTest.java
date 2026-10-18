package io.github.rareities.protonbridge;

import java.io.FileNotFoundException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ProviderMissingRemoteTest {
    @Test public void missingRemoteIsAProviderNotFoundError() throws Exception {
        VirtualContentProvider provider = new VirtualContentProvider();
        java.lang.reflect.Field remotes = VirtualContentProvider.class.getDeclaredField("remotes");
        remotes.setAccessible(true);
        remotes.set(provider, new java.util.concurrent.ConcurrentHashMap<>());
        java.lang.reflect.Field config = VirtualContentProvider.class.getDeclaredField("configFile");
        config.setAccessible(true);
        config.set(provider, new java.io.File("missing-provider-config-" + java.util.UUID.randomUUID()));
        assertThrows(FileNotFoundException.class,
                () -> provider.queryChildDocuments(VirtualContentProvider.ROOT_DOC_PREFIX + "deleted:/", null, (String) null));
    }
}
