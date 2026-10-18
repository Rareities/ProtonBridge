package io.github.rareities.protonbridge.Services;

import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import io.github.rareities.protonbridge.BroadcastReceivers.ServeCancelAction;
import io.github.rareities.protonbridge.Items.RemoteItem;
import io.github.rareities.protonbridge.R;
import io.github.rareities.protonbridge.Rclone;
import io.github.rareities.protonbridge.util.FLog;
import io.github.rareities.protonbridge.util.NotificationUtils;


public class StreamingService extends IntentService {

    private static final String TAG = "StreamingService";
    public static final String SERVE_PATH_ARG = "io.github.rareities.protonbridge.streaming_service.arg1";
    public static final String REMOTE_ARG = "io.github.rareities.protonbridge.streaming_service.arg2";
    public static final String SHOW_NOTIFICATION_TEXT = "io.github.rareities.protonbridge.streaming_service.arg3";
    public static final String SERVE_PORT = "io.github.rareities.protonbridge.streaming_service.arg4";
    public static final String SERVE_BASE_URL = "io.github.rareities.protonbridge.streaming_service.base_url";
    public static final String SERVE_PROTOCOL = "io.github.rareities.protonbridge.serve_protocol";
    public static final String ALLOW_REMOTE_ACCESS = "io.github.rareities.protonbridge.allow_remote_access";
    public static final String AUTHENTICATION_USERNAME = "io.github.rareities.protonbridge.username";
    public static final String AUTHENTICATION_PASSWORD = "io.github.rareities.protonbridge.password";
    public static final int SERVE_HTTP = 11;
    public static final int SERVE_WEBDAV = 12;
    public static final int SERVE_FTP = 13;
    public static final int SERVE_DLNA = 14;
    private final String CHANNEL_ID = "io.github.rareities.protonbridge.streaming_channel";
    private final String CHANNEL_NAME = "Streaming service";
    private final int PERSISTENT_NOTIFICATION_ID = 179;
    private Rclone rclone;
    private Process runningProcess;
    private volatile io.github.rareities.protonbridge.util.NativePipeTransfer transfer;

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.*
     */
    public StreamingService() {
        super("io.github.rareities.protonbridge.streamingservice");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setNotificationChannel();
        rclone = new Rclone(this);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        final String servePath = intent.getStringExtra(SERVE_PATH_ARG);
        final RemoteItem remote = intent.getParcelableExtra(REMOTE_ARG);
        final Boolean showNotificationText = intent.getBooleanExtra(SHOW_NOTIFICATION_TEXT, false);
        final int protocol = intent.getIntExtra(SERVE_PROTOCOL, SERVE_HTTP);
        final int port = intent.getIntExtra(SERVE_PORT, 8080);
        final Boolean allowRemoteAccess = intent.getBooleanExtra(ALLOW_REMOTE_ACCESS, false);
        final String authenticationUsername = intent.getStringExtra(AUTHENTICATION_USERNAME);
        final String authenticationPassword = intent.getStringExtra(AUTHENTICATION_PASSWORD);
        final String baseUrl = intent.getStringExtra(SERVE_BASE_URL);

        int flags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = PendingIntent.FLAG_IMMUTABLE;
        }
        Intent foregroundIntent = new Intent(this, io.github.rareities.protonbridge.Activities.MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, foregroundIntent, flags);

        Intent cancelIntent = new Intent(this, ServeCancelAction.class);
        PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(this, 0, cancelIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_streaming)
                .setContentTitle(getString(R.string.streaming_service_notification_title))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_cancel_download, getString(R.string.cancel), cancelPendingIntent);

        if (showNotificationText) {
            Uri.Builder address = Uri.parse("http://127.0.0.1:" + port).buildUpon();
            if (baseUrl != null && !baseUrl.isEmpty()) address.appendPath(baseUrl);
            Uri uri = address.build();
            Intent webPageIntent = new Intent(Intent.ACTION_VIEW, uri);
            webPageIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent webPagePendingIntent = PendingIntent.getActivity(this, 0, webPageIntent, flags);
            builder.setContentIntent(webPagePendingIntent);
            builder.setContentText(getString(R.string.streaming_service_notification_content, port));
        }

        startForeground(PERSISTENT_NOTIFICATION_ID, builder.build());

        try {
        switch (protocol) {
            case SERVE_FTP:
                runningProcess = rclone.serve(Rclone.SERVE_PROTOCOL_FTP, port, allowRemoteAccess, authenticationUsername, authenticationPassword, remote, servePath, baseUrl);
                break;
            case SERVE_WEBDAV:
                runningProcess = rclone.serve(Rclone.SERVE_PROTOCOL_WEBDAV, port, allowRemoteAccess, authenticationUsername, authenticationPassword, remote, servePath, baseUrl);
                break;
            case SERVE_DLNA:
                runningProcess = rclone.serve(Rclone.SERVE_PROTOCOL_DLNA, port, allowRemoteAccess, authenticationUsername, authenticationPassword, remote, servePath, baseUrl);
                break;
            case SERVE_HTTP:
            default:
                runningProcess = rclone.serve(Rclone.SERVE_PROTOCOL_HTTP, port, allowRemoteAccess, authenticationUsername, authenticationPassword, remote, servePath, baseUrl);
                break;
        }

        if (runningProcess == null) throw new java.io.IOException("Listener could not be started");
        transfer = new io.github.rareities.protonbridge.util.NativePipeTransfer(runningProcess, true);
        transfer.output().close();
        transfer.awaitSuccessfulExit();
        } catch (Exception error) {
            String raw = error.getMessage() == null ? "Listener failed" : error.getMessage();
            final String diagnostic = io.github.rareities.protonbridge.util.DiagnosticRedactor.redact(
                    baseUrl == null || baseUrl.isEmpty() ? raw : raw.replace(baseUrl, "[REDACTED]"));
            FLog.e(TAG, "Streaming stopped: %s", diagnostic);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    android.widget.Toast.makeText(getApplicationContext(), "Streaming stopped: " +
                            diagnostic, android.widget.Toast.LENGTH_LONG).show());
        } finally { stopForeground(true); }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (transfer != null) transfer.cancel();
        else if (runningProcess != null) runningProcess.destroy();
    }

    @Override public void onTimeout(int startId, int fgsType) {
        if (transfer != null) transfer.cancel();
        stopForeground(true);
        stopSelf(startId);
    }

    private void setNotificationChannel() {
        NotificationUtils.createNotificationChannel(this,
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
                getString(R.string.streaming_service_notification_channel_description)
        );
    }
}
