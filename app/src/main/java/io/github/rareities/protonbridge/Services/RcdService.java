package io.github.rareities.protonbridge.Services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.concurrent.TimeUnit;

import io.github.rareities.protonbridge.R;
import io.github.rareities.protonbridge.RcloneRcd;
import io.github.rareities.protonbridge.util.FLog;
import io.github.rareities.protonbridge.util.FlagsUtil;
import io.github.rareities.protonbridge.util.NotificationUtils;
import io.github.rareities.protonbridge.util.TransferLocks;


public class RcdService extends Service implements RcloneRcd.JobsUpdateHandler {

    private static final String TAG = "RcdService";
    private static final String CHANNEL_ID = "io.github.rareities.protonbridge.rcd_channel";
    private static final String CHANNEL_NAME = "Rclone";
    private static final int PERSISTENT_NOTIFICATION_ID = 200;
    public static final String ACTION_START_FOREGROUND = "io.github.rareities.protonbridge.RcdService.StartForeground";
    public static final String ACTION_STOP_FOREGROUND = "io.github.rareities.protonbridge.RcdService.StopForeground";

    /**
     * If the service was started 60 or less seconds ago, it ignores system memory pressure of
     * level {@link android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW}.
     */
    private static final int ALIVE_SECONDS_LOW = 60;

    /**
     * If the service was started 30 or less seconds ago, it ignores system memory pressure of
     * level {@link android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL}.
     */
    private static final int ALIVE_SECONDS_CRITICAL = 30;
    private static final long IDLE_GRACE_MS = TimeUnit.SECONDS.toMillis(30);

    private RcloneRcd rcloneRcd;
    private boolean shutdown;
    private final Object onlineLock = new Object();
    private long initNanosTimestamp = 0;
    // CPU ownership only while VCP/RC jobs or streams are active.
    // This does not override Android Doze or Wi-Fi power saving.
    private TransferLocks activeTransferLocks;
    private final Handler idleHandler = new Handler(Looper.getMainLooper());
    private final Runnable idleAction = this::shutdownIfPossible;
    private final RcDemandState demand = new RcDemandState();
    private final RcNotificationState notificationState = new RcNotificationState();
    private final java.util.Set<Runnable> streamCancellations = new java.util.HashSet<>();
    private boolean stopping;

    private final IBinder binder = new RcdBinder();
    private NotificationManagerCompat notificationManager;

    public RcdService() {
        super();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        initNanosTimestamp = System.nanoTime();
        FLog.d(TAG, "onBind: client binds to service");
        return binder;
    }

    @Override
    public synchronized void onRcdJobsUpdate(@NonNull SparseArray<RcloneRcd.JobStatusResponse> status) {
        if (shutdown) return;
        onNotifyUse();
        // build job statistics
        long running = 0;
        long finished = 0;
        long failed = 0;
        for (int i = 0; i < status.size(); i++) {
            int key = status.keyAt(i);
            RcloneRcd.JobStatusResponse response = status.get(key);
            if(response.finished) {
                if (response.success) {
                    finished++;
                } else {
                    // Terminal handlers already report failures. Retained history is present in
                    // every poll, so do not re-log it or retain raw backend diagnostics here.
                    failed++;
                }
            } else {
                running++;
            }
        }

        // Wakelock ownership follows real activity even when the notification is unchanged.
        if (running > 0) {
            if (activeTransferLocks == null) activeTransferLocks = TransferLocks.acquire(this, "rcd");
        } else if (demand.streamCount() == 0 && activeTransferLocks != null) {
            activeTransferLocks.release();
            activeTransferLocks = null;
        }
        // The notification displays counts and indeterminate progress only. Reposting the
        // same summary every status poll adds binder/UI work without showing new information.
        if (!notificationManager.areNotificationsEnabled()) {
            // A denied publish is not a cached summary. If permission is later granted,
            // the first poll must still show the active operation even if counts are unchanged.
            notificationState.reset();
            return;
        }
        if (!notificationState.update(running, finished, failed)) return;

        int flags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = PendingIntent.FLAG_IMMUTABLE;
        }
        Intent foregroundIntent = new Intent(this, io.github.rareities.protonbridge.Activities.MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, foregroundIntent, flags);

