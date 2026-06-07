package com.winlator

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.winlator.container.ContainerManager
import com.winlator.container.Shortcut
import com.winlator.xenvironment.components.GuestProgramLauncherComponent

class SplashActivity : AppCompatActivity() {
    
    private lateinit var downloader: NFSDownloader
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var actionButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        downloader = NFSDownloader(this)
        setupUI()
        updateUI()
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
        
        layout.addView(TextView(this).apply {
            text = "• Экран: 800x600\n• Драйвер: Turnip\n• DX: WineD3D\n• Windows: XP\n• Box64: Stability\n• MESA: 2003 / GL 4.5"
            textSize = 12f
            setPadding(0, 8, 0, 16)
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
                if (downloader.isGameInstalled()) launchGame()
                else startDownload()
            }
        }
        layout.addView(actionButton)
        
        setContentView(layout)
    }
    
    private fun updateUI() {
        if (downloader.isGameInstalled()) {
            statusText.text = "✓ Игра установлена"
            actionButton.text = "ЗАПУСТИТЬ NFS UNDERGROUND 2"
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
    
    private fun launchGame() {
        try {
            val containerManager = ContainerManager(this)
            
            // Берём первый контейнер или создаём новый
            val container = containerManager.containers.firstOrNull() ?: run {
                // Создаём контейнер с настройками для NFS
                val newContainer = containerManager.createContainer(
                    "NFS Underground 2",
                    "C:",           // диск C
                    "C:\\Games"     // папка для игр
                )
                
                // Применяем настройки
                newContainer.resolution = "800x600"
                newContainer.graphicsDriver = "turnip"
                newContainer.dxWrapper = "wined3d"
                newContainer.winVersion = "winxp"
                newContainer.box64Preset = "stability"
                newContainer.envVars = mapOf(
                    "MESA_EXTENSION_MAX_YEAR" to "2003",
                    "MESA_GL_VERSION_OVERRIDE" to "4.5"
                )
                newContainer.save()
                
                Toast.makeText(this, "Контейнер создан!", Toast.LENGTH_SHORT).show()
                newContainer
            }
            
            val exeFile = NFSDownloader.EXE_FILE
            if (!exeFile.exists()) {
                Toast.makeText(this, "Файл не найден:\n${exeFile.absolutePath}", Toast.LENGTH_LONG).show()
                return
            }
            
            // Создаём ярлык с аргументами
            val shortcut = Shortcut(container, exeFile).apply {
                arguments = "-force-gfx-direct"
                forceFullscreen = true
            }
            
            // Запускаем игру
            GuestProgramLauncherComponent.launch(this, shortcut, container)
            finish()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
}
