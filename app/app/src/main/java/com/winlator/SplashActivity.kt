package com.winlator

import android.content.Intent
import android.os.Bundle
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
    
    private fun isRootFSInstalled(): Boolean {
        return try {
            RootFS.find(this) != null
        } catch (e: Exception) {
            false
        }
    }
    
    private fun launchGame() {
        if (!isRootFSInstalled()) {
            Toast.makeText(this, "Первичная настройка...", Toast.LENGTH_LONG).show()
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
            return
        }
        
        try {
            val containerManager = ContainerManager(this)
            
            if (containerManager.containers.isEmpty()) {
                val data = JSONObject()
                data.put("name", "NFS Underground 2")
                data.put("screenSize", "800x600")
                data.put("graphicsDriver", "turnip")
                data.put("dxwrapper", "wined3d")
                data.put("envVars", "MESA_EXTENSION_MAX_YEAR=2003 MESA_GL_VERSION_OVERRIDE=4.5")
                
                actionButton.isEnabled = false
                statusText.text = "Создание контейнера..."
                
                containerManager.createContainerAsync(data, object : Callback<Container> {
                    override fun call(container: Container) {
                        runOnUiThread {
                            actionButton.isEnabled = true
                            if (container != null) {
                                Toast.makeText(this@SplashActivity, "Контейнер создан!", Toast.LENGTH_SHORT).show()
                                launchWithExec(container)
                            } else {
                                statusText.text = "Ошибка создания контейнера"
                            }
                        }
                    }
                })
            } else {
                launchWithExec(containerManager.containers[0])
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun launchWithExec(container: Container) {
        try {
            val exeFile = NFSDownloader.EXE_FILE
            if (!exeFile.exists()) {
                Toast.makeText(this, "Файл не найден:\n${exeFile.absolutePath}", Toast.LENGTH_LONG).show()
                return
            }
            
            val gameDirInContainer = File(container.rootDir, ".wine/drive_c/Games/NFS_U2")
            val exeInContainer = File(gameDirInContainer, "SPEED2.EXE")
            
            if (!exeInContainer.exists()) {
                statusText.text = "Копирование игры в контейнер..."
                gameDirInContainer.mkdirs()
                NFSDownloader.GAME_DIR.copyRecursively(gameDirInContainer, true)
                statusText.text = "✓ Игра установлена"
            }
            
            val intent = Intent(this, XServerDisplayActivity::class.java)
            intent.putExtra("container_id", container.id)
            intent.putExtra("exec_path", "C:\\Games\\NFS_U2\\SPEED2.EXE")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            startActivity(intent)
            finish()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
