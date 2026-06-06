package com.imperia.emulator

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import java.io.File

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
        val containerConfig = intent.getStringExtra("CONTAINER_CONFIG") ?: "nfsu2_container.wcp"
        
        Log.d(TAG, "Запуск игры: $gameId, exe: $exeName, config: $containerConfig")
        
        launchGame(gameId, exeName, gamePath, containerConfig)
    }
    
    private fun launchGame(gameId: String, exeName: String, gamePath: String, containerConfig: String) {
        try {
            val gamesDir = File(Environment.getExternalStorageDirectory(), GAMES_DIR)
            val gameDir = if (gamePath.isNotEmpty()) File(gamePath) else File(gamesDir, gameId)
            val exeFile = File(gameDir, exeName)
            
            if (!exeFile.exists()) {
                Toast.makeText(this, "Файл не найден: ${exeFile.absolutePath}", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            
            val wineIntent = Intent().apply {
                setClassName("com.imperia.emulator", "com.winlator.WineActivity")
                putExtra("executable", exeFile.absolutePath)
                putExtra("working_dir", gameDir.absolutePath)
                putExtra("container_config", containerConfig)
                putExtra("fullscreen", true)
                putExtra("dx_wrapper", "WineD3D")
                putExtra("box_preset", "Stability")
                putExtra("windows_version", "winxp")
                putExtra("env_vars", "MESA_EXTENSION_MAX_YEAR=2003,MESA_GL_VERSION_OVERRIDE=4.5")
                putExtra("force_fullscreen", true)
                putExtra("arguments", "-force-gfx-direct")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            startActivity(wineIntent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска игры", e)
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            finish()
        }
    }
}
