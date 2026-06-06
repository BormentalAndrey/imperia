package com.imperia.emulator

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class GameShortcutCreator : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Простой UI для создания ярлыка
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 100, 50, 50)
        }
        
        val titleText = TextView(this).apply {
            text = "Imperia Emulator"
            textSize = 24f
        }
        layout.addView(titleText)
        
        val statusText = TextView(this).apply {
            text = "Нажмите кнопку для создания ярлыка"
            textSize = 16f
            setPadding(0, 32, 0, 32)
        }
        layout.addView(statusText)
        
        val createButton = Button(this).apply {
            text = "Создать ярлык на рабочем столе"
            setOnClickListener {
                requestCreateShortcut()
            }
        }
        layout.addView(createButton)
        
        val scanButton = Button(this).apply {
            text = "Сканировать игры"
            setOnClickListener {
                scanGamesAndCreateShortcuts()
            }
        }
        layout.addView(scanButton)
        
        setContentView(layout)
    }
    
    private fun requestCreateShortcut() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = getSystemService(ShortcutManager::class.java)
            
            if (shortcutManager?.isRequestPinShortcutSupported == true) {
                // Сканируем папку с играми и создаём ярлыки
                scanGamesAndCreateShortcuts()
            } else {
                Toast.makeText(this, "Создание ярлыков не поддерживается на этом устройстве", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Требуется Android 8.0 или выше", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun scanGamesAndCreateShortcuts() {
        val gamesDir = File(Environment.getExternalStorageDirectory(), "RetroEmulator/games")
        
        if (!gamesDir.exists()) {
            gamesDir.mkdirs()
            Toast.makeText(this, "Создана папка RetroEmulator/games. Добавьте туда игры.", Toast.LENGTH_LONG).show()
            return
        }
        
        val games = gamesDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        
        if (games.isEmpty()) {
            Toast.makeText(this, "Игры не найдены в папке RetroEmulator/games", Toast.LENGTH_LONG).show()
            return
        }
        
        val shortcutManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(ShortcutManager::class.java)
        } else null
        
        if (shortcutManager == null || !shortcutManager.isRequestPinShortcutSupported) {
            Toast.makeText(this, "Ярлыки не поддерживаются", Toast.LENGTH_LONG).show()
            return
        }
        
        games.forEach { gameDir ->
            val exeFile = gameDir.listFiles()?.find { 
                it.extension.equals("exe", true) 
            }
            if (exeFile != null) {
                createPinnedShortcut(
                    shortcutManager = shortcutManager,
                    gameId = gameDir.name,
                    gameName = gameDir.name,
                    exeName = exeFile.name,
                    exePath = exeFile.absolutePath
                )
            }
        }
        
        Toast.makeText(this, "Создано ярлыков: ${games.size}", Toast.LENGTH_SHORT).show()
    }
    
    private fun createPinnedShortcut(
        shortcutManager: ShortcutManager,
        gameId: String,
        gameName: String,
        exeName: String,
        exePath: String
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        
        // Создаём Intent для запуска игры
        val launchIntent = Intent(this, DirectLauncher::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("GAME_ID", gameId)
            putExtra("EXE_NAME", exeName)
            putExtra("EXE_PATH", exePath)
            putExtra("GAME_PATH", File(exePath).parent ?: "")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        // Иконка
        val icon = Icon.createWithResource(this, android.R.drawable.ic_media_play)
        
        // Создаём ярлык
        val shortcut = ShortcutInfo.Builder(this, gameId)
            .setShortLabel(gameName)
            .setLongLabel("Запустить $gameName")
            .setIcon(icon)
            .setIntent(launchIntent)
            .build()
        
        try {
            shortcutManager.requestPinShortcut(shortcut, null)
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка создания ярлыка для $gameName", Toast.LENGTH_SHORT).show()
        }
    }
}
