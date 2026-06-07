package com.imperia.emulator

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import com.github.luben.zstd.ZstdInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream

class DirectLauncher : Activity() {
    
    companion object {
        private const val TAG = "DirectLauncher"
        private const val GAMES_DIR = "RetroEmulator/games"
    }
    
    // Продакшен-решение: собственный Scope для корутин, защищающий от утечек памяти
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Временный экран загрузки, пока распаковываются библиотеки эмулятора
        val statusText = TextView(this).apply {
            text = "Инициализация движка Winlator...\nПожалуйста, подождите."
            textSize = 18f
            setPadding(32, 64, 32, 32)
        }
        setContentView(statusText)
        
        val gameId = intent.getStringExtra("GAME_ID") ?: "nfsu2"
        val exeName = intent.getStringExtra("EXE_NAME") ?: "speed2.exe"
        val gamePath = intent.getStringExtra("GAME_PATH") ?: ""
        val exePath = intent.getStringExtra("EXE_PATH") ?: ""
        
        Log.d(TAG, "Launching: $gameId, exe: $exeName")
        
        // Запускаем асинхронную проверку и распаковку в безопасном скоупе
        activityScope.launch {
            try {
                statusText.text = "Проверка библиотек Box64 и Turnip..."
                prepareEmulatorEnvironment()
                
                statusText.text = "Запуск игры..."
                launchGame(gameId, exeName, gamePath, exePath)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка инициализации окружения", e)
                Toast.makeText(this@DirectLauncher, "Критическая ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Очищаем корутины при уничтожении Activity
        activityScope.cancel()
    }
    
    // Распаковка .tzst файлов (Box64, Turnip, DXVK) из папки assets/components/ 
    // в рабочую директорию приложения (imagefs), которую понимает Wine.
    private suspend fun prepareEmulatorEnvironment() = withContext(Dispatchers.IO) {
        val imageFsDir = File(filesDir, "imagefs")
        if (!imageFsDir.exists()) {
            imageFsDir.mkdirs()
        }

        // Список компонентов для распаковки (должны лежать в assets/components/)
        val components = listOf("box64-0.3.7.tzst", "turnip-24.1.0.tzst", "dxvk-1.7.2.tzst")
        
        for (component in components) {
            val componentMarker = File(imageFsDir, ".installed_$component")
            if (componentMarker.exists()) continue // Уже распаковано
            
            Log.d(TAG, "Extracting component: $component")
            try {
                assets.open("components/$component").use { inputStream ->
                    BufferedInputStream(inputStream).use { bis ->
                        ZstdInputStream(bis).use { zstd ->
                            TarArchiveInputStream(zstd).use { tar ->
                                var entry: TarArchiveEntry?
                                while (tar.nextTarEntry.also { entry = it } != null) {
                                    val outputFile = File(imageFsDir, entry!!.name)
                                    if (entry!!.isDirectory) {
                                        outputFile.mkdirs()
                                    } else {
                                        outputFile.parentFile?.mkdirs()
                                        FileOutputStream(outputFile).use { fos ->
                                            tar.copyTo(fos)
                                        }
                                        // Восстановление прав на выполнение для Linux бинарников
                                        if (entry!!.mode and 0b001_000_000 > 0) { 
                                            outputFile.setExecutable(true, false)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                componentMarker.createNewFile()
            } catch (e: Exception) {
                Log.e(TAG, "Missing or corrupt asset: $component", e)
                // Не прерываем процесс, возможно файл был удален сознательно
            }
        }
    }
    
    private fun launchGame(gameId: String, exeName: String, gamePath: String, exePath: String) {
        try {
            val gamesDir = File(Environment.getExternalStorageDirectory(), GAMES_DIR)
            val gameDir = if (gamePath.isNotEmpty()) File(gamePath) else File(gamesDir, gameId)
            val exeFile = if (exePath.isNotEmpty()) File(exePath) else File(gameDir, exeName)
            
            if (!exeFile.exists()) {
                Toast.makeText(this, "Файл не найден:\n${exeFile.absolutePath}", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            
            // Запуск целевого WineActivity из Winlator
            val wineIntent = Intent().apply {
                setClassName(packageName, "com.winlator.WineActivity")
                putExtra("executable", exeFile.absolutePath)
                putExtra("working_dir", gameDir.absolutePath)
                
                // Передаем флаги для конфигурации (эти данные будет читать ядро Winlator)
                putExtra("resolution", "800x600")
                putExtra("dx_wrapper", "dxvk")
                
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            startActivity(wineIntent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error launching WineActivity", e)
            Toast.makeText(this, "Ошибка запуска: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            finish()
        }
    }
}