        String statusLine = getString(R.string.rcd_service_notification_stats_template, running, finished, failed);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_rclone_logo)
                .setContentTitle(getString(R.string.rcd_service_notification_running_in_background))
                .setContentText(statusLine)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_cancel_download, getString(R.string.rcd_service_notification_btn_stop_all), stopServiceIntent());
        if (running > 0) builder.setOngoing(true).setProgress(100, 25, true);

        NotificationUtils.createNotification(this, PERSISTENT_NOTIFICATION_ID, builder.build());
    }

    public RcloneRcd.JobStatusHandler manageJob() {
        return jobStatusResponse -> {
            // ignored, we already get updates over main
        };
    }

    @Override
    public void onRcdActivityChanged() {
        // RC network calls and asynchronous jobs can originate on worker threads.
        idleHandler.post(() -> {
            synchronized (RcdService.this) {
                if (demand.streamCount() == 0 && (rcloneRcd == null ||
                        (!rcloneRcd.hasActiveWork() && (!stopping || !rcloneRcd.hasLiveProcess()))) && activeTransferLocks != null) {
                    activeTransferLocks.release();
                    activeTransferLocks = null;
                }
                onNotifyUse();
            }
        });
    }

    /** Holds the daemon and wakelock while a provider cat/rcat pipe is alive. */
    public interface DocumentLease extends AutoCloseable {
        void setCancellation(Runnable cancellation);
        @Override void close();
    }

    public synchronized DocumentLease acquireDocumentLease() {
        if (stopping) throw new IllegalStateException("The remote service is stopping");
        RcDemandState.Lease lease = demand.openStream();
        try {
            if (activeTransferLocks == null) activeTransferLocks = TransferLocks.acquire(this, "rcd-stream");
            onNotifyUse();
        } catch (RuntimeException error) {
            lease.close();
            throw error;
        }
        return new DocumentLease() {
            private Runnable cancellation;
            private boolean closed;
            @Override public void setCancellation(Runnable action) {
                boolean cancelNow;
                synchronized (RcdService.this) {
                    if (closed) return;
                    if (cancellation != null) streamCancellations.remove(cancellation);
                    cancellation = action;
                    streamCancellations.add(action);
                    cancelNow = stopping;
                }
                if (cancelNow) action.run();
            }
            @Override public void close() { synchronized (RcdService.this) {
                if (closed) return;
                closed = true;
                if (cancellation != null) streamCancellations.remove(cancellation);
                lease.close();
                if (demand.streamCount() == 0 && (rcloneRcd == null || !rcloneRcd.hasPendingJobs())
                        && activeTransferLocks != null) {
                    activeTransferLocks.release();
                    activeTransferLocks = null;
                }
                onNotifyUse();
            } }
        };
    }

    public class RcdBinder extends Binder {
        public RcdService getService() {
            FLog.d(TAG, "getService: returning RcdService");
            return RcdService.this;
        }
    }

    @Override
    public void onRebind(Intent intent) {
        initNanosTimestamp = System.nanoTime();
        FLog.v(TAG, "onRebind: client binds again");
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        FLog.v(TAG, "onUnbind: unbinding from client");
        return true;
    }

    // called when at least one client requested binder. Does not imply startService / onStartCommand!
    @Override
    public void onCreate() {
        super.onCreate();
        initNanosTimestamp = System.nanoTime();
        FLog.d(TAG, "onCreate: service is being created");
        shutdown = false;
        notificationManager = NotificationManagerCompat.from(getApplicationContext());
        setNotificationChannel();
        onNotifyUse();
    }

    // Called when service is startService()-ed
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        FLog.d(TAG, "onStartCommand: service onStart()");
        shutdown = false;
        if (intent != null && ACTION_STOP_FOREGROUND.equals(intent.getAction())) {
            FLog.d(TAG, "Removing foreground service");
            stopActiveWork();
            stopForeground(true);
            stopSelf();
        } else {
            showNotification();
            onNotifyUse();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void showNotification() {
        // The placeholder replaces any previous summary; publish the next real state again.
        notificationState.reset();
        Intent foregroundIntent = new Intent(this, io.github.rareities.protonbridge.Activities.MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, foregroundIntent, FlagsUtil.Companion.getFlagImmutable());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_rclone_logo)
                .setContentTitle(getString(R.string.rcd_service_notification_running_in_background))
                .setContentText(getString(R.string.rcd_service_notification_no_active_jobs))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_cancel_download, getString(R.string.rcd_service_notification_btn_stop_all), stopServiceIntent());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(PERSISTENT_NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(PERSISTENT_NOTIFICATION_ID, builder.build());
        }
    }

    @Override
    public void onDestroy() {
        idleHandler.removeCallbacks(idleAction);
        super.onDestroy();
        shutdown();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        FLog.v(TAG, "onTaskRemoved(%s)", rootIntent.toString());
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        FLog.v(TAG, "onConfigurationChanged(%s)", newConfig.toString());
        super.onConfigurationChanged(newConfig);
    }

    /**
     * In general, reducing memory usage when requested by the system can result in improved user
     * experience. However, unloading resources must be weighed agains the cost of recreating the
     * resources when required. On device testing has shown that user activity causes high memory
     * pressure. The system does not seem to take into account that app components are currently in
     * use. Therefore, a recently used service ignores memory pressure signals.
     * @param level
     */
    @Override
    public void onTrimMemory(int level) {
        if (shutdown) {
            // Service is already in cached state, will be reaped completely if
            // the system really needs memory.
            return;
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            long currentNanos = System.nanoTime();
            long graceNanos = initNanosTimestamp + TimeUnit.SECONDS.toNanos(ALIVE_SECONDS_CRITICAL);
            if (currentNanos > graceNanos) {
                long actualSeconds = TimeUnit.NANOSECONDS.toSeconds(currentNanos - graceNanos);
                FLog.v(TAG, "Unprotected (%ds) memory critical (level=%d), shutdown requested", actualSeconds, level);
                shutdownIfPossible();
            } else {
                FLog.v(TAG, "Protected memory critical (level=%d), ignoring signal", level);
            }
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            long currentNanos = System.nanoTime();
            long graceNanos = initNanosTimestamp + TimeUnit.SECONDS.toNanos(ALIVE_SECONDS_LOW);
            if (currentNanos > graceNanos) {
                FLog.v(TAG, "Unprotected memory low (level=%d), shutdown requested", level);
                shutdownIfPossible();
            } else {
                FLog.v(TAG, "Protected memory low (level=%d), ignoring signal", level);
            }
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE) {
            FLog.v(TAG, "Memory moderate (level=%d), ignoring signal", level);
        }
    }

    /**
     * Notify service that user is active. Some clients (e.g.
     * @{@link io.github.rareities.protonbridge.VirtualContentProvider}) may use the service for longer time
     * spans and can use this method to inform the service that when it s in use.
     *
     */
    public synchronized void onNotifyUse() {
        initNanosTimestamp = System.nanoTime();
        idleHandler.removeCallbacks(idleAction);
        if (!shutdown) idleHandler.postDelayed(idleAction, IDLE_GRACE_MS);
    }

    private synchronized void shutdownIfPossible() {
        synchronized (onlineLock) {
            if (shutdown || demand.isBusy(rcloneRcd != null && rcloneRcd.hasActiveWork())) return;
            // Use the same lifecycle lock as getLocalRcd so an on-demand restart
            // cannot race the service setting its cached daemon to null.
            if (rcloneRcd != null && !rcloneRcd.stopRcdIfIdle()) return;
            rcloneRcd = null;
        }
        FLog.d(TAG, "RC idle grace expired; stopping daemon");
        idleHandler.removeCallbacks(idleAction);
        if (activeTransferLocks != null) {
            activeTransferLocks.release();
            activeTransferLocks = null;
        }
        shutdown = true;
        stopForeground(true);
        stopSelf();
    }

    private synchronized void shutdown() {
        FLog.d(TAG, "Service shutting down");
        idleHandler.removeCallbacks(idleAction);
        stopActiveWork();
        shutdown = true;
    }

    private synchronized void stopActiveWork() {
        stopping = true;
        shutdown = true;
        idleHandler.removeCallbacks(idleAction);
        if (rcloneRcd != null) rcloneRcd.stopRcd();
        for (Runnable cancel : new java.util.ArrayList<>(streamCancellations)) {
            Thread signal = new Thread(cancel, "rcd-document-cancel");
            signal.setDaemon(true);
            signal.start();
        }
        onRcdActivityChanged();
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        // Android 15+ requires prompt exit from a timed-out dataSync foreground service.
        // Cancellation is non-blocking. Admissions remain until native exit.
        stopActiveWork();
        stopForeground(true);
        stopSelf(startId);
    }

    /**
     * If the service has been told to shutdown and waiting to be killed by OOM.
     * @return
     */
    public boolean isShutdown() {
        return shutdown;
    }

    private PendingIntent stopServiceIntent() {
        Intent cancelIntent = new Intent(this, RcdServiceAction.class);
        cancelIntent.setAction(RcdServiceAction.ACTION_STOP_ALL);
        int flags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(this, 0, cancelIntent, flags);
    }

    public boolean waitOnline(long timeout) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0, timeout));
        RcloneRcd attempted = null;
        while (System.nanoTime() < deadline) {
            synchronized (onlineLock) { attempted = rcloneRcd; }
            if (attempted != null) {
                try { attempted.isOnline(); return true; }
                catch (RcloneRcd.RcdIOException starting) { /* bounded startup retry */ }
                catch (RcloneRcd.RcdOpException rejected) { break; }
            }
            try { Thread.sleep(100); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
        }
        // A failed startup must not retain its configuration-session lease
        // indefinitely. Ownership still ends only at actual native exit.
        if (attempted != null) attempted.stopRcd();
        return false;
    }

    public synchronized RcloneRcd getLocalRcd() {
        synchronized (onlineLock) {
            if (null == rcloneRcd || !rcloneRcd.isAlive()) {
                FLog.d(TAG, "Creating rcd process");
                if (rcloneRcd != null) rcloneRcd.stopRcd();
                if (rcloneRcd != null && rcloneRcd.hasLiveProcess()) {
                    if (Looper.myLooper() == Looper.getMainLooper() || !rcloneRcd.awaitNativeExit(3000))
                        throw new IllegalStateException("The previous remote service is still stopping; retry after it exits");
                }
                if (RcloneRcd.hasRunningProcess()) throw new IllegalStateException("The previous remote service is still stopping");
                rcloneRcd = new RcloneRcd(getApplicationContext(), this);
                rcloneRcd.startRcd();
            }
            stopping = false;
            shutdown = false;
            onNotifyUse();
            return rcloneRcd;
        }
    }

    private void setNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.rcd_service_notification_channel_description));
            notificationManager.createNotificationChannel(channel);
        }
    }

    public static class RcdServiceAction extends BroadcastReceiver {

        public static final String ACTION_STOP_ALL = "io.github.rareities.protonbridge.RcdService.Stop";
        public static final String ACTION_PAUSE = "io.github.rareities.protonbridge.RcdService.Pause";

        @Override
        public void onReceive(Context context, Intent intent) {
            FLog.d(TAG, "Broadcast received");
            if (null == intent.getAction()) {
                FLog.w(TAG, "Empty action received, something has gone wrong");
                return;
            }
            switch (intent.getAction()) {
                case ACTION_STOP_ALL:
                    FLog.d(TAG, "Requesting shutdown of RcdService");
                    Intent stopIntent = new Intent(context, RcdService.class);
                    stopIntent.setAction(ACTION_STOP_FOREGROUND);
                    context.startService(stopIntent);
                    break;
                case ACTION_PAUSE:
                    FLog.w(TAG, "Pause not implemented");
                    break;
                default:
                    FLog.e(TAG, "Unknown action '%s' received. Check your code.", intent.getAction());
                    break;
            }
        }
    }
}
