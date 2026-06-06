package com.retroemulator.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class DirectLauncher : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Получаем путь к EXE
        val exePath = intent.getStringExtra("EXE_PATH") ?: "default.exe"
        
        // Запускаем напрямую
        Toast.makeText(this, "Запуск: $exePath", Toast.LENGTH_SHORT).show()
        
        // Здесь будет интеграция с Wine
        launchGame(exePath)
    }
    
    private fun launchGame(exePath: String) {
        // Интеграция с нативным кодом Winlator
        val intent = Intent(this, Class.forName("com.winlator.WineActivity"))
        intent.putExtra("executable", exePath)
        startActivity(intent)
        finish()
    }
}
