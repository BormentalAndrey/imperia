package com.winlator

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.winlator.container.Container
import com.winlator.container.ContainerManager
import com.winlator.container.Shortcut
import com.winlator.core.Callback
import org.json.JSONArray
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
        
        layout.addView(TextView(this).apply {
            text = "• Экран: 800x600\n• Драйвер: Turnip\n• DX: WineD3D\n• Box64: Stability\n• MESA: 2003 / GL 4.5"
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
    
    private fun setupControlsProfile() {
        try {
            val profilesDir = File(filesDir, "input_profiles")
            if (!profilesDir.exists()) profilesDir.mkdirs()
            
            val profileFile = File(profilesDir, "NFS_U2.icp")
            
            if (!profileFile.exists()) {
                val json = JSONObject()
                json.put("id", 999)
                json.put("name", "NFS U2")
                val elements = JSONArray()
                
                fun addButton(id: Int, key: Int, name: String, x: Double, y: Double) {
                    val btn = JSONObject()
                    btn.put("id", id)
                    btn.put("type", "Button")
                    btn.put("x", x)
                    btn.put("y", y)
                    btn.put("scale", 1.0)
                    btn.put("binding", key)
                    btn.put("name", name)
                    elements.put(btn)
                }

                addButton(1, KeyEvent.KEYCODE_W, "ГАЗ", 0.85, 0.65)
                addButton(2, KeyEvent.KEYCODE_S, "ТОРМОЗ", 0.03, 0.65)
                addButton(3, KeyEvent.KEYCODE_SPACE, "РУЧНИК", 0.85, 0.50)
                addButton(4, KeyEvent.KEYCODE_ALT_LEFT, "НИТРО", 0.03, 0.50)
                addButton(5, KeyEvent.KEYCODE_CTRL_LEFT, "СКОР-", 0.85, 0.80)
                addButton(6, KeyEvent.KEYCODE_SHIFT_LEFT, "СКОР+", 0.42, 0.02)
                
                for (i in 0..3) {
                    addButton(7 + i, KeyEvent.KEYCODE_F1 + i, "F${i + 1}", 0.02, 0.02 + i * 0.08)
                }
                
                addButton(11, KeyEvent.KEYCODE_Y, "Y", 0.91, 0.02)
                addButton(12, KeyEvent.KEYCODE_U, "U", 0.91, 0.10)
                addButton(13, KeyEvent.KEYCODE_P, "P", 0.91, 0.18)
                addButton(14, KeyEvent.KEYCODE_HOME, "HOME", 0.91, 0.26)
                
                json.put("elements", elements)
                profileFile.writeText(json.toString())
            }
            
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            prefs.edit().putString("controls_profile", "NFS_U2").apply()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun launchGame() {
        val containerManager = ContainerManager(this)
        
        val existingContainer = containerManager.containers.firstOrNull { 
            it.name == "NFS Underground 2" 
        }
        
        if (existingContainer != null) {
            launchWithContainer(existingContainer)
            return
        }
        
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
                        launchWithContainer(container)
                    } else {
                        statusText.text = "Ошибка создания контейнера"
                        Toast.makeText(this@SplashActivity, "Не удалось создать контейнер", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
    
    private fun launchWithContainer(container: Container) {
        val exeFile = NFSDownloader.EXE_FILE
        if (!exeFile.exists()) {
            Toast.makeText(this, "Файл не найден:\n${exeFile.absolutePath}", Toast.LENGTH_LONG).show()
            return
        }
        
        setupControlsProfile()
        
        val shortcut = Shortcut(container, exeFile)
        
        val intent = Intent(this, XServerDisplayActivity::class.java)
        intent.putExtra("container_id", container.id)
        intent.putExtra("shortcut_path", shortcut.file.absolutePath)
        
        startActivity(intent)
        finish()
    }
}
