package io.github.rareities.protonbridge.RemoteConfig;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;
import io.github.rareities.protonbridge.Items.RemoteItem;
import io.github.rareities.protonbridge.R;
import io.github.rareities.protonbridge.Rclone;
import io.github.rareities.protonbridge.util.ProcessRunner;
import es.dmoral.toasty.Toasty;
import io.github.x0b.safdav.SafAccessProvider;
import io.github.x0b.safdav.file.SafConstants;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RemoteConfigHelper {
    private static final long CONFIG_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(2);
    private static final AtomicBoolean safSetupRunning = new AtomicBoolean();

    /** Only a completed, zero-exit native process can confirm a saved remote. */
    public static final class Result {
        public final boolean success;
        public final boolean cancelled;
        public final boolean timedOut;
        public final Integer exitCode;

        private Result(boolean success, boolean cancelled, boolean timedOut, Integer exitCode) {
            this.success = success;
            this.cancelled = cancelled;
            this.timedOut = timedOut;
            this.exitCode = exitCode;
        }
    }

    public static String getRemotePath(String path, RemoteItem selectedRemote) {
        String remotePath;
        if (selectedRemote.isRemoteType(RemoteItem.LOCAL)) {
            if (path.equals("//" + selectedRemote.getName())) {
                remotePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/";
            } else {
                remotePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + path;
            }
        } else {
            if (path.equals("//" + selectedRemote.getName())) {
                remotePath = selectedRemote.getName() + ":";
            } else {
                remotePath = selectedRemote.getName() + ":" + path;
            }
        }
        return remotePath;
    }

    public static void updateAndWait(Context context, ArrayList<String> options) {
        scheduleForUi(context, options, true);
    }

    public static void setupAndWait(Context context, ArrayList<String> options) {
        scheduleForUi(context, options, false);
    }

    /**
     * Run on an IO thread. Rclone.configUpdate deliberately uses the same
     * create command as the existing editor; preserve its name/type/options.
     */
    public static Result runConfig(Context context, ArrayList<String> options, boolean update,
                                   ProcessRunner runner, ProcessRunner.StopCheck stop) {
        if (stop.stopped()) return new Result(false, true, false, null);
        Process child;
        try {
            Rclone rclone = new Rclone(context);
            ArrayList<String> arguments = new ArrayList<>(options);
            child = update ? rclone.configUpdate(arguments) : rclone.configCreate(arguments);
        } catch (RuntimeException error) {
            // In particular, never expose credential-bearing command arguments
            // or native error text through UI errors or logcat.
            return new Result(false, stop.stopped(), false, null);
        }
        return awaitProcess(child, runner, stop, CONFIG_TIMEOUT_MS);
    }

    /** No child output is retained, logged, or exposed to the remote editor. */
    @VisibleForTesting
    static Result awaitProcess(Process child, ProcessRunner runner, ProcessRunner.StopCheck stop,
                               long timeoutMillis) {
        final long started = System.nanoTime();
        final AtomicBoolean timedOut = new AtomicBoolean();
        try {
            ProcessRunner.Outcome outcome = runner.run(child, () -> {
                if (stop.stopped()) return true;
                if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) >= timeoutMillis) {
                    timedOut.set(true);
                    return true;
                }
                return false;
            }, null, null);
            return new Result(outcome.success() && !timedOut.get(),
                    outcome.cancelled && !timedOut.get(), timedOut.get(), outcome.exitCode);
        } catch (RuntimeException error) {
            runner.requestStop();
            return new Result(false, stop.stopped(), timedOut.get(), null);
        }
    }

    /** Compatibility entry points for settings callers: never wait on the UI thread. */
    private static void scheduleForUi(Context context, ArrayList<String> options, boolean update) {
        Context app = context.getApplicationContext();
        ArrayList<String> snapshot = new ArrayList<>(options);
        new Thread(() -> {
            Result result = runConfig(app, snapshot, update, new ProcessRunner(), () -> false);
            new Handler(Looper.getMainLooper()).post(() -> showResult(app, result));
        }, "rclone-config-editor").start();
    }

    private static void showResult(Context context, Result result) {
        if (result.success) {
            Toasty.success(context, context.getString(R.string.remote_creation_success), Toast.LENGTH_SHORT, true).show();
        } else if (!result.cancelled) {
            Toasty.error(context, context.getString(R.string.error_creating_remote), Toast.LENGTH_SHORT, true).show();
        }
    }

    public static void enableSaf(Context context) {
        final String user;
        final String pass;
        try {
            user = SafAccessProvider.getUser(context);
            pass = SafAccessProvider.getPassword(context);
        } catch (RuntimeException unavailable) {
            Context app = context.getApplicationContext();
            new Handler(Looper.getMainLooper()).post(() -> {
                // The caller may persist its enable toggle after this method returns.
                PreferenceManager.getDefaultSharedPreferences(app).edit()
                        .putBoolean(app.getString(R.string.pref_key_enable_saf), false).apply();
                Toasty.error(app, app.getString(R.string.error_creating_remote), Toast.LENGTH_SHORT, true).show();
            });
            return;
        }
        ArrayList<String> options = new ArrayList<>();
        options.add(SafConstants.SAF_REMOTE_NAME);
        options.add("webdav");
        options.add("url");
        options.add(SafConstants.SAF_REMOTE_URL);
        options.add("user");
        options.add(user);
        options.add("pass");
        options.add(pass);
        // The preference is written by FileAccessSettingsFragment after this
        // callback returns, so defer checking it until the next main-loop turn.
        if (!safSetupRunning.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!isSafEnabled(app)) {
                safSetupRunning.set(false);
                return;
            }
            new Thread(() -> {
                try {
                    Result result = runConfig(app, options, false, new ProcessRunner(), () -> !isSafEnabled(app));
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (isSafEnabled(app)) showResult(app, result);
                    });
                } finally {
                    safSetupRunning.set(false);
                }
            }, "rclone-saf-config").start();
        });
    }

    private static boolean isSafEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                context.getString(R.string.pref_key_enable_saf), false);
    }
}
