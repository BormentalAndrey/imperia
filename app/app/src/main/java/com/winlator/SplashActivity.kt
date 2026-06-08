package com.winlator

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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
    
    private val exeFile = File(Environment.getExternalStorageDirectory(), "download/nfsu2/SPEED2.EXE")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        downloader = NFSDownloader(this)
        setupUI()
        updateUI()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
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
        
        progressBar = ProgressBar(this).apply {
            isIndeterminate = false
            max = 100
            visibility = ProgressBar.GONE
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
            statusText.text = "Игра не установлена"
            actionButton.text = "СКАЧАТЬ (2.1 GB)"
        }
    }
    
    private fun startDownload() {
        actionButton.isEnabled = false
        progressBar.visibility = ProgressBar.VISIBLE
        downloader.downloadGame(
            onProgress = { progress ->
                runOnUiThread {
                    progressBar.progress = progress
                    statusText.text = "Скачивание: $progress%"
                }
            },
            onComplete = { success ->
                runOnUiThread {
                    progressBar.visibility = ProgressBar.GONE
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
    
    private fun prepareContainer() {
        if (!isRootFSInstalled()) {
            Toast.makeText(this, "Первичная настройка...", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
            return
        }
        
        val containerManager = ContainerManager(this)
        
        if (containerManager.containers.isEmpty()) {
            actionButton.isEnabled = false
            statusText.text = "Создание контейнера..."
            progressBar.visibility = ProgressBar.VISIBLE
            progressBar.isIndeterminate = true
            
            val data = JSONObject().apply {
                put("name", "NFS Underground 2")
                put("screenSize", "800x600")
                put("graphicsDriver", "turnip")
                put("dxwrapper", "wined3d")
                put("envVars", "MESA_EXTENSION_MAX_YEAR=2003 MESA_GL_VERSION_OVERRIDE=4.5")
            }
            
            containerManager.createContainerAsync(data, object : Callback<Container> {
                override fun call(container: Container) {
                    runOnUiThread {
                        progressBar.visibility = ProgressBar.GONE
                        actionButton.isEnabled = true
                        if (container != null) {
                            containerReady = true
                            Toast.makeText(this@SplashActivity, "Контейнер создан!", Toast.LENGTH_SHORT).show()
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
    
    private fun launchGame() {
        if (!containerReady) { prepareContainer(); return }
        
        try {
            val container = ContainerManager(this).containers.firstOrNull()
            if (container == null) {
                Toast.makeText(this, "Контейнер не найден", Toast.LENGTH_LONG).show()
                containerReady = false
                return
            }
            if (!exeFile.exists()) {
                Toast.makeText(this, "Файл не найден\n${exeFile.absolutePath}", Toast.LENGTH_LONG).show()
                return
            }
            
            // Передаём DOS-путь Z:\sdcard\download\nfsu2\SPEED2.EXE
            // Z: = корень /, всегда доступен в Wine
            val dosPath = "Z:\\sdcard\\download\\nfsu2\\SPEED2.EXE"
            
            Log.d("SplashActivity", "Launching: $dosPath, container=${container.id}")
            
            startActivity(Intent(this, XServerDisplayActivity::class.java).apply {
                putExtra("container_id", container.id)
                putExtra("exec_path", dosPath)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            finish()
        } catch (e: Exception) {
            Log.e("SplashActivity", "Launch error", e)
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
