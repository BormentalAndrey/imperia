package com.winlator

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
        const val EXE_NAME = "SPEED2.EXE"
        private const val GAME_SIZE = "2.1 GB"
        
        private const val DOWNLOAD_URL = "https://drive.google.com/uc?export=download&id=YOUR_FILE_ID"
        
        val GAMES_DIR = File(Environment.getExternalStorageDirectory(), "RetroEmulator/games")
        val GAME_DIR = File(GAMES_DIR, GAME_ID)
        val EXE_FILE = File(GAME_DIR, EXE_NAME)
    }
    
    fun isGameInstalled(): Boolean = EXE_FILE.exists()
    
    fun downloadGame(onProgress: (Int) -> Unit, onComplete: (Boolean) -> Unit) {
        Toast.makeText(context, "Скачивание $GAME_NAME...", Toast.LENGTH_LONG).show()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            downloadWithManager(onComplete)
        } else {
            downloadDirect(onProgress, onComplete)
        }
    }
    
    private fun downloadWithManager(onComplete: (Boolean) -> Unit) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(DOWNLOAD_URL)).apply {
                setTitle("Скачивание $GAME_NAME")
                setDescription("Размер: $GAME_SIZE")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "RetroEmulator/$GAME_ID.zip")
            }
            
            val downloadId = dm.enqueue(request)
            
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        ctx.unregisterReceiver(this)
                        val zipFile = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "RetroEmulator/$GAME_ID.zip"
                        )
                        CoroutineScope(Dispatchers.IO).launch {
                            val success = unzipFile(zipFile, GAME_DIR)
                            zipFile.delete()
                            withContext(Dispatchers.Main) {
                                onComplete(success)
                            }
                        }
                    }
                }
            }
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            onComplete(false)
        }
    }
    
    private fun downloadDirect(onProgress: (Int) -> Unit, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connection = URL(DOWNLOAD_URL).openConnection() as HttpURLConnection
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
                                withContext(Dispatchers.Main) { onProgress(((total * 100) / fileSize).toInt()) }
                            }
                        }
                    }
                }
                
                val success = unzipFile(zipFile, GAME_DIR)
                zipFile.delete()
                withContext(Dispatchers.Main) { onComplete(success) }
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }
    
    private fun unzipFile(zipFile: File, destDir: File): Boolean {
        return try {
            destDir.mkdirs()
            val process = Runtime.getRuntime().exec(arrayOf("unzip", "-o", zipFile.absolutePath, "-d", destDir.absolutePath))
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Unzip error", e)
            false
        }
    }
}
