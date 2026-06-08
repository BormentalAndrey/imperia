package com.winlator

import android.content.Intent
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
import com.winlator.container.Container
import com.winlator.container.ContainerManager
import com.winlator.core.Callback
import com.winlator.xenvironment.RootFS
import org.json.JSONObject
import java.io.File

class SplashActivity : AppCompatActivity() {

    private lateinit var downloader: NFSDownloader
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var actionButton: Button
    private var containerReady = false

    // ИСПРАВЛЕНО: путь с большой буквой D (Download)
    private val exeFile = File(Environment.getExternalStorageDirectory(), "Download/nfsu2/SPEED2.EXE")
    
    // Путь в стиле Windows для диска D: (ИСПРАВЛЕНО)
    private val gamePathOnD = "D:\\nfsu2\\SPEED2.EXE"
    
    // Рабочая директория для игры (где лежат папки Global, Cars и т.д.)
    private val workingDir = "D:\\nfsu2"

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                updateUI()
            } else {
                Toast.makeText(this, "Для работы требуется разрешение на доступ к файлам!", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        downloader = NFSDownloader(this)
        setupUI()
        updateUI()
        checkStoragePermissions()
        
        // Логируем информацию для отладки
        logDebugInfo()
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
                runOnUiThread {
                    progressBar.isIndeterminate = false
                    progressBar.progress = progress
                    statusText.text = "Скачивание: $progress%"
                }
            },
            onComplete = { success ->
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    actionButton.isEnabled = true
                    if (success) {
                        Toast.makeText(this, "Игра установлена!", Toast.LENGTH_LONG).show()
                        updateUI()
                    } else {
                        statusText.text = "Ошибка скачивания"
                        actionButton.text = "ПОВТОРИТЬ"
                    }
                }
            }
        )
    }

    private fun isRootFSInstalled(): Boolean {
        return try { RootFS.find(this) != null } catch (e: Exception) { false }
    }
    
    // Автоопределение графического драйвера
    private fun detectGraphicsDriver(): String {
        return try {
            val cpuInfo = File("/proc/cpuinfo").readText()
            when {
                cpuInfo.contains("Qualcomm") || cpuInfo.contains("Snapdragon") -> "turnip"
                cpuInfo.contains("MT") || cpuInfo.contains("MediaTek") -> "virgl"
                else -> "turnip"
            }
        } catch (e: Exception) {
            "turnip"
        }
    }

    private fun prepareContainer() {
        if (!isRootFSInstalled()) {
            Toast.makeText(this, "Первичная настройка Winlator...", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
            return
        }

        val containerManager = ContainerManager(this)
        
        // Проверяем, существует ли уже наш контейнер
        val existingContainer = containerManager.containers.find { it.name == "NFS Underground 2" }
        
        if (existingContainer != null) {
            Log.d("SplashActivity", "Найден существующий контейнер: ${existingContainer.id}")
            containerReady = true
            updateUI()
            launchGame()
            return
        }

        if (containerManager.containers.isEmpty()) {
            actionButton.isEnabled = false
            statusText.text = "Создание контейнера..."
            progressBar.visibility = View.VISIBLE
            progressBar.isIndeterminate = true

            val data = JSONObject().apply {
                put("name", "NFS Underground 2")
                put("screenSize", "800x600")
                put("graphicsDriver", detectGraphicsDriver()) // Автоопределение
                put("dxwrapper", "wined3d")
                put("envVars", "MESA_EXTENSION_MAX_YEAR=2003 MESA_GL_VERSION_OVERRIDE=4.5")
                
                // КЛЮЧЕВОЕ: Настройка диска D:
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
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        actionButton.isEnabled = true
                        if (container != null) {
                            containerReady = true
                            Toast.makeText(this@SplashActivity, "Контейнер создан! Диск D: привязан к Download", Toast.LENGTH_SHORT).show()
                            updateUI()
                            launchGame()
                        } else {
                            statusText.text = "Ошибка создания контейнера"
                        }
                    }
                }
            })
        } else {
            containerReady = true
            updateUI()
            launchGame()
        }
    }
    
    // Создание .desktop файла с правильной рабочей директорией
    private fun createDesktopFile(container: Container): File? {
        try {
            val desktopDir = File(container.rootDir, "desktop_files")
            if (!desktopDir.exists()) {
                desktopDir.mkdirs()
            }
            
            val desktopFile = File(desktopDir, "nfs_underground_2.desktop")
            
            val desktopContent = """
                [Desktop Entry]
                Name=Need for Speed Underground 2
                Exec=wine "$gamePathOnD"
                Path=$workingDir
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
            Log.d("SplashActivity", "Desktop файл создан: ${desktopFile.absolutePath}")
            Log.d("SplashActivity", "Содержимое:\n$desktopContent")
            
            return desktopFile
        } catch (e: Exception) {
            Log.e("SplashActivity", "Ошибка создания desktop файла", e)
            return null
        }
    }

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
            Log.d("SplashActivity", "Рабочая директория: $workingDir")
            
            // Пытаемся создать .desktop файл для правильного запуска
            val desktopFile = createDesktopFile(container)
            
            val intent = Intent(this, XServerDisplayActivity::class.java).apply {
                putExtra("container_id", container.id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                
                if (desktopFile != null && desktopFile.exists()) {
                    // Используем .desktop файл (рекомендуемый способ)
                    putExtra("shortcut_path", desktopFile.absolutePath)
                    Log.d("SplashActivity", "Запуск через shortcut_path")
                } else {
                    // Fallback: прямой запуск через exec_path с диском D:
                    putExtra("exec_path", gamePathOnD)
                    Log.d("SplashActivity", "Запуск через exec_path")
                }
            }
            
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e("SplashActivity", "Ошибка запуска", e)
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            saveDebugLog(e.message ?: "Неизвестная ошибка")
        }
    }
    
    // Сохранение лога ошибки на телефон
    private fun saveDebugLog(errorMessage: String) {
        try {
            val logFile = File(Environment.getExternalStorageDirectory(), "Download/nfsu2/launcher_debug.log")
            val logText = """
                === NFS Underground 2 Launcher Debug Log ===
                Время: ${System.currentTimeMillis()}
                Ошибка: $errorMessage
                
                Система:
                Android: ${Build.VERSION.RELEASE}
                Устройство: ${Build.MANUFACTURER} ${Build.MODEL}
                
                Файлы:
                Игра существует: ${exeFile.exists()}
                Путь: ${exeFile.absolutePath}
                Размер: ${if (exeFile.exists()) exeFile.length() else 0}
                
                Контейнер готов: $containerReady
            """.trimIndent()
            
            logFile.writeText(logText)
            Log.d("SplashActivity", "Лог сохранен: ${logFile.absolutePath}")
            Toast.makeText(this, "Лог сохранен в Download/nfsu2/launcher_debug.log", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("SplashActivity", "Не удалось сохранить лог", e)
        }
    }

    override fun onResume() {
        super.onResume()
        if (containerReady) {
            updateUI()
        }
    }
}
