package io.github.rareities.protonbridge.workmanager

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import io.github.rareities.protonbridge.R
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Scheduler for Session Guardian Worker.
 * Schedules periodic health checks for OAuth-enabled remotes.
 */
object SessionGuardianScheduler {

    private const val WORK_NAME = "session_guardian_worker"
    private var observedPreferences: SharedPreferences? = null
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        val context = observedContext
        if (context != null && key == context.getString(R.string.pref_key_session_guardian)) {
            reconcile(prefs, context)
        }
    }
    private var observedContext: Context? = null

    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.pref_key_session_guardian), false)

    /**
     * Schedule the Session Guardian Worker to run every 8 hours.
     */
    @JvmStatic
    fun schedule(context: Context) {
        val app = context.applicationContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        synchronized(this) {
            if (observedPreferences !== prefs) {
                observedPreferences?.unregisterOnSharedPreferenceChangeListener(settingsListener)
                observedPreferences = prefs
                observedContext = app
                prefs.registerOnSharedPreferenceChangeListener(settingsListener)
            }
        }
        reconcile(prefs, app)
    }

    private fun reconcile(prefs: SharedPreferences, context: Context) {
        if (!prefs.getBoolean(context.getString(R.string.pref_key_session_guardian), false)) {
            cancel(context)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<SessionGuardianWorker>(
            8, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                15,
                TimeUnit.MINUTES
            )
            .setInitialDelay(1, TimeUnit.HOURS) // Start after 1 hour on first install
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
    }

    /**
     * Cancel the Session Guardian Worker.
     */
    @JvmStatic
    fun cancel(context: Context) {
        WorkManager.getInstance(context)
            .cancelUniqueWork(WORK_NAME)
    }

}
