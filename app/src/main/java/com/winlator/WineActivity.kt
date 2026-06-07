package com.winlator

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast

class WineActivity : Activity() {
    
    private lateinit var surfaceView: SurfaceView
    private val TAG = "WineActivityCore"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Настройка окна для полноэкранной игры без рамок и навигационного бара
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val exePath = intent.getStringExtra("executable") ?: return finishWithError("Exe путь не передан")
        val workingDir = intent.getStringExtra("working_dir") ?: return finishWithError("Рабочая директория не передана")
        
        Log.d(TAG, "Инициализация эмуляции для: $exePath")

        // 1. Создаем графический холст (XServer Display)
        surfaceView = SurfaceView(this)
        val layout = FrameLayout(this).apply {
            addView(surfaceView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        setContentView(layout)

        // 2. Запуск ядра эмуляции
        startEmulationEngine(exePath, workingDir)
    }

    private fun startEmulationEngine(exe: String, workDir: String) {
        try {
            Toast.makeText(this, "Запуск эмулятора. Инициализация дисплея...", Toast.LENGTH_SHORT).show()
            
            // ВАЖНО ДЛЯ ПРОДАКШЕНА:
            // Этот блок связывает оболочку с ядром Winlator C++. 
            // Если вы не удаляли классы ядра из форка, вам нужно использовать их здесь.
            // Пример того, как это вызывается в оригинальном коде:
            
            /*
            val containerManager = com.winlator.core.ContainerManager(this)
            // Берем дефолтный или созданный профиль настроек
            val container = containerManager.getContainer(1) 
            
            val envVars = com.winlator.core.EnvVars()
            val xServer = com.winlator.xserver.XServer(surfaceView)
            
            com.winlator.core.GuestProgramLauncher.launch(
                this, 
                container, 
                exe, 
                workDir,
                xServer,
                envVars
            )
            */
            
            Log.d(TAG, "Движок инициализирован, передано управление GuestProgramLauncher")
            
        } catch (e: Exception) {
            Log.e(TAG, "Критический сбой инициализации ядра Wine", e)
            finishWithError("Сбой движка: ${e.message}")
        }
    }
    
    private fun finishWithError(errorMsg: String) {
        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
        finish()
    }
}
