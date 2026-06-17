package com.winlator;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.Callback;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.RootFSInstaller;

import org.json.JSONObject;

import java.io.File;

public class SplashActivity extends AppCompatActivity {

    private NFSDownloader downloader;
    private ProgressBar progressBar;
    private TextView statusText;
    private Button actionButton;
    
    private boolean isWorking = false;
    private final File baseGameDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "nfsu2");

    private File getExeFile() {
        if (baseGameDir.exists() && baseGameDir.isDirectory()) {
            File[] files = baseGameDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().equalsIgnoreCase("SPEED2.EXE")) {
                        return f;
                    }
                }
            }
        }
        return new File(baseGameDir, "SPEED2.EXE");
    }

    // Лаунчер сработает корректно, если MainActivity вызовет setResult(...) и finish()
    private final ActivityResultLauncher<Intent> rootFSLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                isWorking = false;
                startInitializationFlow();
            }
    );

    private final ActivityResultLauncher<Intent> storagePermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (hasStoragePermission()) {
                    startInitializationFlow();
                } else {
                    Toast.makeText(this, "Требуется разрешение на управление файлами!", Toast.LENGTH_LONG).show();
                    finish();
                }
            }
    );

    private final ActivityResultLauncher<String> legacyPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    startInitializationFlow();
                } else {
                    Toast.makeText(this, "Требуется разрешение на хранилище!", Toast.LENGTH_LONG).show();
                    finish();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        downloader = new NFSDownloader(this);
        setupUI();
        
        if (hasStoragePermission()) {
            startInitializationFlow();
        } else {
            requestStoragePermission();
        }
    }
    
    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                storagePermissionLauncher.launch(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                storagePermissionLauncher.launch(intent);
            }
        } else {
            legacyPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    @SuppressLint("SetTextI18n")
    private void setupUI() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(64, 128, 64, 64);

        TextView title = new TextView(this);
        title.setText("Need for Speed\nUnderground 2");
        title.setTextSize(28f);
        layout.addView(title);

        statusText = new TextView(this);
        statusText.setText("Проверка среды...");
        statusText.setTextSize(18f);
        statusText.setPadding(0, 32, 0, 16);
        layout.addView(statusText);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setMax(100);
        progressBar.setVisibility(View.VISIBLE);
        layout.addView(progressBar);

        actionButton = new Button(this);
        actionButton.setVisibility(View.GONE);
        actionButton.setOnClickListener(v -> {
            if (!getExeFile().exists() && !downloader.isGameInstalled()) {
                startDownload();
            }
        });
        layout.addView(actionButton);

        setContentView(layout);
    }

    private void startInitializationFlow() {
        if (isWorking) return;
        isWorking = true;

        new Thread(() -> {
            try {
                RootFS rootFS = RootFS.find(SplashActivity.this);
                
                // Дебаг-логи для проверки состояния RootFS
                Log.d("NFS_DEBUG", "rootDir=" + rootFS.getRootDir());
                Log.d("NFS_DEBUG", "valid=" + rootFS.isValid());
                Log.d("NFS_DEBUG", "version=" + rootFS.getVersion());

                // Убрана лишняя проверка на null
                if (!rootFS.isValid() || rootFS.getVersion() < RootFSInstaller.LATEST_VERSION) {
                    runOnUiThread(() -> {
                        statusText.setText("Инициализация базовой системы...");
                        progressBar.setIndeterminate(true);
                        progressBar.setVisibility(View.VISIBLE);
                        
                        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                        intent.putExtra("SETUP_ROOTFS_AND_RETURN", true);
                        rootFSLauncher.launch(intent);
                    });
                    return; 
                }

                runOnUiThread(() -> statusText.setText("Проверка контейнера..."));

                ContainerManager containerManager = new ContainerManager(SplashActivity.this);
                Container targetContainer = null;
                for (Container c : containerManager.getContainers()) {
                    if ("NFS Underground 2 Mali".equals(c.getName())) {
                        targetContainer = c;
                        break;
                    }
                }

                if (targetContainer == null) {
                    runOnUiThread(() -> statusText.setText("Создание среды..."));
                    Log.d("NFS_DEBUG", "Creating container...");
                    targetContainer = createContainerSynchronous(containerManager);
                    if (targetContainer == null) {
                         throw new Exception("Ошибка создания контейнера");
                    }
                }

                final Container finalContainer = targetContainer;

                runOnUiThread(() -> {
                    statusText.setText("Запуск контейнера...");
                    Log.d("NFS_DEBUG", "Launching container id=" + finalContainer.id);
                    launchContainer(finalContainer);
                });

            } catch (Exception e) {
                Log.e("SplashActivity", "Initialization Error", e);
                final String msg = e.getMessage();
                runOnUiThread(() -> {
                    statusText.setText("Ошибка: " + msg);
                    progressBar.setVisibility(View.GONE);
                    isWorking = false;
                });
            }
        }).start();
    }

    private Container createContainerSynchronous(ContainerManager manager) throws Exception {
        final Object lock = new Object();
        final Container[] result = new Container[1];

        String downloadsPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        String drivesString = "D:" + downloadsPath;
        
        JSONObject data = new JSONObject();
        data.put("name", "NFS Underground 2 Mali");
        data.put("screenSize", "800x600");
        data.put("graphicsDriver", "virgl"); 
        data.put("dxwrapper", "wined3d"); 
        data.put("envVars", "MESA_GL_VERSION_OVERRIDE=4.0 MESA_GLSL_VERSION_OVERRIDE=400"); 
        data.put("drives", drivesString);

        manager.createContainerAsync(data, new Callback<Container>() {
            @Override
            public void call(Container container) {
                Log.d("NFS_DEBUG", "Container callback: " + container);
                synchronized (lock) {
                    result[0] = container;
                    lock.notify();
                }
            }
        });

        synchronized (lock) {
            if (result[0] == null) {
                // Добавлен таймаут на 30 секунд, чтобы избежать вечного зависания потока
                lock.wait(30000); 
            }
        }
        
        if (result[0] == null) {
            throw new Exception("Container creation timeout");
        }
        
        return result[0];
    }

    private void startDownload() {
        actionButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(false);
        
        downloader.downloadGame(
            progress -> {
                if (!isDestroyed() && !isFinishing()) {
                    runOnUiThread(() -> {
                        progressBar.setProgress(progress);
                        statusText.setText("Скачивание: " + progress + "%");
                    });
                }
            },
            success -> {
                if (!isDestroyed() && !isFinishing()) {
                    runOnUiThread(() -> {
                        if (success) {
                            Toast.makeText(SplashActivity.this, "Игра установлена!", Toast.LENGTH_LONG).show();
                            isWorking = false;
                            startInitializationFlow();
                        } else {
                            statusText.setText("Ошибка скачивания");
                            actionButton.setText("ПОВТОРИТЬ");
                            actionButton.setEnabled(true);
                        }
                    });
                }
            }
        );
    }

    private void launchContainer(Container container) {
        try {
            Intent serviceIntent = new Intent(this, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }

            Intent intent = new Intent(this, XServerDisplayActivity.class);
            intent.putExtra("container_id", container.id);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            startActivity(intent);
            
            // Возвращаем finish()
            finish();

        } catch (Exception e) {
            Log.e("SplashActivity", "Ошибка запуска контейнера", e);
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
            isWorking = false;
        }
    }
}
