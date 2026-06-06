package com.imperia.emulator

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class NFSDownloader(private val context: Context) {
    
    companion object {
        private const val TAG = "NFSDownloader"
        private const val GAME_ID = "nfsu2"
        private const val GAME_NAME = "Need for Speed: Underground 2"
        private const val EXE_NAME = "speed2.exe"
        private const val GAME_SIZE = "2.1 GB"
        
        // ЗАМЕНИТЬ НА ВАШУ ССЫЛКУ
        private const val DOWNLOAD_URL = "https://drive.google.com/uc?export=download&id=YOUR_FILE_ID"
        
        val GAMES_DIR = File(Environment.getExternalStorageDirectory(), "RetroEmulator/games")
        val GAME_DIR = File(GAMES_DIR, GAME_ID)
        val EXE_FILE = File(GAME_DIR, EXE_NAME)
    }
    
    private var downloadId: Long = 0
    
    fun isGameInstalled(): Boolean {
        return EXE_FILE.exists()
    }
    
    fun getGameSize(): String = GAME_SIZE
    
    fun downloadGame(onProgress: (Int) -> Unit, onComplete: (Boolean) -> Unit) {
        Toast.makeText(context, "Скачивание $GAME_NAME...", Toast.LENGTH_LONG).show()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            downloadWithManager(onProgress, onComplete)
        } else {
            downloadDirect(onProgress, onComplete)
        }
    }
    
    private fun downloadWithManager(onProgress: (Int) -> Unit, onComplete: (Boolean) -> Unit) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.parse(DOWNLOAD_URL)
            
            val request = DownloadManager.Request(uri).apply {
                setTitle("Скачивание $GAME_NAME")
                setDescription("Размер: $GAME_SIZE")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "RetroEmulator/$GAME_ID.zip"
                )
            }
            
            downloadId = dm.enqueue(request)
            
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        context.unregisterReceiver(this)
                        
                        val zipFile = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "RetroEmulator/$GAME_ID.zip"
                        )
                        
                        CoroutineScope(Dispatchers.IO).launch {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Распаковка...", Toast.LENGTH_LONG).show()
                            }
                            
                            if (unzipFile(zipFile, GAME_DIR)) {
                                zipFile.delete()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "$GAME_NAME готова к запуску!", Toast.LENGTH_LONG).show()
                                    onComplete(true)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Ошибка распаковки", Toast.LENGTH_LONG).show()
                                    onComplete(false)
                                }
                            }
                        }
                    }
                }
            }
            
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            onComplete(false)
        }
    }
    
    private fun downloadDirect(onProgress: (Int) -> Unit, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(DOWNLOAD_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()
                
                val fileSize = connection.contentLength
                val zipFile = File(context.cacheDir, "$GAME_ID.zip")
                
                connection.inputStream.use { input ->
                    FileOutputStream(zipFile).use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        var bytesRead: Int
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            total += bytesRead
                            
                            if (fileSize > 0) {
                                val progress = ((total * 100) / fileSize).toInt()
                                withContext(Dispatchers.Main) {
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Распаковка...", Toast.LENGTH_LONG).show()
                }
                
                if (unzipFile(zipFile, GAME_DIR)) {
                    zipFile.delete()
                    withContext(Dispatchers.Main) {
                        onComplete(true)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onComplete(false)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Direct download error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    onComplete(false)
                }
            }
        }
    }
    
    private fun unzipFile(zipFile: File, destDir: File): Boolean {
        return try {
            destDir.mkdirs()
            
            val process = Runtime.getRuntime().exec(
                arrayOf("unzip", "-o", zipFile.absolutePath, "-d", destDir.absolutePath)
            )
            process.waitFor()
            
            process.exitValue() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Unzip error", e)
            false
        }
    }
}
