package com.winlator

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.winlator.container.ContainerManager
import com.winlator.container.Shortcut
import com.winlator.inputcontrols.*
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
    
    private fun setupControlsProfile() {
        val controlsManager = InputControlsManager(this)
        
        // Создаём профиль если его нет
        if (controlsManager.getProfile("NFS U2") == null) {
            val profile = ControlsProfile("NFS U2")
            val elements = mutableListOf<ControlElement>()
            
            // Гироскоп — поворот (невидимый, весь экран)
            elements.add(ControlElement().apply {
                type = ControlElement.TYPE_GYRO
                action = ControlElement.ACTION_MAP_TO_STICK
                targetStick = GamepadState.STICK_LEFT
                x = 0.0f; y = 0.0f; width = 1.0f; height = 1.0f
                sensitivity = 1.0f; alpha = 0.0f
            })
            
            // Газ W — справа
            elements.add(ControlElement().apply {
                type = ControlElement.TYPE_BUTTON
                keyCode = KeyEvent.KEYCODE_W; label = "ГАЗ"
                x = 0.85f; y = 0.65f; width = 0.12f; height = 0.12f
                color = 0xFF00AA00.toInt()
            })
            
            // Тормоз S — слева
            elements.add(ControlElement().apply {
                type = ControlElement.TYPE_BUTTON
                keyCode = KeyEvent.KEYCODE_S; label = "ТОРМОЗ"
                x = 0.03f; y = 0.65f; width = 0.12f; height = 0.12f
                color = 0xFFFF0000.toInt()
            })
            
            // Ручной тормоз Space — справа
            elements.add(ControlElement().apply {
                type = ControlElement.TYPE_BUTTON
                keyCode = KeyEvent.KEYCODE_SPACE; label = "РУЧНИК"
                x = 0.85f; y = 0.50f; width = 0.10f; height = 0.10f
                color = 0xFFFF6600.toInt()
            })
            
            // Нитро Alt — слева
            elements.add(ControlElement().apply {
                type = ControlElement.TYPE_BUTTON
                keyCode = KeyEvent.KEYCODE_ALT_LEFT; label = "НИТРО"
                x = 0.03f; y = 0.50f; width = 0.10f; height = 0.10f
                color = 0xFFFFDD00.toInt()
            })
            
            // Понижение скорости Ctrl — справа
            elements.add(ControlElement().apply {
                type = ControlElement.TYPE_BUTTON
                keyCode = KeyEvent.KEYCODE_CTRL_LEFT; label = "СКОР-"
                x = 0.85f; y = 0.80f; width = 0.10f; height = 0.10f
                color = 0xFFFF0000.toInt()
            })
            
            // Повышение скорости Shift — сверху
            elements.add(ControlElement().apply {
                type = ControlElement.TYPE_BUTTON
                keyCode = KeyEvent.KEYCODE_SHIFT_LEFT; label = "СКОР+"
                x = 0.42f; y = 0.02f; width = 0.16f; height = 0.08f
                color = 0xFF00CC00.toInt()
            })
            
            // F1-F4 слева сверху
            for (i in 0..3) {
                elements.add(ControlElement().apply {
                    type = ControlElement.TYPE_BUTTON
                    keyCode = KeyEvent.KEYCODE_F1 + i; label = "F${i + 1}"
                    x = 0.02f; y = 0.02f + i * 0.08f; width = 0.07f; height = 0.07f
                    color = 0xFF4488FF.toInt()
                })
            }
            
            // Y, U, P, Home справа сверху
            val rightKeys = listOf(
                KeyEvent.KEYCODE_Y to "Y",
                KeyEvent.KEYCODE_U to "U",
                KeyEvent.KEYCODE_P to "P",
                KeyEvent.KEYCODE_HOME to "HOME"
            )
            rightKeys.forEachIndexed { i, (code, label) ->
                elements.add(ControlElement().apply {
                    type = ControlElement.TYPE_BUTTON
                    keyCode = code; this.label = label
                    x = 0.91f; y = 0.02f + i * 0.08f; width = 0.07f; height = 0.07f
                    color = 0xFF4488FF.toInt()
                })
            }
            
            profile.elements = elements
            controlsManager.saveProfile(profile)
        }
        
        controlsManager.currentProfileName = "NFS U2"
    }
    
    private fun launchGame() {
        try {
            val containerManager = ContainerManager(this)
            
            val container = containerManager.containers.firstOrNull() ?: run {
                val newContainer = containerManager.createContainer(
                    "NFS Underground 2",
                    "C:",
                    "C:\\Games"
                )
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
            
            // Настраиваем управление
            setupControlsProfile()
            
            val shortcut = Shortcut(container, exeFile).apply {
                arguments = "-force-gfx-direct"
                forceFullscreen = true
            }
            
            GuestProgramLauncherComponent.launch(this, shortcut, container)
            finish()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
}
