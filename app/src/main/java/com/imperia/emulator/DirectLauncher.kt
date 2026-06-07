package com.imperia.emulator

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.github.luben.zstd.ZstdInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val gameId = intent.getStringExtra("GAME_ID") ?: "nfsu2"
        val exeName = intent.getStringExtra("EXE_NAME") ?: "speed2.exe"
        val gamePath = intent.getStringExtra("GAME_PATH") ?: ""
        val exePath = intent.getStringExtra("EXE_PATH") ?: ""
        
        Log.d(TAG, "Launching: $gameId, exe: $exeName")
        
        // Уведомляем пользователя (используем applicationContext, так как Activity закроется)
        Toast.makeText(applicationContext, "Инициализация движка Winlator...", Toast.LENGTH_LONG).show()
        
        // Запускаем распаковку и запуск игры в независимом фоновом потоке (IO)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                prepareEmulatorEnvironment()
                launchGame(gameId, exeName, gamePath, exePath)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка инициализации", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Критическая ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        // ВАЖНО: Закрываем Activity СРАЗУ ЖЕ.
        // Это предотвращает краш "did not call finish() prior to onResume() completing",
        // который возникает из-за использования Theme.NoDisplay в манифесте.
        finish()
    }
    
    // Распаковка .tzst файлов (Box64, Turnip, DXVK) из папки assets/components/ 
    // Используем suspend, чтобы не блокировать Main поток.
    private suspend fun prepareEmulatorEnvironment() {
        // Берем глобальный контекст, так как Activity уже завершена
        val context = applicationContext
        val imageFsDir = File(context.filesDir, "imagefs")
        if (!imageFsDir.exists()) {
            imageFsDir.mkdirs()
        }

        val components = listOf("box64-0.3.7.tzst", "turnip-24.1.0.tzst", "dxvk-1.7.2.tzst")
        
        for (component in components) {
            val componentMarker = File(imageFsDir, ".installed_$component")
            if (componentMarker.exists()) continue // Уже распаковано
            
            Log.d(TAG, "Extracting component: $component")
            try {
                context.assets.open("components/$component").use { inputStream ->
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
            }
        }
    }
    
    private suspend fun launchGame(gameId: String, exeName: String, gamePath: String, exePath: String) {
        val gamesDir = File(Environment.getExternalStorageDirectory(), GAMES_DIR)
        val gameDir = if (gamePath.isNotEmpty()) File(gamePath) else File(gamesDir, gameId)
        val exeFile = if (exePath.isNotEmpty()) File(exePath) else File(gameDir, exeName)
        
        if (!exeFile.exists()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, "Файл не найден:\n${exeFile.absolutePath}", Toast.LENGTH_LONG).show()
            }
            return
        }
        
        // Запуск целевого WineActivity из Winlator
        val wineIntent = Intent().apply {
            setClassName(applicationContext.packageName, "com.winlator.WineActivity")
            putExtra("executable", exeFile.absolutePath)
            putExtra("working_dir", gameDir.absolutePath)
            
            // Передаем флаги конфигурации
            putExtra("resolution", "800x600")
            putExtra("dx_wrapper", "dxvk")
            
            // Обязательный флаг при запуске из applicationContext
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        // Возвращаемся в главный поток для запуска UI-компонента (WineActivity)
        withContext(Dispatchers.Main) {
            applicationContext.startActivity(wineIntent)
        }
    }
}
