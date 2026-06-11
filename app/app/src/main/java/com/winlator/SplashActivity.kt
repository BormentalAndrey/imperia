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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.winlator.container.Container
import com.winlator.container.ContainerManager
import com.winlator.core.Callback
import com.winlator.xenvironment.RootFS
import com.winlator.xenvironment.RootFSInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

@SuppressLint("SetTextI18n")
class SplashActivity : AppCompatActivity() {

    private lateinit var downloader: NFSDownloader
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var actionButton: Button
    
    private var isWorking = false

    private val exeFile = File(Environment.getExternalStorageDirectory(), "Download/nfsu2/SPEED2.EXE")
    private val gamePathOnD = "D:\\nfsu2\\SPEED2.EXE"

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasStoragePermission()) {
            startInitializationFlow()
        } else {
            Toast.makeText(this, "Требуется разрешение на управление файлами!", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private val legacyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startInitializationFlow()
        } else {
            Toast.makeText(this, "Требуется разрешение на хранилище!", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        downloader = NFSDownloader(this)
        setupUI()
        
        if (hasStoragePermission()) {
            startInitializationFlow()
        } else {
            requestStoragePermission()
        }
    }
    
    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                storagePermissionLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                storagePermissionLauncher.launch(intent)
            }
        } else {
            legacyPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
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
            text = "Проверка среды..."
            textSize = 18f
            setPadding(0, 32, 0, 16)
        }
        layout.addView(statusText)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            max = 100
            visibility = View.VISIBLE
        }
        layout.addView(progressBar)

        actionButton = Button(this).apply {
            visibility = View.GONE
            setOnClickListener {
                if (!exeFile.exists() && !downloader.isGameInstalled()) {
                    startDownload()
                }
            }
        }
        layout.addView(actionButton)

        setContentView(layout)
    }

    // Главный метод инициализации: не блокирует UI и выполняется последовательно
    private fun startInitializationFlow() {
        if (isWorking) return
        isWorking = true

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Установка/Проверка RootFS (теперь работает без MainActivity)
                val rootFS = RootFS.find(this@SplashActivity)
                if (!rootFS.isValid || rootFS.version < RootFSInstaller.LATEST_VERSION) {
                    withContext(Dispatchers.Main) {
                        statusText.text = "Распаковка базовой системы (занимает время)..."
                        progressBar.isIndeterminate = true
                        progressBar.visibility = View.VISIBLE
                    }
                    
                    val success = RootFSInstaller.installSynchronous(this@SplashActivity)
                    if (!success) {
                        throw Exception("Не удалось распаковать rootfs.tzst")
                    }
                }

                // 2. Создание контейнера
                withContext(Dispatchers.Main) {
                    statusText.text = "Проверка контейнера..."
                }

                val containerManager = ContainerManager(this@SplashActivity)
                var container = containerManager.containers.find { it.name == "NFS Underground 2" }

                if (container == null) {
                    withContext(Dispatchers.Main) {
                        statusText.text = "Создание среды..."
                    }
                    container = createContainerSynchronous(containerManager)
                    if (container == null) {
                         throw Exception("Ошибка создания контейнера")
                    }
                }

                // 3. Проверка игры
                if (exeFile.exists() || downloader.isGameInstalled()) {
                    withContext(Dispatchers.Main) {
                        statusText.text = "Запуск игры..."
                        launchGame(container)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        statusText.text = "Игра не найдена. Скачайте файлы."
                        progressBar.visibility = View.GONE
                        actionButton.text = "СКАЧАТЬ ИГРУ"
                        actionButton.visibility = View.VISIBLE
                        isWorking = false
                    }
                }

            } catch (e: Exception) {
                Log.e("SplashActivity", "Initialization Error", e)
                withContext(Dispatchers.Main) {
                    statusText.text = "Ошибка: ${e.message}"
                    progressBar.visibility = View.GONE
                    isWorking = false
                }
            }
        }
    }

    private suspend fun createContainerSynchronous(manager: ContainerManager): Container? = suspendCoroutine { cont ->
        val downloadsPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        val drivesString = "D:$downloadsPath"
        
        val data = JSONObject().apply {
            put("name", "NFS Underground 2")
            put("screenSize", "800x600")
            put("graphicsDriver", detectGraphicsDriver())
            put("dxwrapper", "wined3d")
            put("envVars", "MESA_EXTENSION_MAX_YEAR=2003 MESA_GL_VERSION_OVERRIDE=4.5")
            put("drives", drivesString)
        }

        manager.createContainerAsync(data, object : Callback<Container> {
            override fun call(container: Container?) {
                cont.resume(container)
            }
        })
    }

    private fun detectGraphicsDriver(): String {
        return try {
            val hardware = Build.HARDWARE.lowercase()
            val board = Build.BOARD.lowercase()
            val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.lowercase() else ""
            when {
                hardware.contains("qcom") || board.contains("msm") || soc.contains("snapdragon") -> "turnip"
                hardware.contains("mt") || board.contains("mt") || soc.contains("mediatek") -> "virgl"
                else -> "turnip"
            }
        } catch (e: Exception) {
            "turnip"
        }
    }

    private fun startDownload() {
        actionButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = false
        
        downloader.downloadGame(
            onProgress = { progress ->
                if (!isDestroyed && !isFinishing) {
                    runOnUiThread {
                        progressBar.progress = progress
                        statusText.text = "Скачивание: $progress%"
                    }
                }
            },
            onComplete = { success ->
                if (!isDestroyed && !isFinishing) {
                    runOnUiThread {
                        if (success) {
                            Toast.makeText(this@SplashActivity, "Игра установлена!", Toast.LENGTH_LONG).show()
                            isWorking = false
                            startInitializationFlow() // Рестарт процесса после скачивания
                        } else {
                            statusText.text = "Ошибка скачивания"
                            actionButton.text = "ПОВТОРИТЬ"
                            actionButton.isEnabled = true
                        }
                    }
                }
            }
        )
    }

    private fun launchGame(container: Container) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(Intent(this, KeepAliveService::class.java))
            } else {
                startService(Intent(this, KeepAliveService::class.java))
            }

            val cleanPath = gamePathOnD.replace("\\", "/")
            
            val intent = Intent(this, XServerDisplayActivity::class.java).apply {
                putExtra("container_id", container.id)
                putExtra("exec_path", cleanPath)
            }
            startActivity(intent)
            finish() // Закрываем Splash, чтобы не висел в памяти и не ловил onResume

        } catch (e: Exception) {
            Log.e("SplashActivity", "Ошибка запуска", e)
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            isWorking = false
        }
    }
}
