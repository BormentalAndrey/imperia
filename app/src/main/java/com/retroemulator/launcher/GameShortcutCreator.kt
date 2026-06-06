package com.retroemulator.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
            text = "Создание ярлыка игры"
            textSize = 20f
        }
        layout.addView(titleText)
        
        val createButton = Button(this).apply {
            text = "Создать ярлык на рабочем столе"
            setOnClickListener {
                requestCreateShortcut()
            }
        }
        layout.addView(createButton)
        
        setContentView(layout)
    }
    
    private fun requestCreateShortcut() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = getSystemService(ShortcutManager::class.java)
            
            if (shortcutManager?.isRequestPinShortcutSupported == true) {
                createPinnedShortcut(shortcutManager)
            } else {
                Toast.makeText(this, "Создание ярлыков не поддерживается", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun createPinnedShortcut(shortcutManager: ShortcutManager) {
        val gameId = intent.getStringExtra("GAME_ID") ?: "game"
        val gameName = intent.getStringExtra("GAME_NAME") ?: "Игра"
        val exeName = intent.getStringExtra("EXE_NAME") ?: "game.exe"
        val iconPath = intent.getStringExtra("ICON_PATH") ?: ""
        
        // Создаём Intent для запуска игры
        val launchIntent = Intent(this, DirectLauncher::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("GAME_ID", gameId)
            putExtra("EXE_NAME", exeName)
            putExtra("GAME_PATH", File(filesDir, "games/$gameId").absolutePath)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        // Загружаем иконку
        val icon = if (iconPath.isNotEmpty() && File(iconPath).exists()) {
            Icon.createWithBitmap(BitmapFactory.decodeFile(iconPath))
        } else {
            Icon.createWithResource(this, android.R.drawable.ic_media_play)
        }
        
        // Создаём ярлык
        val shortcut = ShortcutInfo.Builder(this, gameId)
            .setShortLabel(gameName)
            .setLongLabel(gameName)
            .setIcon(icon)
            .setIntent(launchIntent)
            .build()
        
        shortcutManager.requestPinShortcut(shortcut, null)
    }
}
