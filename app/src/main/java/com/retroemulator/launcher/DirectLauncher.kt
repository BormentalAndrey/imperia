package com.imperia.emulator

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

class DirectLauncher : Activity() {
    
    companion object {
        private const val TAG = "DirectLauncher"
        private const val GAMES_DIR = "RetroEmulator/games"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Получаем параметры запуска
        val gameId = intent.getStringExtra("GAME_ID") ?: "default"
        val exeName = intent.getStringExtra("EXE_NAME") ?: "game.exe"
        val gamePath = intent.getStringExtra("GAME_PATH") ?: ""
        
        Log.d(TAG, "Запуск игры: $gameId, exe: $exeName")
        
        // Запускаем игру сразу, без UI
        launchGame(gameId, exeName, gamePath)
    }
    
    private fun launchGame(gameId: String, exeName: String, gamePath: String) {
        try {
            // Путь к папке с играми
            val gamesDir = File(Environment.getExternalStorageDirectory(), GAMES_DIR)
            val gameDir = if (gamePath.isNotEmpty()) {
                File(gamePath)
            } else {
                File(gamesDir, gameId)
            }
            
            // Проверяем, существует ли игра
            val exeFile = File(gameDir, exeName)
            if (!exeFile.exists()) {
                // Копируем из assets при первом запуске
                copyGameFromAssets(gameId, gameDir)
            }
            
            if (!exeFile.exists()) {
                Toast.makeText(this, "Файл не найден: ${exeFile.absolutePath}", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            
            // Запускаем через Wine Activity
            val wineIntent = Intent()
            wineIntent.setClassName(
                "com.imperia.emulator",
                "com.winlator.WineActivity"
            )
            wineIntent.putExtra("executable", exeFile.absolutePath)
            wineIntent.putExtra("working_dir", gameDir.absolutePath)
            wineIntent.putExtra("fullscreen", true)
            wineIntent.putExtra("dxvk", true)
            wineIntent.putExtra("show_fps", false)
            wineIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            startActivity(wineIntent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска игры", e)
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            // Закрываем лаунчер
            finish()
        }
    }
    
    private fun copyGameFromAssets(gameId: String, gameDir: File) {
        try {
            gameDir.mkdirs()
            
            // Копируем файлы игры из assets
            val assetsPath = "games/$gameId"
            val assetManager = assets
            
            assetManager.list(assetsPath)?.forEach { filename ->
                val inputStream = assetManager.open("$assetsPath/$filename")
                val outputFile = File(gameDir, filename)
                
                FileOutputStream(outputFile).use { output ->
                    inputStream.copyTo(output)
                }
                
                // Даём права на выполнение для .exe файлов
                if (filename.endsWith(".exe") || filename.endsWith(".bat")) {
                    outputFile.setExecutable(true)
                }
            }
            
            Log.d(TAG, "Игра скопирована в ${gameDir.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка копирования игры", e)
        }
    }
}
