package com.winlator;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.contentdialog.AboutDialog;
import com.winlator.core.AppUtils;
import com.winlator.core.Callback;
import com.winlator.core.LocaleHelper;
import com.winlator.core.PreloaderDialog;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.RootFSInstaller;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "MainActivity";
    public static final boolean DEBUG_MODE = false;
    public static final @IntRange(from = 1, to = 19) byte CONTAINER_PATTERN_COMPRESSION_LEVEL = 9;
    public static final byte PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 1;
    public static final byte OPEN_FILE_REQUEST_CODE = 2;
    public static final byte EDIT_INPUT_CONTROLS_REQUEST_CODE = 3;
    public static final byte OPEN_DIRECTORY_REQUEST_CODE = 4;
    private static final int ROOTFS_TIMEOUT_SECONDS = 120;
    
    private DrawerLayout drawerLayout;
    public final PreloaderDialog preloaderDialog = new PreloaderDialog(this);
    private boolean editInputControls = false;
    private int selectedProfileId;
    private Callback<Uri> openFileCallback;
    private SharedPreferences preferences;
    private Fragment currentFragment;
    private boolean isAppReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        splashScreen.setKeepOnScreenCondition(() -> !isAppReady);
        
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        drawerLayout = findViewById(R.id.DrawerLayout);
        NavigationView navigationView = findViewById(R.id.NavigationView);
        navigationView.setNavigationItemSelectedListener(this);

        setSupportActionBar(findViewById(R.id.Toolbar));
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        Intent intent = getIntent();
        editInputControls = intent.getBooleanExtra("edit_input_controls", false);
        
        if (editInputControls) {
            selectedProfileId = intent.getIntExtra("selected_profile_id", 0);
            actionBar.setHomeAsUpIndicator(R.drawable.icon_action_bar_back);
            onNavigationItemSelected(navigationView.getMenu().findItem(R.id.menu_item_input_controls));
            navigationView.setCheckedItem(R.id.menu_item_input_controls);
            isAppReady = true;
        }
        else {
            actionBar.setHomeAsUpIndicator(R.drawable.icon_action_bar_menu);
            initEnvironment();
        }
    }

    private void initEnvironment() {
        if (requestAppPermissions()) {
            return;
        }

        RootFS rootFS = RootFS.find(this);
        if (rootFS != null && rootFS.isValid() && rootFS.getVersion() >= RootFSInstaller.LATEST_VERSION) {
            Log.d(TAG, "RootFS ready");
            onEnvironmentReady();
            return;
        }

        Log.d(TAG, "RootFS needs installation");
        RootFSInstaller.installIfNeeded(this);

        new Thread(() -> {
            int attempts = 0;
            while (!isFinishing() && !isDestroyed() && attempts < ROOTFS_TIMEOUT_SECONDS) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
                RootFS currentRootFS = RootFS.find(MainActivity.this);
                if (currentRootFS != null && currentRootFS.isValid() && currentRootFS.getVersion() >= RootFSInstaller.LATEST_VERSION) {
                    Log.d(TAG, "RootFS installed after " + attempts + "s");
                    runOnUiThread(MainActivity.this::onEnvironmentReady);
                    return;
                }
                attempts++;
            }
            Log.e(TAG, "RootFS timeout");
            runOnUiThread(() -> isAppReady = true);
        }).start();
    }

    private void onEnvironmentReady() {
        ContainerManager cm = new ContainerManager(this);

        if (cm.getContainers().isEmpty()) {
            Log.e(TAG, "Container was not created");
            isAppReady = true;
            return;
        }

        Container container = cm.getContainers().get(0);
        Log.d(TAG, "Using container: " + container.id + " - " + container.getName());
        cm.activateContainer(container);
        launchContainer(container);
    }

    private void launchContainer(Container container) {
        Log.d(TAG, "Launching container: " + container.id);

        Intent xServerIntent = new Intent(this, XServerDisplayActivity.class);
        xServerIntent.putExtra("container_id", container.id);
        
        startActivity(xServerIntent);
        
        isAppReady = true;
        finish();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setSystemLocale(newBase));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initEnvironment();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    initEnvironment();
                } else {
                    finish();
                }
                return;
            }
        }
        
        if (requestCode == OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (openFileCallback != null && data != null) {
                openFileCallback.call(data.getData());
                openFileCallback = null;
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if ((newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) && 
            currentFragment instanceof BaseFileManagerFragment) {
            ((BaseFileManagerFragment)currentFragment).onOrientationChanged();
        }
    }

    @Override
    public void onBackPressed() {
        if (currentFragment != null && currentFragment.isVisible()) {
            if (currentFragment instanceof BaseFileManagerFragment) {
                if (((BaseFileManagerFragment)currentFragment).onBackPressed()) return;
            } else if (currentFragment instanceof ContainersFragment) {
                finish();
                return;
            }
        }
        showFragment(new ContainersFragment());
    }

    public void setOpenFileCallback(Callback<Uri> openFileCallback) {
        this.openFileCallback = openFileCallback;
    }

    private boolean requestAppPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                isAppReady = true;
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
                }
                return true;
            }
            return false;
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
            return true;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        if (itemId == R.id.menu_item_add || itemId == R.id.menu_item_home ||
            itemId == R.id.menu_item_view_style || itemId == R.id.menu_item_new_folder) {
            return super.onOptionsItemSelected(menuItem);
        } else {
            if (editInputControls) {
                setResult(RESULT_OK);
                finish();
            } else {
                if (currentFragment instanceof BaseFileManagerFragment &&
                    ((BaseFileManagerFragment)currentFragment).onOptionsMenuClicked()) {
                    return true;
                }
                drawerLayout.openDrawer(GravityCompat.START);
            }
            return true;
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }

        int itemId = item.getItemId();
        if (itemId == R.id.menu_item_shortcuts) {
            preferences.edit().putBoolean("show_shortcuts_first", true).apply();
            showFragment(new ShortcutsFragment());
        } else if (itemId == R.id.menu_item_containers) {
            preferences.edit().putBoolean("show_shortcuts_first", false).apply();
            showFragment(new ContainersFragment());
        } else if (itemId == R.id.menu_item_input_controls) {
            showFragment(new InputControlsFragment(selectedProfileId));
        } else if (itemId == R.id.menu_item_settings) {
            showFragment(new SettingsFragment());
        } else if (itemId == R.id.menu_item_about) {
            new AboutDialog(this).show();
        }
        return true;
    }

    public void showFragment(Fragment fragment) {
        FragmentManager fm = getSupportFragmentManager();
        fm.beginTransaction()
            .replace(R.id.FLFragmentContainer, fragment)
            .commit();
        drawerLayout.closeDrawer(GravityCompat.START);
        currentFragment = fragment;
    }
}
