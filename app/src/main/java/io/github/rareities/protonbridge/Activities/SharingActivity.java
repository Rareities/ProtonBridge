package io.github.rareities.protonbridge.Activities;

import static io.github.rareities.protonbridge.util.ActivityHelper.tryStartService;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import io.github.rareities.protonbridge.Dialogs.Dialogs;
import io.github.rareities.protonbridge.Dialogs.LoadingDialog;
import io.github.rareities.protonbridge.Fragments.ShareFragment;
import io.github.rareities.protonbridge.Fragments.ShareRemotesFragment;
import io.github.rareities.protonbridge.Items.RemoteItem;
import io.github.rareities.protonbridge.R;
import io.github.rareities.protonbridge.Rclone;
import io.github.rareities.protonbridge.RuntimeConfiguration;
import io.github.rareities.protonbridge.util.ActivityHelper;
import io.github.rareities.protonbridge.util.FLog;
import io.github.rareities.protonbridge.workmanager.EphemeralTaskManager;
import es.dmoral.toasty.Toasty;

public class SharingActivity extends AppCompatActivity implements ShareRemotesFragment.OnRemoteClickListener,
        ShareFragment.OnShareDestinationSelected {

    private static final String TAG = "SharingActivity";
    private Fragment fragment;
    private ArrayList<String> uploadList;
    private boolean isDataReady;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(RuntimeConfiguration.attach(this, newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        ActivityHelper.applyTheme(this);
        setContentView(R.layout.activity_sharing);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Rclone rclone = new Rclone(this);
        uploadList = new ArrayList<>();

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            copyFile(intent);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            copyFiles(intent);
        } else {
            finish();
            return;
        }

        if (rclone.isConfigEncrypted() || !rclone.isConfigFileCreated() || rclone.getRemotes().isEmpty()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder
                    .setTitle(R.string.app_not_configured)
                    .setMessage(R.string.open_app_to_configure)
                    .setPositiveButton(R.string.ok, (dialog, which) -> finish())
                    .show();
        } else {
            startRemotesFragment();
        }
    }

    @Override
    public void onBackPressed() {
        if (fragment != null && fragment instanceof ShareFragment) {
            if (((ShareFragment)fragment).onBackButtonPressed()) {
                return;
            }
        }
        super.onBackPressed();
    }

    private void startRemotesFragment() {
        fragment = ShareRemotesFragment.newInstance();
        FragmentManager fragmentManager = getSupportFragmentManager();

        for (int i = 0; i < fragmentManager.getBackStackEntryCount(); i++) {
            fragmentManager.popBackStack();
        }

        fragmentManager.beginTransaction().replace(R.id.flFragment, fragment).commit();
    }

    @Override
    public void onRemoteClick(RemoteItem remote) {
        startRemote(remote);
    }

    private void startRemote(RemoteItem remoteItem) {
        fragment = ShareFragment.newInstance(remoteItem);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.flFragment, fragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    @Override
    public void onShareDestinationSelected(RemoteItem remote, String path) {
        new UploadTask(this, remote, path).execute();
    }

    private void copyFile(Intent intent) {
        Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri == null) {
            finish();
            return;
        }
        isDataReady = false;
        new CopyFile(this, uri).execute();
    }

    private void copyFiles(Intent intent) {
        ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (uris == null) {
            finish();
            return;
        }
        isDataReady = false;
        new CopyFile(this, uris).execute();
    }


    @SuppressLint("StaticFieldLeak")
    private class UploadTask extends AsyncTask<Void, Void, Void> {

        RemoteItem remote;
        String path;
        Context context;
        LoadingDialog loadingDialog;

        UploadTask(Context context, RemoteItem remote, String path) {
            this.context = context;
            this.remote = remote;
            this.path = path;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            loadingDialog = new LoadingDialog()
                    .setTitle(R.string.loading)
                    .setNegativeButton(R.string.cancel)
                    .setOnNegativeListener(() -> cancel(true));
            loadingDialog.show(getSupportFragmentManager(), "loading dialog");
        }

        @Override
        protected Void doInBackground(Void... voids) {
            while (!isDataReady) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    FLog.e(TAG, "UploadTask/doInBackground: error waiting for data", e);
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Dialogs.dismissSilently(loadingDialog);

            for (String uploadFile : uploadList) {
                EphemeralTaskManager.Companion.queueUpload(this.context, remote, uploadFile, path);
            }
            finish();
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class CopyFile extends AsyncTask<Void, Void, Boolean> {

        private static final String TAG = "SharingActvty/CopyFile";
        private Context context;
        private ArrayList<Uri> uris;

        CopyFile(Context context, Uri uri) {
            this.context = context;
            uris = new ArrayList<>();
            uris.add(uri);
        }

        CopyFile(Context context, ArrayList<Uri> uris) {
            this.context = context;
            this.uris = uris;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            boolean success = true;
            for (Uri uri : uris) {
                String fileName;
                if (null == uri || null == uri.getScheme() || null == uri.getPath()) {
                    FLog.w(TAG, "Can't copy invalid uri %s", uri);
                    success = false;
                    continue;
                }

                File outFile = null;
                boolean copied = false;
                try {
                    if ("file".equalsIgnoreCase(uri.getScheme())) {
                        File source = new File(uri.getPath());
                        io.github.rareities.protonbridge.util.PrivateFileStaging.requireOutside(source,
                                new File(getApplicationInfo().dataDir));
                        if (android.os.Build.VERSION.SDK_INT >= 24) {
                            io.github.rareities.protonbridge.util.PrivateFileStaging.requireOutside(source,
                                    createDeviceProtectedStorageContext().getDataDir());
                        }
                    }
                    fileName = resolveName(uri);
                    File cacheDir = io.github.rareities.protonbridge.util.PrivateFileStaging.directory(getCacheDir(), "share_staging");
                    outFile = io.github.rareities.protonbridge.util.PrivateFileStaging.target(cacheDir, fileName);
                    InputStream raw = getContentResolver().openInputStream(uri);
                    if (raw == null) throw new IOException("Shared content is unavailable");
                    try (InputStream in = raw;
                         OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile), 64 * 1024)) {
                        byte[] buf = new byte[64 * 1024];
                        int len;
                        while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                        out.flush();
                    }
                    uploadList.add(outFile.getAbsolutePath());
                    copied = true;
                } catch (IOException | SecurityException e) {
                    FLog.w(TAG, "Shared content could not be staged safely");
                    success = false;
                } finally {
                    if (!copied && outFile != null) outFile.delete();
                }
            }
            return success;
        }

        @NonNull
        private String resolveName(@NonNull Uri uri) {
            String[] projection = {OpenableColumns.DISPLAY_NAME};
            try (Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
                if (null != cursor && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if(index>=0) {
                        String name = cursor.getString(index);
                        if (null != name) {
                            return name;
                        }
                    }
                }
            }
            List<String> segments = uri.getPathSegments();
            if (segments.size() >= 1) {
                return segments.get(segments.size() - 1);
            }
            return "unnamed";
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            if (!success) {
                Toasty.error(context, getString(R.string.error_retrieving_files), Toast.LENGTH_LONG, true).show();
                finish();
            }
            isDataReady = true;
        }
    }
}
