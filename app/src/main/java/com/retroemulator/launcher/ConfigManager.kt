package com.retroemulator.launcher

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.File

data class GameConfig(
    val id: String,
    val name: String,
    val exeName: String,
    val arguments: String = "",
    val useDxvk: Boolean = true,
    val fullscreen: Boolean = true,
    val resolution: String = "1280x720"
)

class ConfigManager(private val context: Context) {
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("retro_emulator", Context.MODE_PRIVATE)
    
    fun loadGameConfig(gameId: String): GameConfig {
        // Пытаемся загрузить из JSON файла игры
        val configFile = File(context.filesDir, "games/$gameId/config.json")
        
        if (configFile.exists()) {
            val json = JSONObject(configFile.readText())
            return GameConfig(
                id = json.getString("id"),
                name = json.getString("name"),
                exeName = json.getString("exe_name"),
                arguments = json.optString("arguments", ""),
                useDxvk = json.optBoolean("use_dxvk", true),
                fullscreen = json.optBoolean("fullscreen", true),
                resolution = json.optString("resolution", "1280x720")
            )
        }
        
        // Конфиг по умолчанию
        return GameConfig(
            id = gameId,
            name = gameId,
            exeName = "game.exe"
        )
    }
    
    fun saveGameConfig(config: GameConfig) {
        val configFile = File(context.filesDir, "games/${config.id}/config.json")
        configFile.parentFile?.mkdirs()
        
        val json = JSONObject().apply {
            put("id", config.id)
            put("name", config.name)
            put("exe_name", config.exeName)
            put("arguments", config.arguments)
            put("use_dxvk", config.useDxvk)
            put("fullscreen", config.fullscreen)
            put("resolution", config.resolution)
        }
        
        configFile.writeText(json.toString(2))
    }
    
    fun getInstalledGames(): List<GameConfig> {
        val gamesDir = File(context.filesDir, "games")
        if (!gamesDir.exists()) return emptyList()
        
        return gamesDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { loadGameConfig(it.name) }
            ?: emptyList()
    }
    
    fun setLastPlayedGame(gameId: String) {
        prefs.edit().putString("last_game", gameId).apply()
    }
    
    fun getLastPlayedGame(): String? {
        return prefs.getString("last_game", null)
    }
}
