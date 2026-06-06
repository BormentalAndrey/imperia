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
        val exePath = intent.getStringExtra("EXE_PATH") ?: ""
        
        Log.d(TAG, "Launching: $gameId, exe: $exeName")
        launchGame(gameId, exeName, gamePath, exePath)
    }
    
    private fun launchGame(gameId: String, exeName: String, gamePath: String, exePath: String) {
        try {
            val gamesDir = File(Environment.getExternalStorageDirectory(), GAMES_DIR)
            val gameDir = if (gamePath.isNotEmpty()) File(gamePath) else File(gamesDir, gameId)
            val exeFile = if (exePath.isNotEmpty()) File(exePath) else File(gameDir, exeName)
            
            if (!exeFile.exists()) {
                Toast.makeText(this, "Game not found:\n${exeFile.absolutePath}", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            
            val wineIntent = Intent().apply {
                setClassName(packageName, "com.winlator.WineActivity")
                putExtra("executable", exeFile.absolutePath)
                putExtra("working_dir", gameDir.absolutePath)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            startActivity(wineIntent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            finish()
        }
    }
}
