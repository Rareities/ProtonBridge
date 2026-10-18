package io.github.rareities.protonbridge.workmanager

import android.content.Context
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.rareities.protonbridge.Rclone
import io.github.rareities.protonbridge.notifications.AppErrorNotificationManager
import io.github.rareities.protonbridge.util.FLog
import io.github.rareities.protonbridge.util.NativeSessionGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Explicitly opted-in, bounded probes; normal transfers refresh their own tokens. */
class SessionGuardianWorker(private val mContext: Context, params: WorkerParameters) : CoroutineWorker(mContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!SessionGuardianScheduler.isEnabled(mContext) || NativeSessionGate.GLOBAL.isBusy())
            return@withContext Result.success()
        val deadline = SystemClock.elapsedRealtime() + 120_000L
        val rclone = Rclone(mContext)
        try {
            val config = runInterruptible(Dispatchers.IO) {
                rclone.configDump { isStopped || !SessionGuardianScheduler.isEnabled(mContext) || SystemClock.elapsedRealtime() >= deadline }
            }?.takeIf { it.isNotBlank() } ?: return@withContext Result.success()
            val entries = JSONObject(config)
            // Avoid an additional native list-remotes process; dump already contains names.
            for (name in entries.keys()) {
                if (isStopped || !SessionGuardianScheduler.isEnabled(mContext) ||
                    SystemClock.elapsedRealtime() >= deadline || NativeSessionGate.GLOBAL.isBusy()) break
                val settings = entries.optJSONObject(name) ?: continue
                // A key with no credential (or a TOTP seed alone) is not a cached session.
                // Probing it can bootstrap an unnecessary password login every maintenance run.
                if (!SessionProbePolicy.hasCachedSession(settings.opt("token"), settings.opt("access_token"))) continue
                val result = runInterruptible(Dispatchers.IO) {
                    rclone.listDirectories(name, 1) {
                        isStopped || !SessionGuardianScheduler.isEnabled(mContext) || SystemClock.elapsedRealtime() >= deadline
                    }
                }
                if (result.isAuthenticationError)
                    AppErrorNotificationManager(mContext).showSessionExpiredNotification(name)
                else if (!result.isSuccess)
                    FLog.w("SessionGuardian", "Optional health check could not confirm a session; no reconnect alarm issued")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Neither private remote names nor raw backend diagnostics belong in system logs.
            FLog.w("SessionGuardian", "Optional session checks could not complete")
        }
        Result.success()
    }
}
