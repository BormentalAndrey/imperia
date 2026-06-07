package com.winlator

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
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
    
    private val exeOnDriveD = File(Environment.getExternalStorageDirectory(), "download/nfsu2/SPEED2.EXE")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        downloader = NFSDownloader(this)
        setupUI()
        updateUI()
        
        // Запрос разрешения для Android 11+
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
                    launchWithExec()
                } else if (exeOnDriveD.exists() || downloader.isGameInstalled()) {
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
        if (exeOnDriveD.exists() || downloader.isGameInstalled()) {
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
        return try {
            RootFS.find(this) != null
        } catch (e: Exception) {
            false
        }
    }
    
    private fun prepareContainer() {
        if (!isRootFSInstalled()) {
            Toast.makeText(this, "Первичная настройка...", Toast.LENGTH_LONG).show()
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
            return
        }
        
        val containerManager = ContainerManager(this)
        
        if (containerManager.containers.isEmpty()) {
            actionButton.isEnabled = false
            statusText.text = "Создание контейнера..."
            progressBar.visibility = ProgressBar.VISIBLE
            progressBar.isIndeterminate = true
            
            val data = JSONObject()
            data.put("name", "NFS Underground 2")
            data.put("screenSize", "800x600")
            data.put("graphicsDriver", "turnip")
            data.put("dxwrapper", "wined3d")
            data.put("envVars", "MESA_EXTENSION_MAX_YEAR=2003 MESA_GL_VERSION_OVERRIDE=4.5")
            
            containerManager.createContainerAsync(data, object : Callback<Container> {
                override fun call(container: Container) {
                    runOnUiThread {
                        progressBar.visibility = ProgressBar.GONE
                        actionButton.isEnabled = true
                        
                        if (container != null) {
                            containerReady = true
                            Toast.makeText(this@SplashActivity, "Контейнер создан!", Toast.LENGTH_SHORT).show()
                            updateUI()
                            // Автозапуск после создания
                            launchWithExec()
                        } else {
                            statusText.text = "Ошибка создания контейнера"
                        }
                    }
                }
            })
        } else {
            containerReady = true
            updateUI()
            launchWithExec()
        }
    }
    
    private fun launchWithExec() {
        if (!containerReady) {
            prepareContainer()
            return
        }
        
        try {
            val containerManager = ContainerManager(this)
            val container = containerManager.containers.firstOrNull()
            
            if (container == null) {
                Toast.makeText(this, "Контейнер не найден", Toast.LENGTH_LONG).show()
                containerReady = false
                return
            }
            
            if (!exeOnDriveD.exists()) {
                val oldExe = NFSDownloader.EXE_FILE
                if (oldExe.exists()) {
                    statusText.text = "Копирование на диск D..."
                    exeOnDriveD.parentFile?.mkdirs()
                    oldExe.copyTo(exeOnDriveD, true)
                } else {
                    Toast.makeText(this, "Файл не найден\nПоложите игру в:\n/sdcard/download/nfsu2/", Toast.LENGTH_LONG).show()
                    return
                }
            }
            
            statusText.text = "Запуск игры..."
            
            val intent = Intent(this, XServerDisplayActivity::class.java)
            intent.putExtra("container_id", container.id)
            intent.putExtra("exec_path", "D:\\nfsu2\\SPEED2.EXE")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            startActivity(intent)
            finish()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
