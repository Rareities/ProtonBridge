package io.github.rareities.protonbridge.util

import android.app.Application
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import io.github.rareities.protonbridge.Items.SyncDirectionObject
import io.github.rareities.protonbridge.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TaskLayoutTest {
    class LayoutActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.AppTheme_NoActionBar)
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_task)
        }
    }

    @Test fun bisyncControlsInflateWithDirectResync() {
        Robolectric.buildActivity(LayoutActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            assertNotNull(activity.findViewById<EditText>(R.id.bisync_advanced_flags))
            assertEquals("Resync", activity.findViewById<Button>(R.id.bisync_initialize_button).text.toString())
            assertEquals(2, activity.findViewById<Spinner>(R.id.bisync_precedence).count)
            assertNotNull(activity.findViewById<Button>(R.id.task_schedule_button))
            assertNotNull(activity.findViewById<EditText>(R.id.task_quiet_start))
            assertEquals(7, SyncDirectionObject.getOptionsArray(activity).size)
            assertEquals(6, SyncDirectionObject.directionForSpinnerPosition(6))
            assertEquals(-1, SyncDirectionObject.directionForSpinnerPosition(99))
        }
    }
}
