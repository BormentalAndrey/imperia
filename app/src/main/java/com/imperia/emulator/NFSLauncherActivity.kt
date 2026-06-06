package com.imperia.emulator

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class NFSLauncherActivity : AppCompatActivity() {
    
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
        
        // Заголовок
        layout.addView(TextView(this).apply {
            text = "Need for Speed\nUnderground 2"
            textSize = 28f
        })
        
        // Размер
        layout.addView(TextView(this).apply {
            text = "Размер: 2.1 GB"
            textSize = 16f
            setPadding(0, 16, 0, 16)
        })
        
        // Статус
        statusText = TextView(this).apply {
            text = "Проверка..."
            textSize = 18f
            setPadding(0, 32, 0, 16)
        }
        layout.addView(statusText)
        
        // Прогресс-бар
        progressBar = ProgressBar(this).apply {
            isIndeterminate = false
            max = 100
            visibility = ProgressBar.GONE
        }
        layout.addView(progressBar)
        
        // Кнопка
        actionButton = Button(this).apply {
            text = "Загрузить игру"
            setOnClickListener {
                if (downloader.isGameInstalled()) {
                    launchGame()
                } else {
                    startDownload()
                }
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
        statusText.text = "Скачивание..."
        
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
        if (!downloader.isGameInstalled()) {
            Toast.makeText(this, "Игра не найдена", Toast.LENGTH_SHORT).show()
            return
        }
        
        val intent = Intent(this, DirectLauncher::class.java).apply {
            putExtra("GAME_ID", "nfsu2")
            putExtra("EXE_NAME", "speed2.exe")
            putExtra("EXE_PATH", NFSDownloader.EXE_FILE.absolutePath)
            putExtra("GAME_PATH", NFSDownloader.GAME_DIR.absolutePath)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }
}
