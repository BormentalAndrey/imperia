package com.winlator.core;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicLong;

public abstract class TarCompressorUtils {
    private static final String TAG = "TarCompressorUtils";
    
    public enum Type {XZ, ZSTD}

    public interface OnExtractFileListener {
        File onExtractFile(File destination, long size);
    }

    private static void addFile(ArchiveOutputStream tar, File file, String entryName) {
        try {
            tar.putArchiveEntry(tar.createArchiveEntry(file, entryName));
            try (BufferedInputStream inStream = new BufferedInputStream(new FileInputStream(file), StreamUtils.BUFFER_SIZE)) {
                StreamUtils.copy(inStream, tar);
            }
            tar.closeArchiveEntry();
        }
        catch (Exception e) {}
    }

    private static void addLinkFile(ArchiveOutputStream tar, File file, String entryName) {
        try {
            TarArchiveEntry entry = new TarArchiveEntry(entryName, TarConstants.LF_SYMLINK);
            entry.setLinkName(FileUtils.readSymlink(file));
            tar.putArchiveEntry(entry);
            tar.closeArchiveEntry();
        }
        catch (Exception e) {}
    }

    private static void addDirectory(ArchiveOutputStream tar, File folder, String basePath) throws IOException {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (FileUtils.isSymlink(file)) {
                addLinkFile(tar, file, basePath+file.getName());
            }
            else if (file.isDirectory()) {
                String entryName = basePath+file.getName() + "/";
                tar.putArchiveEntry(tar.createArchiveEntry(folder, entryName));
                tar.closeArchiveEntry();
                addDirectory(tar, file, entryName);
            }
            else addFile(tar, file, basePath+file.getName());
        }
    }

    public static void compress(Type type, File file, File destination) {
        compress(type, file, destination, 3);
    }

    public static void compress(Type type, File file, File destination, int level) {
        compress(type, new File[]{file}, destination, level);
    }

    public static void compress(Type type, File[] files, File destination, int level) {
        try (OutputStream outStream = getCompressorOutputStream(type, destination, level);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(outStream)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            boolean skipFirstEntry = files.length == 1 && files[0].getName().equals(".");
            for (File file : files) {
                if (FileUtils.isSymlink(file)) {
                    addLinkFile(tar, file, file.getName());
                }
                else if (file.isDirectory()) {
                    String basePath = "";
                    if (!skipFirstEntry) {
                        basePath = file.getName() + "/";
                        tar.putArchiveEntry(tar.createArchiveEntry(file, basePath));
                        tar.closeArchiveEntry();
                    }
                    addDirectory(tar, file, basePath);
                }
                else addFile(tar, file, file.getName());
            }
            tar.finish();
        }
        catch (IOException e) {}
    }

    public static boolean extract(Type type, Context context, String assetFile, File destination) {
        return extract(type, context, assetFile, destination, null);
    }

    public static boolean extract(Type type, Context context, String assetFile, File destination, OnExtractFileListener onExtractFileListener) {
        try {
            Log.d(TAG, "Extracting from assets: " + assetFile + " to " + destination);
            return extract(type, context.getAssets().open(assetFile), destination, onExtractFileListener);
        }
        catch (IOException e) {
            Log.e(TAG, "Failed to open asset file: " + assetFile, e);
            return false;
        }
    }

    public static boolean extract(Type type, Context context, Uri source, File destination) {
        return extract(type, context, source, destination, null);
    }

    public static boolean extract(Type type, Context context, Uri source, File destination, OnExtractFileListener onExtractFileListener) {
        if (source == null) return false;
        try {
            Log.d(TAG, "Extracting from URI: " + source + " to " + destination);
            return extract(type, context.getContentResolver().openInputStream(source), destination, onExtractFileListener);
        }
        catch (FileNotFoundException e) {
            Log.e(TAG, "File not found: " + source, e);
            return false;
        }
    }

    public static boolean extract(Type type, File source, File destination) {
        return extract(type, source, destination, null);
    }

    public static boolean extract(Type type, File source, File destination, OnExtractFileListener onExtractFileListener) {
        if (source == null || !source.isFile()) {
            Log.e(TAG, "Source file is null or does not exist: " + source);
            return false;
        }
        try {
            Log.d(TAG, "Extracting from file: " + source + " to " + destination);
            return extract(type, new BufferedInputStream(new FileInputStream(source), StreamUtils.BUFFER_SIZE), destination, onExtractFileListener);
        }
        catch (FileNotFoundException e) {
            Log.e(TAG, "File not found: " + source, e);
            return false;
        }
    }

    private static boolean extract(Type type, InputStream source, File destination, OnExtractFileListener onExtractFileListener) {
        if (source == null) {
            Log.e(TAG, "Source stream is null");
            return false;
        }
        
        InputStream decompressorStream = null;
        ArchiveInputStream tar = null;
        
        try {
            decompressorStream = getCompressorInputStream(type, source);
            if (decompressorStream == null) {
                Log.e(TAG, "Failed to create compressor input stream for type: " + type);
                return false;
            }
            
            tar = new TarArchiveInputStream(decompressorStream);
            
            TarArchiveEntry entry;
            int entryCount = 0;
            
            while ((entry = (TarArchiveEntry)tar.getNextEntry()) != null) {
                if (!tar.canReadEntryData(entry)) continue;
                
                File file = new File(destination, entry.getName());
                entryCount++;
                
                if (entryCount % 100 == 0) {
                    Log.d(TAG, "Extracted " + entryCount + " files...");
                }

                if (onExtractFileListener != null) {
                    file = onExtractFileListener.onExtractFile(file, entry.getSize());
                    if (file == null) continue;
                }

                if (entry.isDirectory()) {
                    if (!file.isDirectory()) {
                        file.mkdirs();
                        Log.d(TAG, "Created directory: " + file.getAbsolutePath());
                    }
                    FileUtils.chmod(file, 0771);
                }
                else if (entry.isSymbolicLink()) {
                    File parentDir = file.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }
                    if (file.exists()) {
                        file.delete();
                    }
                    try {
                        android.system.Os.symlink(entry.getLinkName(), file.getAbsolutePath());
                        Log.d(TAG, "Created symlink: " + entry.getName() + " -> " + entry.getLinkName());
                    } catch (android.system.ErrnoException e) {
                        Log.e(TAG, "Failed to create symlink: " + entry.getName() + " -> " + entry.getLinkName(), e);
                        return false;
                    }
                }
                else {
                    File parentDir = file.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }
                    
                    try (BufferedOutputStream outStream = new BufferedOutputStream(new FileOutputStream(file), StreamUtils.BUFFER_SIZE)) {
                        if (!StreamUtils.copy(tar, outStream)) {
                            Log.e(TAG, "Failed to copy entry: " + entry.getName());
                            return false;
                        }
                    }
                    // Log.d(TAG, "Extracted file: " + entry.getName() + " (" + entry.getSize() + " bytes)");
                    FileUtils.chmod(file, 0771);
                }
            }
            
            Log.d(TAG, "Successfully extracted " + entryCount + " files to " + destination);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Extraction failed", e);
            return false;
        } finally {
            try {
                if (tar != null) tar.close();
                if (decompressorStream != null) decompressorStream.close();
                if (source != null) source.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close streams", e);
            }
        }
    }

    public static long getContentLength(Type type, Context context, String assetFile, File destination) {
        AtomicLong totalSizeRef = new AtomicLong();
        extract(type, context, assetFile, destination, (file, size) -> {
            totalSizeRef.addAndGet(size);
            return null;
        });
        return totalSizeRef.get();
    }

    public static byte[] read(Type type, File source, String localPath) {
        boolean pathIsPrefix = false;
        boolean pathIsSuffix = false;

        if (localPath.startsWith("*")) {
            pathIsSuffix = true;
        }
        else if (localPath.endsWith("*")) {
            pathIsPrefix = true;
        }

        localPath = localPath.replace("*", "");
        ByteArrayOutputStream dataOutputStream = new ByteArrayOutputStream();

        try (InputStream inStream = getCompressorInputStream(type, new BufferedInputStream(new FileInputStream(source), StreamUtils.BUFFER_SIZE));
             ArchiveInputStream tar = new TarArchiveInputStream(inStream)) {
            TarArchiveEntry entry;
            while ((entry = (TarArchiveEntry)tar.getNextEntry()) != null) {
                if (!tar.canReadEntryData(entry)) continue;
                String entryName = entry.getName();
                boolean match = pathIsSuffix ? entryName.endsWith(localPath) : (pathIsPrefix ? entryName.startsWith(localPath) : entryName.equals(localPath));

                if (match && !entry.isDirectory() && !entry.isSymbolicLink()) {
                    try (BufferedOutputStream outStream = new BufferedOutputStream(dataOutputStream, StreamUtils.BUFFER_SIZE)) {
                        if (!StreamUtils.copy(tar, outStream)) return null;
                    }
                    return dataOutputStream.toByteArray();
                }
            }
            return null;
        }
        catch (IOException e) {
            Log.e(TAG, "Failed to read from archive", e);
            return null;
        }
    }

    private static InputStream getCompressorInputStream(Type type, InputStream source) throws IOException {
        if (type == Type.XZ) {
            Log.d(TAG, "Creating XZ compressor input stream");
            return new XZCompressorInputStream(source);
        }
        else if (type == Type.ZSTD) {
            Log.d(TAG, "Creating ZSTD compressor input stream");
            return new ZstdCompressorInputStream(source);
        }
        Log.e(TAG, "Unknown compressor type: " + type);
        return null;
    }

    private static OutputStream getCompressorOutputStream(Type type, File destination, int level) throws IOException {
        if (type == Type.XZ) {
            return new XZCompressorOutputStream(new BufferedOutputStream(new FileOutputStream(destination), StreamUtils.BUFFER_SIZE), level);
        }
        else if (type == Type.ZSTD) {
            return new ZstdCompressorOutputStream(new BufferedOutputStream(new FileOutputStream(destination), StreamUtils.BUFFER_SIZE), level);
        }
        return null;
    }
}
