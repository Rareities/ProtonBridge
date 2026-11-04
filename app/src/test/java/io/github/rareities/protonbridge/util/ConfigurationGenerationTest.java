package io.github.rareities.protonbridge.util;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.File;
import java.io.IOException;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, application=Application.class)
public class ConfigurationGenerationTest {
    private DurableFiles.DirectorySync original;
    @Before public void setup() { original = DurableFiles.directorySync; DurableFiles.directorySync = directory -> {}; }
    @After public void restore() { DurableFiles.directorySync = original; }

    @Test public void reconnectEpochPersistsAndCannotBeRestoredThroughPreferences() throws Exception {
        Application context = ApplicationProvider.getApplicationContext();
        try (ConfigurationExclusion.Preparation ignored = ConfigurationExclusion.prepare()) {
            String before = ConfigurationGeneration.read(context);
            ConfigurationGeneration.advance(context);
            String reauthorized = ConfigurationGeneration.read(context);
            assertNotEquals(before, reauthorized);
            // Restore/import writes default preferences, not this private generation file.
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
            assertEquals(reauthorized, ConfigurationGeneration.read(context));
            ConfigurationGeneration.advance(context);
            assertNotEquals(reauthorized, ConfigurationGeneration.read(context));
        }
    }

    @Test public void failedBarrierAndCorruptGenerationFailClosed() throws Exception {
        Application context = ApplicationProvider.getApplicationContext();
        try (ConfigurationExclusion.Preparation ignored = ConfigurationExclusion.prepare()) {
            DurableFiles.directorySync = directory -> { throw new IOException("storage failure"); };
            assertThrows(IOException.class, () -> ConfigurationGeneration.advance(context));
            new File(context.getFilesDir(), "configuration-generation").delete();
            new File(context.getFilesDir(), "configuration-generation").createNewFile();
            assertThrows(IOException.class, () -> ConfigurationGeneration.read(context));
        }
    }
    @Test public void suppressedReplacementCannotReportAdvancedEpoch() throws Exception {
        Application context = ApplicationProvider.getApplicationContext();
        try (ConfigurationExclusion.Preparation ignored = ConfigurationExclusion.prepare()) {
            ConfigurationGeneration.advance(context);
            File installed = new File(context.getFilesDir(), "configuration-generation");
            byte[] original = java.nio.file.Files.readAllBytes(installed.toPath());
            DurableFiles.directorySync = directory -> java.nio.file.Files.write(installed.toPath(), original);
            assertThrows(IOException.class, () -> ConfigurationGeneration.advance(context));
            assertArrayEquals(original, java.nio.file.Files.readAllBytes(installed.toPath()));
        }
    }

}
