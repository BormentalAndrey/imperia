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

import org.json.JSONObject;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "MainActivity";
    public static final boolean DEBUG_MODE = false;
    public static final @IntRange(from = 1, to = 19) byte CONTAINER_PATTERN_COMPRESSION_LEVEL = 9;
    public static final byte PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 1;
    public static final byte OPEN_FILE_REQUEST_CODE = 2;
    public static final byte EDIT_INPUT_CONTROLS_REQUEST_CODE = 3;
    public static final byte OPEN_DIRECTORY_REQUEST_CODE = 4;
    private static final int ROOTFS_TIMEOUT_SECONDS = 120;
    private static final String CONTAINER_NAME = "NFS Underground 2 Mali";
    
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
            initEnvironment(intent);
        }
    }

    private void initEnvironment(Intent intent) {
        if (requestAppPermissions()) {
            return;
        }

        RootFS rootFS = RootFS.find(this);
        if (rootFS != null && rootFS.isValid() && rootFS.getVersion() >= RootFSInstaller.LATEST_VERSION) {
            Log.d(TAG, "RootFS ready, proceeding to container setup");
            onEnvironmentReady(intent);
            return;
        }

        Log.d(TAG, "RootFS needs installation, starting...");
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
                    Log.d(TAG, "RootFS installation completed after " + attempts + " seconds");
                    runOnUiThread(() -> onEnvironmentReady(intent));
                    return;
                }
                attempts++;
            }
            Log.e(TAG, "RootFS installation timeout");
            runOnUiThread(() -> {
                isAppReady = true;
            });
        }).start();
    }

    private void onEnvironmentReady(Intent intent) {
        int containerId = intent.getIntExtra("container_id", 0);
        String startPath = intent.getStringExtra("start_path");
        
        if (containerId > 0) {
            launchContainer(containerId, startPath);
            return;
        }

        ContainerManager containerManager = new ContainerManager(this);
        ArrayList<Container> containers = containerManager.getContainers();
        Container targetContainer = null;

        for (Container c : containers) {
            if (CONTAINER_NAME.equals(c.getName())) {
                targetContainer = c;
                break;
            }
        }

        if (targetContainer != null) {
            launchContainer(targetContainer.id, startPath);
        } else {
            createAndLaunchContainerAsync(containerManager, startPath);
        }
    }

    private void createAndLaunchContainerAsync(ContainerManager containerManager, String startPath) {
        try {
            Log.d(TAG, "Creating " + CONTAINER_NAME + " container asynchronously...");
            
            JSONObject data = new JSONObject();
            data.put("name", CONTAINER_NAME);
            data.put("screenSize", "800x600");
            data.put("graphicsDriver", "virgl");
            data.put("dxwrapper", "wined3d");
            data.put("audioDriver", "alsa");
            data.put("envVars", 
                "MESA_GL_VERSION_OVERRIDE=4.0 " +
                "MESA_GLSL_VERSION_OVERRIDE=400 " +
                "WINEESYNC=1"
            );
            data.put("box64Preset", "performance");
            data.put("startupSelection", 1);
            
            containerManager.createContainerAsync(data, container -> {
                runOnUiThread(() -> {
                    if (container != null) {
                        Log.d(TAG, "Container created successfully. ID: " + container.id);
                        launchContainer(container.id, startPath);
                    } else {
                        Log.e(TAG, "Failed to create container object");
                        isAppReady = true;
                    }
                });
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to build container configuration JSON", e);
            isAppReady = true;
        }
    }

    private void launchContainer(int containerId, String startPath) {
        ContainerManager cm = new ContainerManager(this);
        Container container = cm.getContainerById(containerId);

        if (container == null) {
            Log.e(TAG, "Container not found for ID: " + containerId);
            isAppReady = true;
            return;
        }

        // Единоразовая активация контейнера
        cm.activateContainer(container);

        Intent xServerIntent = new Intent(this, XServerDisplayActivity.class);
        xServerIntent.putExtra("container_id", containerId);
        
        // Если startPath не передан — используем дефолтный путь к игре
        String gameExePath = (startPath != null && !startPath.isEmpty()) ? startPath : "D:\\nfsu2\\SPEED2.EXE";
        xServerIntent.putExtra("start_path", gameExePath);
        
        Log.d(TAG, "Starting XServerDisplayActivity with container " + containerId + " and path: " + gameExePath);
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
                initEnvironment(getIntent());
            }
            else finish();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    initEnvironment(getIntent());
                } else {
                    finish();
                }
                return;
            }
        }
        
        if (requestCode == MainActivity.OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (openFileCallback != null) {
                openFileCallback.call(data.getData());
                openFileCallback = null;
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if ((newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) && currentFragment instanceof BaseFileManagerFragment) {
            ((BaseFileManagerFragment)currentFragment).onOrientationChanged();
        }
    }

    @Override
    public void onBackPressed() {
        if (currentFragment != null && currentFragment.isVisible()) {
            if (currentFragment instanceof BaseFileManagerFragment) {
                BaseFileManagerFragment fileManagerFragment = (BaseFileManagerFragment)currentFragment;
                if (fileManagerFragment.onBackPressed()) return;
            }
            else if (currentFragment instanceof ContainersFragment) {
                finish();
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
                    intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                    startActivityForResult(intent, PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
                } catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
                }
                return true;
            }
            return false;
        } 
        else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                return false;
            }

            String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
            return true;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        if (itemId == R.id.menu_item_add ||
            itemId == R.id.menu_item_home ||
            itemId == R.id.menu_item_view_style ||
            itemId == R.id.menu_item_new_folder) {
            return super.onOptionsItemSelected(menuItem);
        }
        else {
            if (editInputControls) {
                setResult(RESULT_OK);
                finish();
            }
            else {
                if (currentFragment instanceof BaseFileManagerFragment) {
                    BaseFileManagerFragment fileManagerFragment = (BaseFileManagerFragment)currentFragment;
                    if (fileManagerFragment.onOptionsMenuClicked()) return true;
                }
                drawerLayout.openDrawer(GravityCompat.START);
            }
            return true;
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }

        switch (item.getItemId()) {
            case R.id.menu_item_shortcuts:
                preferences.edit().putBoolean("show_shortcuts_first", true).apply();
                showFragment(new ShortcutsFragment());
                break;
            case R.id.menu_item_containers:
                preferences.edit().putBoolean("show_shortcuts_first", false).apply();
                showFragment(new ContainersFragment());
                break;
            case R.id.menu_item_input_controls:
                showFragment(new InputControlsFragment(selectedProfileId));
                break;
            case R.id.menu_item_settings:
                showFragment(new SettingsFragment());
                break;
            case R.id.menu_item_about:
                (new AboutDialog(this)).show();
                break;
        }
        return true;
    }

    public void showFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
            .replace(R.id.FLFragmentContainer, fragment)
            .commit();

        drawerLayout.closeDrawer(GravityCompat.START);
        currentFragment = fragment;
    }
}
