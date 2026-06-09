package com.winlator

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.winlator.container.Container
import com.winlator.container.ContainerManager
import com.winlator.core.Callback
import com.winlator.xenvironment.RootFS
import org.json.JSONObject
import java.io.File

@SuppressLint("SetTextI18n")
class SplashActivity : AppCompatActivity() {

    private lateinit var downloader: NFSDownloader
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var actionButton: Button
    private var containerReady = false
    private var isPreparing = false

    private val exeFile = File(Environment.getExternalStorageDirectory(), "Download/nfsu2/SPEED2.EXE")
    private val gamePathOnD = "D:\\nfsu2\\SPEED2.EXE"
    private val workingDir = "D:\\nfsu2"

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                updateUI()
                if (!containerReady && (exeFile.exists() || downloader.isGameInstalled())) {
                    prepareContainer()
                } else if (containerReady) {
                    launchGame()
                }
            } else {
                Toast.makeText(this, "Для работы требуется разрешение на доступ к файлам!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val legacyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            updateUI()
            if (!containerReady && (exeFile.exists() || downloader.isGameInstalled())) {
                prepareContainer()
            } else if (containerReady) {
                launchGame()
            }
        } else {
            Toast.makeText(this, "Для работы требуется разрешение на чтение файлов!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        downloader = NFSDownloader(this)
        setupUI()
        checkStoragePermissions()
        updateUI()
        logDebugInfo()
    }
    
    override fun onStop() {
        super.onStop()
        Log.d("SplashActivity", "onStop called - Activity stopped but not destroyed")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d("SplashActivity", "onDestroy called")
    }
    
    private fun logDebugInfo() {
        Log.d("SplashActivity", "=== NFS Underground 2 Launcher ===")
        Log.d("SplashActivity", "Путь к игре: ${exeFile.absolutePath}")
        Log.d("SplashActivity", "Игра существует: ${exeFile.exists()}")
        if (exeFile.exists()) {
            Log.d("SplashActivity", "Размер игры: ${exeFile.length()} bytes")
        }
        Log.d("SplashActivity", "Путь в Winlator: $gamePathOnD")
        Log.d("SplashActivity", "Рабочая директория: $workingDir")
    }

    private fun checkStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    storagePermissionLauncher.launch(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    storagePermissionLauncher.launch(intent)
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                legacyPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun setupUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 128, 64, 64)
        }

        layout.addView(TextView(this).apply {
            text = "Need for Speed\nUnderground 2"
            textSize = 28f
        })

        statusText = TextView(this).apply {
            text = "Проверка..."
            textSize = 18f
            setPadding(0, 32, 0, 16)
        }
        layout.addView(statusText)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            visibility = View.GONE
        }
        layout.addView(progressBar)

        actionButton = Button(this).apply {
            setOnClickListener {
                if (containerReady) {
                    launchGame()
                } else if (exeFile.exists() || downloader.isGameInstalled()) {
                    prepareContainer()
                } else {
                    startDownload()
                }
            }
        }
        layout.addView(actionButton)

        setContentView(layout)
    }

    private fun updateUI() {
        if (exeFile.exists() || downloader.isGameInstalled()) {
            if (containerReady) {
                statusText.text = "✓ Готово к запуску"
                actionButton.text = "ЗАПУСТИТЬ NFS UNDERGROUND 2"
            } else {
                statusText.text = "✓ Игра установлена"
                actionButton.text = "НАСТРОИТЬ И ЗАПУСТИТЬ"
            }
        } else {
            statusText.text = "Игра не установлена\nСкопируйте игру в:\nDownload/nfsu2/"
            actionButton.text = "СКАЧАТЬ (2.1 GB)"
        }
    }

    private fun startDownload() {
        actionButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        downloader.downloadGame(
            onProgress = { progress ->
                if (!isDestroyed && !isFinishing) {
                    runOnUiThread {
                        progressBar.isIndeterminate = false
                        progressBar.progress = progress
                        statusText.text = "Скачивание: $progress%"
                    }
                }
            },
            onComplete = { success ->
                if (!isDestroyed && !isFinishing) {
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        actionButton.isEnabled = true
                        if (success) {
                            Toast.makeText(this@SplashActivity, "Игра установлена!", Toast.LENGTH_LONG).show()
                            updateUI()
                        } else {
                            statusText.text = "Ошибка скачивания"
                            actionButton.text = "ПОВТОРИТЬ"
                        }
                    }
                }
            }
        )
    }

    private fun isRootFSInstalled(): Boolean {
        return try { RootFS.find(this) != null } catch (e: Exception) { false }
    }
    
    private fun detectGraphicsDriver(): String {
        return try {
            val cpuInfo = File("/proc/cpuinfo").readText()
            when {
                cpuInfo.contains("Qualcomm", ignoreCase = true) || 
                cpuInfo.contains("Snapdragon", ignoreCase = true) -> "turnip"
                cpuInfo.contains("MT", ignoreCase = true) || 
                cpuInfo.contains("MediaTek", ignoreCase = true) -> "virgl"
                else -> "turnip"
            }
        } catch (e: Exception) {
            "turnip"
        }
    }

    private fun prepareContainer() {
        if (isPreparing) {
            Log.d("SplashActivity", "Already preparing, skipping")
            return
        }
        isPreparing = true
        
        if (!isRootFSInstalled()) {
            Toast.makeText(this, "Первичная настройка Winlator...", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
            return
        }

        val containerManager = ContainerManager(this)
        val existingContainer = containerManager.containers.find { it.name == "NFS Underground 2" }
        
        if (existingContainer != null) {
            Log.d("SplashActivity", "Found existing container: ${existingContainer.id}")
            containerReady = true
            isPreparing = false
            updateUI()
            launchGame()
            return
        }

        actionButton.isEnabled = false
        statusText.text = "Создание контейнера..."
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true

        val data = JSONObject().apply {
            put("name", "NFS Underground 2")
            put("screenSize", "800x600")
            put("graphicsDriver", detectGraphicsDriver())
            put("dxwrapper", "wined3d")
            put("envVars", "MESA_EXTENSION_MAX_YEAR=2003 MESA_GL_VERSION_OVERRIDE=4.5")
            
            val drives = JSONObject().apply {
                put("d", JSONObject().apply {
                    put("path", Environment.getExternalStorageDirectory().absolutePath + "/Download")
                    put("type", "External")
                })
            }
            put("drives", drives)
        }

        containerManager.createContainerAsync(data, object : Callback<Container> {
            override fun call(container: Container?) {
                if (!isDestroyed && !isFinishing) {
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        actionButton.isEnabled = true
                        isPreparing = false
                        if (container != null) {
                            containerReady = true
                            Toast.makeText(this@SplashActivity, "Контейнер создан!", Toast.LENGTH_SHORT).show()
                            updateUI()
                            launchGame()
                        } else {
                            statusText.text = "Ошибка создания контейнера"
                        }
                    }
                } else {
                    isPreparing = false
                }
            }
        })
    }
    
    private fun createDesktopFile(container: Container): File? {
        return try {
            val desktopDir = File(container.rootDir, "desktop_files")
            if (!desktopDir.exists()) {
                desktopDir.mkdirs()
            }
            
            val desktopFile = File(desktopDir, "nfs_underground_2.desktop")
            
            val escapedPath = gamePathOnD.replace("\\", "\\\\")
            val escapedWorkingDir = workingDir.replace("\\", "\\\\")
            
            val desktopContent = """
                [Desktop Entry]
                Name=Need for Speed Underground 2
                Exec=wine "$escapedPath"
                Path=$escapedWorkingDir
                Type=Application
                StartupNotify=false
                Icon=default
                Categories=Game;
                X-Wine-Box64-Preset=intermediate
                X-Wine-DXVK=2.4.1
                X-Wine-Graphics-Driver=${detectGraphicsDriver()}
                X-Wine-Environment=MESA_EXTENSION_MAX_YEAR=2003 MESA_GL_VERSION_OVERRIDE=4.5
            """.trimIndent()
            
            desktopFile.writeText(desktopContent)
            Log.d("SplashActivity", "Desktop file created: ${desktopFile.absolutePath}")
            Log.d("SplashActivity", "Escaped path: $escapedPath")
            
            desktopFile
        } catch (e: Exception) {
            Log.e("SplashActivity", "Failed to create desktop file", e)
            null
        }
    }

    // ========== ИСПРАВЛЕННЫЙ МЕТОД launchGame ==========
    // ВАЖНО: НЕ ВЫЗЫВАЕМ finish(), чтобы MIUI не убил процесс
    private fun launchGame() {
        if (!containerReady) { 
            prepareContainer()
            return 
        }

        try {
            val containerManager = ContainerManager(this)
            val container = containerManager.containers.find { it.name == "NFS Underground 2" }
                ?: containerManager.containers.firstOrNull()
                
            if (container == null) {
                Toast.makeText(this, "Контейнер не найден", Toast.LENGTH_LONG).show()
                containerReady = false
                return
            }
            
            if (!exeFile.exists()) {
                Toast.makeText(this, "Файл не найден!\n${exeFile.absolutePath}\n\nСкопируйте игру в:\nDownload/nfsu2/", Toast.LENGTH_LONG).show()
                return
            }

            Log.d("SplashActivity", "=== Запуск NFS Underground 2 ===")
            Log.d("SplashActivity", "Контейнер: ${container.id}")
            Log.d("SplashActivity", "Путь в Windows: $gamePathOnD")
            
            val cleanPath = gamePathOnD.replace("\\", "/")
            Log.d("SplashActivity", "Clean path for Wine: $cleanPath")
            
            // КРИТИЧЕСКИ ВАЖНО: НЕ ВЫЗЫВАЕМ finish()!
            // Запускаем XServerDisplayActivity и оставляем SplashActivity в фоне
            val intent = Intent(this, XServerDisplayActivity::class.java).apply {
                putExtra("container_id", container.id)
                putExtra("exec_path", cleanPath)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            
            // Перемещаем SplashActivity в фон вместо завершения
            moveTaskToBack(true)
            
            Log.d("SplashActivity", "XServerDisplayActivity started, SplashActivity preserved in background")
            
        } catch (e: Exception) {
            Log.e("SplashActivity", "Ошибка запуска", e)
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("SplashActivity", "onResume called, containerReady=$containerReady, isPreparing=$isPreparing")
        
        // Проверяем разрешения и автоматически продолжаем
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                if (!containerReady && !isPreparing && (exeFile.exists() || downloader.isGameInstalled())) {
                    Log.d("SplashActivity", "Auto-starting prepareContainer from onResume")
                    prepareContainer()
                } else if (containerReady) {
                    Log.d("SplashActivity", "Auto-starting launchGame from onResume")
                    launchGame()
                } else {
                    updateUI()
                }
            } else {
                Log.d("SplashActivity", "Permission not granted yet")
                updateUI()
            }
        } else {
            if (!containerReady && !isPreparing && (exeFile.exists() || downloader.isGameInstalled())) {
                prepareContainer()
            } else if (containerReady) {
                launchGame()
            } else {
                updateUI()
            }
        }
    }
}
