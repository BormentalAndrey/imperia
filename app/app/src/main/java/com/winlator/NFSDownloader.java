package com.winlator;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class NFSDownloader {
    private final Context context;
    // ВСТАВЬ СЮДА ПРЯМУЮ ССЫЛКУ НА ZIP-АРХИВ С ИГРОЙ
    private final String gameUrl = "https://example.com/nfsu2.zip";
    private final File baseGameDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "nfsu2");

    public interface ProgressCallback { void onProgress(int progress); }
    public interface CompleteCallback { void onComplete(boolean success); }

    public NFSDownloader(Context context) {
        this.context = context;
    }

    public boolean isGameInstalled() {
        return new File(baseGameDir, "SPEED2.EXE").exists() || new File(baseGameDir, "speed2.exe").exists();
    }

    public void downloadGame(ProgressCallback progressCallback, CompleteCallback completeCallback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                if (!baseGameDir.exists()) baseGameDir.mkdirs();
                File zipFile = new File(baseGameDir.getParentFile(), "nfsu2_temp.zip");
                mainHandler.post(() -> progressCallback.onProgress(0));

                URL url = new URL(gameUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    mainHandler.post(() -> completeCallback.onComplete(false));
                    return;
                }

                int fileLength = connection.getContentLength();
                BufferedInputStream input = new BufferedInputStream(url.openStream());
                FileOutputStream output = new FileOutputStream(zipFile);

                byte[] data = new byte[8192];
                long total = 0;
                int count;

                while ((count = input.read(data)) != -1) {
                    total += count;
                    output.write(data, 0, count);
                    if (fileLength > 0) {
                        int progress = (int) (total * 50 / fileLength); // 50% на загрузку
                        mainHandler.post(() -> progressCallback.onProgress(progress));
                    }
                }
                output.flush();
                output.close();
                input.close();

                unzip(zipFile, extractProgress -> {
                    // Остальные 50% на распаковку
                    mainHandler.post(() -> progressCallback.onProgress(50 + (extractProgress / 2)));
                });

                zipFile.delete();
                mainHandler.post(() -> completeCallback.onComplete(true));

            } catch (Exception e) {
                Log.e("NFSDownloader", "Ошибка установки", e);
                mainHandler.post(() -> completeCallback.onComplete(false));
            }
        }).start();
    }

    private void unzip(File zipFile, ProgressCallback progressCallback) throws Exception {
        long zipLength = zipFile.length();
        long extractedSize = 0;

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new java.io.FileInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(baseGameDir, entry.getName());
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    if (newFile.getParentFile() != null) newFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                            extractedSize += len;
                            if (zipLength > 0) {
                                int progress = (int) (extractedSize * 100 / zipLength);
                                progressCallback.onProgress(Math.min(progress, 100));
                            }
                        }
                    }
                }
            }
        }
    }
}
