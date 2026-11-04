package io.github.rareities.protonbridge.Activities;

import static io.github.rareities.protonbridge.util.ActivityHelper.tryStartActivityForResult;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.core.view.WindowCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.github.rareities.protonbridge.AppShortcutsHelper;
import io.github.rareities.protonbridge.BuildConfig;
import io.github.rareities.protonbridge.Database.json.Importer;
import io.github.rareities.protonbridge.workmanager.SessionGuardianScheduler;
import io.github.rareities.protonbridge.Database.json.SharedPreferencesBackup;
import io.github.rareities.protonbridge.Dialogs.Dialogs;
import io.github.rareities.protonbridge.Dialogs.InputDialog;
import io.github.rareities.protonbridge.Dialogs.LoadingDialog;
import io.github.rareities.protonbridge.Fragments.FileExplorerFragment;
import io.github.rareities.protonbridge.Fragments.LogFragment;
import io.github.rareities.protonbridge.Fragments.PermissionFragment;
import io.github.rareities.protonbridge.Fragments.RemotesFragment;
import io.github.rareities.protonbridge.Fragments.TasksFragment;
import io.github.rareities.protonbridge.Fragments.TriggerFragment;
import io.github.rareities.protonbridge.Items.RemoteItem;
import io.github.rareities.protonbridge.R;
import io.github.rareities.protonbridge.Rclone;
import io.github.rareities.protonbridge.RemoteConfig.InternxtReauth;
import io.github.rareities.protonbridge.RemoteConfig.RemoteConfigHelper;
import io.github.rareities.protonbridge.RuntimeConfiguration;
import io.github.rareities.protonbridge.Services.StreamingService;
import io.github.rareities.protonbridge.Services.TriggerService;
import io.github.rareities.protonbridge.util.ActivityHelper;
import io.github.rareities.protonbridge.util.FLog;
import io.github.rareities.protonbridge.util.PermissionManager;
import io.github.rareities.protonbridge.util.SharedPreferencesUtil;
import io.github.rareities.protonbridge.updates.UpdateChecker;
import es.dmoral.toasty.Toasty;
import java9.util.stream.Stream;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        RemotesFragment.OnRemoteClickListener,
        RemotesFragment.AddRemoteToNavDrawer,
        InputDialog.OnPositive {

    private static final String TAG = "MainActivity";
    public static final String MAIN_ACTIVITY_START_LOG = "MAIN_ACTIVITY_START_LOG";
    public static final String MAIN_ACTIVITY_START_IMPORT = "MAIN_ACTIVITY_START_IMPORT";
    public static final String MAIN_ACTIVITY_START_EXPORT = "MAIN_ACTIVITY_START_EXPORT";
    public static final String MAIN_ACTIVITY_START_REAUTH = "MAIN_ACTIVITY_START_REAUTH";
    private static final int READ_REQUEST_CODE = 42; // code when opening rclone config file
    private static final int REQUEST_PERMISSION_CODE = 62; // code when requesting permissions
    private static final int REQUEST_PERMISSION_CODE_POST_NOTIFICATIONS = 63;
    private static final int SETTINGS_CODE = 71; // code when coming back from settings
    private static final int WRITE_REQUEST_CODE = 81; // code when exporting config
    private final String FILE_EXPLORER_FRAGMENT_TAG = "io.github.rareities.protonbridge.MAIN_ACTIVITY_FILE_EXPLORER_TAG";
    private NavigationView navigationView;
    private DrawerLayout drawer;
    private Rclone rclone;
    private Fragment fragment;
    private Context context;
    private HashMap<Integer, RemoteItem> drawerPinnedRemoteIds;
    private int availableDrawerPinnedRemoteId;
    private volatile boolean protectedConfigReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        ActivityHelper.applyTheme(this);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean allPermissionsGranted = (new PermissionManager(this)).hasAllRequiredPermissions();
        boolean completedIntroOnce = OnboardingActivity.Companion.completedIntro(this);
        if(!allPermissionsGranted || !completedIntroOnce) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }


        context = this;
        drawerPinnedRemoteIds = new HashMap<>();
        availableDrawerPinnedRemoteId = 2;
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionbar = getSupportActionBar();
        if (actionbar != null) {
            actionbar.setDisplayHomeAsUpEnabled(true);
            actionbar.setHomeAsUpIndicator(R.drawable.ic_menu);
        }

        drawer = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        rclone = new Rclone(this);

        findViewById(R.id.locked_config_btn).setOnClickListener(v -> askForConfigPassword());

        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();

        findViewById(R.id.navAbout).setOnClickListener(v -> {
            Intent aboutIntent = new Intent(this, AboutActivity.class);
            startActivity(aboutIntent);
        });

        findViewById(R.id.navSettings).setOnClickListener(v -> {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            tryStartActivityForResult(this, settingsIntent, SETTINGS_CODE);
        });

        updatePermissionFragmentVisibility();
        startProtectedConfigInitialisation(savedInstanceState, intent, sharedPreferences);
    }

    /** Native encryption and validation can each take seconds, so keep them off the UI thread. */
    private void startProtectedConfigInitialisation(Bundle savedInstanceState, Intent intent, SharedPreferences preferences) {
        LoadingDialog loading = new LoadingDialog().setTitle(R.string.working).setCanCancel(false);
        loading.show(getSupportFragmentManager(), "initial configuration protection");
        new Thread(() -> {
            IOException failure = null;
            boolean unlockRequired = false;
            try {
                rclone.prepareProtectedConfig();
                unlockRequired = rclone.isConfigEncrypted();
            } catch (IOException e) {
                failure = e;
            }
            final IOException startupFailure = failure;
            final boolean needsUnlock = unlockRequired;
            runOnUiThread(() -> {
                Dialogs.dismissSilently(loading);
                if (isFinishing() || isDestroyed()) return;
                if (startupFailure != null) {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Configuration unavailable")
                            .setMessage("The encrypted configuration could not be prepared. You can unlock the existing file or import an encrypted backup. No configuration was deleted.")
                            .setPositiveButton("Import backup", (dialog, which) -> importConfigFile())
                            .setNeutralButton("Unlock existing", (dialog, which) -> askForConfigPassword())
                            .setNegativeButton(android.R.string.cancel, null).show();
                    return;
                }
                if (needsUnlock) {
                    askForConfigPassword();
                    return;
                }
                protectedConfigReady = true;
                showInitialContent(savedInstanceState, intent, preferences);
            });
        }, "protonbridge-config-startup").start();
    }

    private void showInitialContent(Bundle savedInstanceState, Intent intent, SharedPreferences sharedPreferences) {
        Bundle bundle = intent == null ? null : intent.getExtras();
        int lastVersionCode = sharedPreferences.getInt(getString(R.string.pref_key_version_code), -1);
        String lastVersionName = sharedPreferences.getString(getString(R.string.pref_key_version_name), "");
        int currentVersionCode = BuildConfig.VERSION_CODE / 10;
        String currentVersionName = BuildConfig.VERSION_NAME;
        if (lastVersionCode < currentVersionCode || !lastVersionName.equals(currentVersionName)) {
            if (lastVersionCode == 9) {
                AppShortcutsHelper.removeAllAppShortcuts(this);
            }
            startRemotesFragment();
            sharedPreferences.edit().putInt(getString(R.string.pref_key_version_code), currentVersionCode)
                    .putString(getString(R.string.pref_key_version_name), currentVersionName).apply();
        } else if (savedInstanceState != null) {
            fragment = getSupportFragmentManager().findFragmentByTag(FILE_EXPLORER_FRAGMENT_TAG);
            if (fragment instanceof FileExplorerFragment) {
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.replace(R.id.flFragment, fragment, FILE_EXPLORER_FRAGMENT_TAG).commit();
            } else startRemotesFragment();
        } else if (bundle != null && bundle.containsKey(AppShortcutsHelper.APP_SHORTCUT_REMOTE_NAME)) {
            String remoteName = bundle.getString(AppShortcutsHelper.APP_SHORTCUT_REMOTE_NAME);
            new Thread(() -> {
                RemoteItem remoteItem = rclone.getRemoteItemFromName(remoteName);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (remoteItem == null) {
                        Toasty.error(this, getString(R.string.remote_not_found), Toast.LENGTH_SHORT, true).show();
                        finish();
                    } else {
                        AppShortcutsHelper.reportAppShortcutUsage(this, remoteItem.getName());
                        startRemote(remoteItem, false);
                    }
                });
            }, "protonbridge-shortcut-remote").start();
        } else startRemotesFragment();
        handleIntentAction(intent);
        final boolean refreshShortcuts = lastVersionCode == 9;
        new Thread(() -> {
            List<RemoteItem> initialRemotes = rclone.getRemotes();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || !protectedConfigReady) return;
                pinRemotesToDrawer(initialRemotes);
                if (refreshShortcuts) AppShortcutsHelper.populateAppShortcuts(this, initialRemotes);
            });
        }, "protonbridge-initial-drawer").start();
        new TriggerService(context).queueTrigger();
        io.github.rareities.protonbridge.workmanager.SessionGuardianScheduler.schedule(this);
        (new UpdateChecker(this)).schedule();
    }

    /**
     * Dispatches intent actions handled outside the normal UI flow (log/import/
     * export views, and the Session Guardian "re-authenticate" deep link).
     * Called from both {@link #onCreate} and {@link #onNewIntent}: the activity
     * uses {@code launchMode="singleTop"}, so a notification tap while the app
     * is already foregrounded delivers to {@code onNewIntent}, not onCreate.
     */
    private void handleIntentAction(@Nullable Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        if (!io.github.rareities.protonbridge.util.InternalActivityAction.isAuthorized(this, intent)) return;
        if (MAIN_ACTIVITY_START_LOG.equals(action)) {
            startLogFragment();
        } else if (MAIN_ACTIVITY_START_IMPORT.equals(action)) {
            //todo: Migrate import and export out of the main activity
            startConfigImportFlow();
        } else if (MAIN_ACTIVITY_START_EXPORT.equals(action)) {
            //todo: Migrate import and export out of the main activity
            startConfigExportFlow();
        } else if (MAIN_ACTIVITY_START_REAUTH.equals(action)) {
            handleReauthIntent(intent);
        }
    }

    /**
     * Deep-link handler for the "Session expired" notification: opens the
     * remotes view and, if the named remote still exists and is an Internxt
     * remote, kicks off {@link InternxtReauth} directly so the user does not
     * have to find and long-press the remote themselves.
     */
    private void handleReauthIntent(@NonNull Intent intent) {
        String remoteName = intent.getStringExtra(AppShortcutsHelper.APP_SHORTCUT_REMOTE_NAME);
        if (remoteName == null) {
            startRemotesFragment();
            return;
        }
        startRemotesFragment();
        RemoteItem remoteItem = rclone.getRemoteItemFromName(remoteName);
        if (remoteItem == null) {
            Toasty.error(this, getString(R.string.remote_not_found), Toast.LENGTH_SHORT, true).show();
            return;
        }
        if ("internxt".equalsIgnoreCase(remoteItem.getTypeReadable())) {
            // Run on the UI thread: InternxtReauth is an AsyncTask whose dialogs
            // and ProgressDialog require an Activity context.
            new InternxtReauth(this, rclone, remoteItem.getName()).executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntentAction(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionFragmentVisibility();
        if (MAIN_ACTIVITY_START_LOG.equals(getIntent().getAction()) &&
                io.github.rareities.protonbridge.util.InternalActivityAction.isAuthorized(this, getIntent())) {
            startLogFragment();
            navigationView.setCheckedItem(R.id.nav_logs);
        }
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        requestPermissions();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(RuntimeConfiguration.attach(this, newBase));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home && !(fragment instanceof FileExplorerFragment)) {
            drawer.openDrawer(GravityCompat.START);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    public void openNavigationDrawer() {
        drawer.openDrawer(GravityCompat.START);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // result from file picker (for importing config file)
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Uri uri;
            if (data != null) {
                uri = data.getData();
                if (uri != null) {
                    promptBackupPassword(uri, false);
                }
            }
        } else if (requestCode == SETTINGS_CODE && resultCode == RESULT_OK) {
            if (data != null) {
                boolean themeChanged = data.getBooleanExtra(SettingsActivity.THEME_CHANGED, false);
                if (themeChanged) {
                    recreate();
                }
            }
        } else if (requestCode == WRITE_REQUEST_CODE && resultCode == RESULT_OK) {
            Uri uri;
            if (data != null) {
                uri = data.getData();
                if (uri != null) {
                    promptBackupPassword(uri, true);
                }
            }
        } else if (requestCode == FileExplorerFragment.STREAMING_INTENT_RESULT) {
            Intent serveIntent = new Intent(this, StreamingService.class);
            context.stopService(serveIntent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int i = 0; i < permissions.length; i++) {
            switch (permissions[i]) {
                case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                    // add/remove path aliases, depending on availability
                    RefreshLocalAliases refresh = new RefreshLocalAliases();
                    if (refresh.isRequired()) {
                        refresh.execute();
                    }
            }
        }
    }

    // Cache entries are owned by previews/uploads, not by this activity's rotation lifecycle.

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        boolean superOnBackPressed = true;

        // Always hide search icon when fragments go back
        View searchButton = this.findViewById(R.id.searchButton);
        searchButton.setVisibility(View.INVISIBLE);

        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (fragment != null) {
            if(fragment instanceof FileExplorerFragment){
                if (((FileExplorerFragment) fragment).onBackButtonPressed()) {
                    return;
                } else {
                    fragment = null;
                }
            } else if(fragment instanceof TasksFragment){
                startRemotesFragment();
                superOnBackPressed=false;
            } else if(fragment instanceof TriggerFragment){
                startRemotesFragment();
                superOnBackPressed=false;
            }
        }
        if(superOnBackPressed){
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.

        // Always hide search icon when fragments go back
        View searchButton = this.findViewById(R.id.searchButton);
        searchButton.setVisibility(View.INVISIBLE);

        int id = item.getItemId();
        navigationView.setCheckedItem(id);
        if (drawerPinnedRemoteIds.containsKey(id)) {
            startPinnedRemote(drawerPinnedRemoteIds.get(id));
            return true;
        }

        // set the last used fragment on each call.
        switch (id) {
            case R.id.nav_remotes:
            case R.id.nav_tasks:
            case R.id.nav_trigger:
            case R.id.nav_logs:
            case R.id.nav_permissions:
                SharedPreferencesUtil.Companion.setLastOpenFragment(this, id);
                break;
            case R.id.nav_import:
            case R.id.nav_export:
                SharedPreferencesUtil.Companion.setLastOpenFragment(this, R.id.nav_remotes);
                break;
        }
        startFragmentById(id);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void startFragmentById(int id){
        switch (id) {
            case R.id.nav_remotes:
                startRemotesFragment();
                break;
            case R.id.nav_tasks:
                startTasksFragment();
                break;
            case R.id.nav_trigger:
                startTriggerFragment();
                break;
            case R.id.nav_logs:
                startLogFragment();
                break;
            case R.id.nav_permissions:
                startPermissionFragment();
                break;
        }
    }

    private void startConfigExportFlow() {
        if (rclone.isConfigFileCreated()) {
            exportConfigFile();
        } else {
            Toasty.info(this,  getString(R.string.no_config_found), Toast.LENGTH_SHORT, true).show();
        }
    }

    private void startConfigImportFlow() {
        Uri configUri;
        if (rclone.isConfigFileCreated()) {
            warnUserAboutOverwritingConfiguration();
        } else if(null != (configUri = rclone.searchExternalConfig())) {
            askUseExternalConfig(configUri);
        } else {
            importConfigFile();
        }
    }

    private void startTasksFragment(){
        startFragment(TasksFragment.newInstance());
    }

    private void startTriggerFragment() {
        startFragment(TriggerFragment.newInstance());
    }

    private void startLogFragment() {
        startFragment(LogFragment.newInstance());
    }

    private void startPermissionFragment() {
        startFragment(PermissionFragment.Companion.newInstance());
    }

    private void startFragment(Fragment fragmentToStart) {
        fragment = fragmentToStart;
        FragmentManager fragmentManager = getSupportFragmentManager();

        for (int i = 0; i < fragmentManager.getBackStackEntryCount(); i++) {
            fragmentManager.popBackStack();
        }

        if (!isFinishing()) {
            fragmentManager.beginTransaction().replace(R.id.flFragment, fragment).commitAllowingStateLoss();
        }
    }

    private void pinRemotesToDrawer() {
        pinRemotesToDrawer(rclone.getRemotes());
    }

    private void pinRemotesToDrawer(List<RemoteItem> remoteItems) {
        Menu menu = navigationView.getMenu();
        MenuItem existingMenu = menu.findItem(1);
        if (existingMenu != null) {
            return;
        }

        SubMenu subMenu = menu.addSubMenu(R.id.drawer_pinned_header, 1, Menu.NONE, R.string.nav_drawer_pinned_header);

        Collections.sort(remoteItems);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> renamedRemotes = sharedPreferences.getStringSet(getString(R.string.pref_key_renamed_remotes), new HashSet<>());
        for(RemoteItem item : remoteItems) {
            if(renamedRemotes.contains(item.getName())) {
                String displayName = sharedPreferences.getString(
                        getString(R.string.pref_key_renamed_remote_prefix, item.getName()), item.getName());
                item.setDisplayName(displayName);
            }
        }
        for (RemoteItem remoteItem : remoteItems) {
            if (remoteItem.isDrawerPinned()) {
                MenuItem menuItem = subMenu.add(R.id.nav_pinned, availableDrawerPinnedRemoteId, Menu.NONE, remoteItem.getDisplayName());
                drawerPinnedRemoteIds.put(availableDrawerPinnedRemoteId, remoteItem);
                availableDrawerPinnedRemoteId++;
                menuItem.setIcon(remoteItem.getRemoteIcon());
            }
        }
    }

    public void startRemotesFragment() {
        fragment = RemotesFragment.newInstance();
        FragmentManager fragmentManager = getSupportFragmentManager();

        for (int i = 0; i < fragmentManager.getBackStackEntryCount(); i++) {
            fragmentManager.popBackStack();
        }

        if (!isFinishing()) {
            fragmentManager.beginTransaction().replace(R.id.flFragment, fragment).commitAllowingStateLoss();
        }
        navigationView.setCheckedItem(R.id.nav_remotes);
    }

    private void warnUserAboutOverwritingConfiguration() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context, R.style.RoundedCornersDialog);
        builder.setTitle(R.string.replace_config_file_question);
        builder.setMessage(R.string.config_file_lost_statement);
        builder.setPositiveButton(R.string.continue_statement, (dialogInterface, i) -> {
            dialogInterface.cancel();
            Uri configUri;
            if(null != (configUri = rclone.searchExternalConfig())){
                askUseExternalConfig(configUri);
            } else {
                importConfigFile();
            }
        });
        builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> dialogInterface.cancel());
        builder.show();
    }

    public void askUseExternalConfig(final Uri uri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.config_use_external_question);
        builder.setMessage(context.getString(R.string.config_import_external_explain, uri.toString()));
        builder.setPositiveButton(R.string.continue_statement, (dialogInterface, i) -> {
            dialogInterface.cancel();
            new CopyConfigFile().execute(uri);
        });
        builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
            dialogInterface.cancel();
            importConfigFile();
        });
        builder.show();
    }

    private void askForConfigPassword() {
        findViewById(R.id.locked_config).setVisibility(View.VISIBLE);
        new InputDialog()
                .setTitle(R.string.config_password_protected)
                .setMessage(R.string.please_enter_password)
                .setNegativeButton(R.string.cancel)
                .setPositiveButton(R.string.okay_confirmation)
                .setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                .show(getSupportFragmentManager(), "input dialog");
    }

    /*
     * Input Dialog callback
     */
    @Override
    public void onPositive(String tag, String input) {
        new DecryptConfig().execute(input);
    }

    public void importConfigFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        tryStartActivityForResult(this, intent, READ_REQUEST_CODE);
    }

    public void exportConfigFile() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd_HHmmss");
        String localTime = date.format(Calendar.getInstance().getTime());

        intent.putExtra(Intent.EXTRA_TITLE, getString(R.string.app_name)+"-backup-"+localTime+".zip");
        tryStartActivityForResult(this, intent, WRITE_REQUEST_CODE);
    }

    private void promptBackupPassword(Uri uri, boolean exporting) {
        android.widget.LinearLayout fields = new android.widget.LinearLayout(this);
        fields.setOrientation(android.widget.LinearLayout.VERTICAL);
        fields.setPadding(32, 8, 32, 8);
        android.widget.EditText password = new android.widget.EditText(this);
        password.setHint("Configuration backup password");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setSaveEnabled(false);
        if (Build.VERSION.SDK_INT >= 26) password.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        fields.addView(password);
        android.widget.EditText confirmation = new android.widget.EditText(this);
        if (exporting) {
            confirmation.setHint("Confirm backup password");
            confirmation.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            confirmation.setSaveEnabled(false);
            if (Build.VERSION.SDK_INT >= 26) confirmation.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
            fields.addView(confirmation);
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(exporting ? "Encrypt configuration backup" : "Unlock configuration backup")
                .setMessage(exporting ? "Use at least 12 characters and keep the password separately. Credentials, task names, paths and settings are encrypted using an AES-256 ZIP. Only the fixed component names and sizes remain visible."
                        : "Enter the backup or rclone configuration password. Leave blank for a legacy unencrypted backup. Your current configuration is kept until all parts pass validation.")
                .setView(fields).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(exporting ? "Export" : "Import", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String value = password.getText().toString();
            try {
                if (exporting || !value.isEmpty()) io.github.rareities.protonbridge.util.NativeConfigEncryption.validatePassword(value);
                if (exporting && (value.length() < 12 || !value.equals(confirmation.getText().toString()))) {
                    password.setError("Use matching passwords of at least 12 characters");
                    return;
                }
            } catch (IllegalArgumentException error) { password.setError(error.getMessage()); return; }
            password.setText("");
            confirmation.setText("");
            dialog.dismiss();
            if (exporting) new ExportProtectedConfig(uri).execute(value);
            else new CopyConfigFile(value).execute(uri);
        }));
        dialog.show();
    }

    @SuppressLint("StaticFieldLeak")
    private class ExportProtectedConfig extends AsyncTask<String, Void, String> {
        private final Uri destination;
        private LoadingDialog loading;
        ExportProtectedConfig(Uri destination) { this.destination = destination; }
        @Override protected void onPreExecute() {
            loading = new LoadingDialog().setTitle(R.string.working).setCanCancel(false);
            loading.show(getSupportFragmentManager(), "protected export");
        }
        @Override protected String doInBackground(String... passwords) {
            try { rclone.exportConfigFile(destination, passwords[0]); return null; }
            catch (Exception error) { return io.github.rareities.protonbridge.util.DiagnosticRedactor.redact(error.getMessage()); }
            finally { java.util.Arrays.fill(passwords, null); }
        }
        @Override protected void onPostExecute(String error) {
            Dialogs.dismissSilently(loading);
            if (error == null) Toasty.success(context, "Backup saved with encrypted credentials and settings", Toast.LENGTH_LONG, true).show();
            else Toasty.error(context, "Backup failed: " + error, Toast.LENGTH_LONG, true).show();
        }
    }

    public void requestPermissions() {
        boolean refreshLocalAliases = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(getString(R.string.pref_key_refresh_local_aliases), true);
        if (refreshLocalAliases) {
            FLog.d(TAG, "Reloading local path aliases");
            RefreshLocalAliases refresh = new RefreshLocalAliases();
            if (refresh.isRequired()) {
                refresh.execute();
            }
        }
    }

    private void updatePermissionFragmentVisibility() {
        Menu navMenu = navigationView.getMenu();
        if((new PermissionManager(this).hasAllPermissions())) {
            navMenu.findItem(R.id.nav_permissions).setVisible(false);
        } else {
            navMenu.findItem(R.id.nav_permissions).setVisible(true);
        }

    }

    @Override
    public void onRemoteClick(RemoteItem remote) {
        startRemote(remote, true);
    }

    private void startRemote(RemoteItem remote, boolean addToBackStack) {
        fragment = FileExplorerFragment.newInstance(remote);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.flFragment, fragment, FILE_EXPLORER_FRAGMENT_TAG);
        if (addToBackStack) {
            transaction.addToBackStack(null);
        }
        transaction.commit();

        AppShortcutsHelper.reportAppShortcutUsage(this, remote.getName());
        //navigationView.getMenu().getItem(0).setChecked(false);
    }

    private void startPinnedRemote(RemoteItem remoteItem) {
        if (fragment != null && fragment instanceof FileExplorerFragment) {
            FragmentManager fragmentManager = getSupportFragmentManager();

            // this is the case when remote gets started from a shortcut
            // therefore back should exit the app, and not go into remotes screen
            if (fragmentManager.getBackStackEntryCount() == 0) {
                startRemote(remoteItem, false);
            } else {
                for (int i = 0; i < fragmentManager.getBackStackEntryCount(); i++) {
                    fragmentManager.popBackStack();
                }

                startRemote(remoteItem, true);
            }
        } else {
            startRemote(remoteItem, true);
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
    }

    @Override
    public void addRemoteToNavDrawer() {
        Menu menu = navigationView.getMenu();

        // remove all items and add them again so that it's in alpha order
        menu.removeItem(1);
        drawerPinnedRemoteIds.clear();
        availableDrawerPinnedRemoteId = 1;

        pinRemotesToDrawer();
    }

    @Override
    public void removeRemoteFromNavDrawer() {
        Menu menu = navigationView.getMenu();

        // remove all items and add them again so that it's in alpha order
        menu.removeItem(1);
        drawerPinnedRemoteIds.clear();
        availableDrawerPinnedRemoteId = 1;

        pinRemotesToDrawer();
    }

    @SuppressLint("StaticFieldLeak")
    private class CopyConfigFile extends AsyncTask<Uri, Void, Boolean> {

        private String importPassword;
        CopyConfigFile() { this(null); }
        CopyConfigFile(String password) { importPassword = password; }

        private final int SUCCESS_IMPORT = 0;
        private final int FAILURE_UNSPECIFIED = 1;
        private final int FAILURE_RCLONE_CONF_NOT_VALID = 2;
        private final int FAILURE_ZIP_NO_JSON = 3;
        private final int FAILURE_ZIP_INVALID_JSON = 4;
        private final int FAILURE_ZIP_INVALID_CONF = 5;
        private final int FAILURE_ZIP_MISSING_CONF = 6;

        private int statusCode = FAILURE_UNSPECIFIED;
        private String failureDetail = "";
        private LoadingDialog loadingDialog;
        private boolean importedConfigLocked;


        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            findViewById(R.id.locked_config).setVisibility(View.GONE);
            loadingDialog = new LoadingDialog()
                    .setTitle(R.string.copying_rclone_config)
                    .setCanCancel(false);
            loadingDialog.show(getSupportFragmentManager(), "loading dialog");
        }

        // ZIP parts are validated together and committed with a private rollback journal.

        @Override
        protected Boolean doInBackground(Uri... uris) {

            String suppliedPassword = importPassword;
            importPassword = null;

            ContentResolver resolver = context.getContentResolver();
            String mime = resolver.getType(uris[0]);

            boolean zip = "application/zip".equals(mime);
            if (!zip) {
                try (java.io.InputStream stream = resolver.openInputStream(uris[0])) {
                    zip = stream != null && stream.read() == 'P' && stream.read() == 'K';
                } catch (IOException e) {
                    statusCode = FAILURE_UNSPECIFIED;
                    return false;
                }
            }
            if (zip) {
                try {
                    io.github.rareities.protonbridge.Database.json.BackupRestore.restore(context, rclone, uris[0], suppliedPassword);
                    importedConfigLocked = rclone.isConfigEncrypted();
                    statusCode = SUCCESS_IMPORT;
                    return true;
                } catch (Exception e) {
                    statusCode = FAILURE_ZIP_INVALID_JSON;
                    failureDetail = io.github.rareities.protonbridge.util.DiagnosticRedactor.redact(e.getMessage());
                    return false;
                }
            }

            try {
                boolean validRclone = rclone.copyConfigFile(uris[0], suppliedPassword);
                if(validRclone) {
                    importedConfigLocked = rclone.isConfigEncrypted();
                    statusCode = SUCCESS_IMPORT;
                    return true;
                }
                statusCode = FAILURE_RCLONE_CONF_NOT_VALID;
                return false;
            } catch (IOException e) {
                statusCode = FAILURE_RCLONE_CONF_NOT_VALID;
                failureDetail = io.github.rareities.protonbridge.util.DiagnosticRedactor.redact(e.getMessage());
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            Dialogs.dismissSilently(loadingDialog);

            String errorMessage = getString(R.string.copying_rclone_config_fail);
            if (!success) {
                switch (statusCode) {
                    case FAILURE_RCLONE_CONF_NOT_VALID:
                    case FAILURE_ZIP_INVALID_CONF:
                        errorMessage = getString(R.string.import_configuration_fail_rclone);
                        break;
                    case FAILURE_ZIP_MISSING_CONF:
                        errorMessage = getString(R.string.import_configuration_fail_zip_conf_missing);
                        break;
                    case FAILURE_ZIP_NO_JSON:
                    case FAILURE_ZIP_INVALID_JSON:
                        errorMessage = getString(R.string.import_configuration_fail_zip_json_missing);
                        break;
                }
                if (!failureDetail.isEmpty()) errorMessage += "\n" + failureDetail;
                Toasty.error(context, errorMessage, Toast.LENGTH_LONG, true).show();
                return;
            }

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            if(sharedPreferences.getBoolean(getString(R.string.pref_key_enable_saf), false)){
                RemoteConfigHelper.enableSaf(context);
            }

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(getString(R.string.shared_preferences_pinned_remotes));
            editor.remove(getString(R.string.shared_preferences_drawer_pinned_remotes));
            editor.remove(getString(R.string.shared_preferences_hidden_remotes));
            editor.remove(getString(R.string.pref_key_accessible_storage_locations));
            editor.apply();

            if (importedConfigLocked) {
                askForConfigPassword();
            } else {
                protectedConfigReady = true;
                AppShortcutsHelper.removeAllAppShortcuts(context);
                startRemotesFragment();
                new Thread(() -> {
                    List<RemoteItem> updated = rclone.getRemotes();
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        AppShortcutsHelper.populateAppShortcuts(context, updated);
                        pinRemotesToDrawer(updated);
                    });
                }, "protonbridge-import-remotes").start();
            }
        }
    }

    private class RefreshLocalAliases extends AsyncTask<Void, Void, Boolean> {

        private String EMULATED = "5d44cd8d-397c-4107-b79b-17f2b6a071e8";

        private LoadingDialog loadingDialog;

        private boolean isPermissable(File file) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                return Environment.isExternalStorageLegacy(file);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return Environment.isExternalStorageManager(file);
            } else {
                return true;
            }
        }

        protected boolean isRequired() {
            String[] externalVolumes = null;
            String persisted = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(getString(R.string.pref_key_accessible_storage_locations), null);
            if(null != persisted) {
                externalVolumes = persisted.split("\\|");
            }
            String[] current = Stream.of(context.getExternalFilesDirs(null))
                    .filter(f -> f != null)
                    .map(this::getRootOrSelf)
                    .filter(this::isPermissable)
                    .map(File::getAbsolutePath)
                    .toArray(String[]::new);

            if(Arrays.deepEquals(externalVolumes, current)) {
                FLog.d(TAG, "Storage volumes not changed, no refresh required");
                return false;
            } else {
                FLog.d(TAG, "Storage volumnes changed, refresh required");
                externalVolumes = current;
                persisted = TextUtils.join("|", current);
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .putString(getString(R.string.pref_key_accessible_storage_locations), persisted).apply();
                return true;
            }
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if(isFinishing() || isDestroyed() || getSupportFragmentManager().isStateSaved()) {
                FLog.w(TAG, "Invalid state, stopping drive refresh");
                cancel(true);
                return;
            }
            View v = findViewById(R.id.locked_config);
            if (v != null) {
                v.setVisibility(View.GONE);
            }
            loadingDialog = new LoadingDialog()
                    .setTitle(R.string.refreshing_local_alias_remotes)
                    .setCanCancel(false);
            loadingDialog.show(getSupportFragmentManager(), "loading dialog");
        }

        @Override
        protected Boolean doInBackground(Void... aVoid) {
            if (isCancelled()) {
                return null;
            }
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            Set<String> generated = pref.getStringSet(getString(R.string.pref_key_local_alias_remotes), new HashSet<>());
            Set<String> renamed = pref.getStringSet(getString(R.string.pref_key_renamed_remotes), new HashSet<>());
            SharedPreferences.Editor editor = pref.edit();
            for(String remote : generated) {
                rclone.deleteRemote(remote);
                renamed.remove(remote);
                editor.remove(getString(R.string.pref_key_renamed_remote_prefix, remote));
            }
            editor.putStringSet(getString(R.string.pref_key_renamed_remotes), renamed);
            editor.apply();
            File[] dirs = context.getExternalFilesDirs(null);
            for(File file : dirs) {
                // May be null if the path is currently not available
                if (null == file) {
                    continue;
                }
                if (file.getPath().contains(BuildConfig.APPLICATION_ID)) {
                    try {
                        File root = getVolumeRoot(file);
                        if (root.canRead()) {
                            addLocalRemote(root);
                        } else if (file.canRead()){
                            addLocalRemote(file);
                        }
                    } catch (NullPointerException | IOException e) {
                        // ignored, this is not a valid file
                        FLog.w(TAG, "doInBackground: could not read file", e);
                    }
                }
            }
            return null;
        }

        private File getRootOrSelf(File file) {
            File root = getVolumeRoot(file);
            if (root.canRead()) {
                return root;
            } else {
                return file;
            }
        }

        private File getVolumeRoot(File file) {
            String path = file.getAbsolutePath();
            int levelsUp = 0;
            int index = path.length();
            while(levelsUp++ <= 3) {
                index = path.lastIndexOf('/', index-1);
            }
            return new File(path.substring(0, index));
        }

        private void addLocalRemote(File root) throws IOException {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            String name = root.getCanonicalPath();
            String id = UUID.randomUUID().toString();
            try {
                if (Environment.isExternalStorageEmulated(root)) {
                    id = EMULATED;
                }
            } catch (IllegalArgumentException e) {
                FLog.e(TAG, "RefreshLocalAliases/addLocalRemote: %s is not a valid path", e, name);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                StorageManager storageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
                StorageVolume storageVolume = storageManager.getStorageVolume(root);
                if (null != storageVolume) {
                    name = storageVolume.getDescription(context);
                    if (null != storageVolume.getUuid()) {
                        id = storageVolume.getUuid();
                    }
                }
            }

            String path = root.getAbsolutePath();
            ArrayList<String> options = new ArrayList<>();
            options.add(id);
            options.add("alias");
            options.add("remote");
            options.add(path);
            FLog.d(TAG, "Adding local remote [%s] remote = %s", id, path);
            Process process = rclone.configCreate(options);
            if (null == process) {
                return;
            }
            try {
                process.waitFor();
                if (process.exitValue() != 0) {
                    FLog.w(TAG, "addLocalRemote: process error");
                    return;
                }
            } catch (InterruptedException e) {
                FLog.e(TAG, "addLocalRemote: process error", e);
                return;
            }
            Set<String> renamedRemotes = pref.getStringSet(getString(R.string.pref_key_renamed_remotes), new HashSet<>());
            Set<String> pinnedRemotes = pref.getStringSet(getString(R.string.shared_preferences_drawer_pinned_remotes), new HashSet<>());
            Set<String> generatedRemotes = pref.getStringSet(getString(R.string.pref_key_local_alias_remotes), new HashSet<>());
            renamedRemotes.add(id);
            pinnedRemotes.add(id);
            generatedRemotes.add(id);
            pref.edit()
                    .putStringSet(getString(R.string.pref_key_renamed_remotes), renamedRemotes)
                    .putString(getString(R.string.pref_key_renamed_remote_prefix, id), name)
                    .putStringSet(getString(R.string.shared_preferences_drawer_pinned_remotes), pinnedRemotes)
                    .putStringSet(getString(R.string.pref_key_local_alias_remotes), generatedRemotes)
                    .apply();
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            Dialogs.dismissSilently(loadingDialog);
            AppShortcutsHelper.removeAllAppShortcuts(context);
            AppShortcutsHelper.populateAppShortcuts(context, rclone.getRemotes());
            pinRemotesToDrawer();

            if (fragment instanceof RemotesFragment) {
                startRemotesFragment();
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class DecryptConfig extends AsyncTask<String, Void, Boolean> {

        private LoadingDialog loadingDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            loadingDialog = new LoadingDialog()
                    .setTitle(R.string.working)
                    .setCanCancel(false);
            loadingDialog.show(getSupportFragmentManager(), "loading dialog");
        }

        @Override
        protected Boolean doInBackground(String... strings) {
            return rclone.decryptConfig(strings[0]);
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            Dialogs.dismissSilently(loadingDialog);
            if (!success) {
                Toasty.error(context, getString(R.string.error_unlocking_config), Toast.LENGTH_LONG, true).show();
                askForConfigPassword();
            } else {
                findViewById(R.id.locked_config).setVisibility(View.GONE);
                if (!protectedConfigReady) {
                    protectedConfigReady = true;
                    showInitialContent(null, getIntent(), PreferenceManager.getDefaultSharedPreferences(context));
                } else {
                    AppShortcutsHelper.removeAllAppShortcuts(context);
                    startRemotesFragment();
                    new Thread(() -> {
                        List<RemoteItem> updated = rclone.getRemotes();
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            AppShortcutsHelper.populateAppShortcuts(context, updated);
                            pinRemotesToDrawer(updated);
                        });
                    }, "protonbridge-unlock-remotes").start();
                }
            }
        }
    }
}
