package com.winlator

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class NFSDownloader(private val context: Context) {

    // ВСТАВЬ СЮДА ПРЯМУЮ ССЫЛКУ НА ZIP-АРХИВ С ИГРОЙ
    private val gameUrl = "https://example.com/nfsu2.zip" 
    
    private val baseGameDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "nfsu2")

    fun isGameInstalled(): Boolean {
        return File(baseGameDir, "SPEED2.EXE").exists() || File(baseGameDir, "speed2.exe").exists()
    }

    fun downloadGame(onProgress: (Int) -> Unit, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!baseGameDir.exists()) {
                    baseGameDir.mkdirs()
                }

                val zipFile = File(baseGameDir.parentFile, "nfsu2_temp.zip")
                
                withContext(Dispatchers.Main) { onProgress(0) }

                val url = URL(gameUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    withContext(Dispatchers.Main) { onComplete(false) }
                    return@launch
                }

                val fileLength = connection.contentLength
                val input = BufferedInputStream(url.openStream())
                val output = FileOutputStream(zipFile)

                val data = ByteArray(8192)
                var total: Long = 0
                var count: Int

                while (input.read(data).also { count = it } != -1) {
                    total += count
                    output.write(data, 0, count)
                    if (fileLength > 0) {
                        val progress = (total * 50 / fileLength).toInt() // 50% прогресса на скачивание
                        withContext(Dispatchers.Main) { onProgress(progress) }
                    }
                }

                output.flush()
                output.close()
                input.close()

                // Распаковка архива
                unzip(zipFile) { extractProgress ->
                    // Вторые 50% прогресса на распаковку
                    withContext(Dispatchers.Main) { onProgress(50 + (extractProgress / 2)) }
                }

                zipFile.delete() // Удаляем временный архив после распаковки
                withContext(Dispatchers.Main) { onComplete(true) }

            } catch (e: Exception) {
                Log.e("NFSDownloader", "Ошибка установки", e)
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    private fun unzip(zipFile: File, onProgress: (Int) -> Unit) {
        val zipLength = zipFile.length()
        var extractedSize: Long = 0
        
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(baseGameDir, entry.name)
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        val buffer = ByteArray(8192)
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                            extractedSize += len
                            if (zipLength > 0) {
                                val progress = (extractedSize * 100 / zipLength).toInt()
                                onProgress(progress.coerceAtMost(100))
                            }
                        }
                    }
                }
                entry = zis.nextEntry
            }
        }
    }
}
