package com.winlator.container;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.winlator.R;
import com.winlator.core.Callback;
import com.winlator.core.FileUtils;
import com.winlator.core.TarCompressorUtils;
import com.winlator.core.WineInfo;
import com.winlator.xenvironment.RootFS;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.Executors;

public class ContainerManager {
    private static final String TAG = "ContainerManager";
    
    private final ArrayList<Container> containers = new ArrayList<>();
    private int maxContainerId = 0;
    private final File homeDir;
    private final Context context;

    public ContainerManager(Context context) {
        this.context = context;
        File rootDir = RootFS.find(context).getRootDir();
        homeDir = new File(rootDir, "home");
        loadContainers();
    }

    public Context getContext() {
        return context;
    }

    public ArrayList<Container> getContainers() {
        return containers;
    }

    private void loadContainers() {
        containers.clear();
        maxContainerId = 0;

        try {
            File[] files = homeDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        if (file.getName().startsWith(RootFS.USER+"-")) {
                            Container container = new Container(Integer.parseInt(file.getName().replace(RootFS.USER+"-", "")));
                            container.setRootDir(new File(homeDir, RootFS.USER+"-"+container.id));
                            JSONObject data = new JSONObject(FileUtils.readString(container.getConfigFile()));
                            container.loadData(data);
                            containers.add(container);
                            maxContainerId = Math.max(maxContainerId, container.id);
                        }
                    }
                }
            }
        }
        catch (JSONException e) {
            Log.e(TAG, "Failed to load containers", e);
        }
    }

    public void activateContainer(Container container) {
        container.setRootDir(new File(homeDir, RootFS.USER+"-"+container.id));
        File file = new File(homeDir, RootFS.USER);
        file.delete();
        FileUtils.symlink(RootFS.USER+"-"+container.id, file.getPath());
        Log.d(TAG, "Activated container: " + container.id);
    }

    public void createContainerAsync(final JSONObject data, Callback<Container> callback) {
        final Handler handler = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            final Container container = createContainer(data);
            handler.post(() -> callback.call(container));
        });
    }

    public void duplicateContainerAsync(Container container, Runnable callback) {
        final Handler handler = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            duplicateContainer(container);
            handler.post(callback);
        });
    }

    public void removeContainerAsync(Container container, Runnable callback) {
        final Handler handler = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            removeContainer(container);
            handler.post(callback);
        });
    }

    private Container createContainer(JSONObject data) {
        try {
            int id = maxContainerId + 1;
            data.put("id", id);
            
            Log.d(TAG, "Creating container with ID: " + id);

            File containerDir = new File(homeDir, RootFS.USER+"-"+id);
            if (!containerDir.mkdirs()) {
                Log.e(TAG, "Failed to create container directory: " + containerDir);
                return null;
            }

            Container container = new Container(id);
            container.setRootDir(containerDir);
            container.loadData(data);

            boolean isMainWineVersion = !data.has("wineVersion") || WineInfo.isMainWineVersion(data.getString("wineVersion"));
            if (!isMainWineVersion) container.setWineVersion(data.getString("wineVersion"));

            Log.d(TAG, "Extracting container pattern file...");
            if (!extractContainerPatternFile(container.getWineVersion(), containerDir)) {
                Log.e(TAG, "Failed to extract container pattern file");
                FileUtils.delete(containerDir);
                return null;
            }
            
            Log.d(TAG, "Container pattern extracted successfully");
            
            // Создаем символические ссылки для дисков
            setupDrivesSymlinks(container);

            container.saveData();
            maxContainerId++;
            containers.add(container);
            Log.d(TAG, "Container created successfully with ID: " + id);
            return container;
        }
        catch (JSONException e) {
            Log.e(TAG, "JSON error while creating container", e);
            return null;
        }
    }
    
    private void setupDrivesSymlinks(Container container) {
        try {
            File dosdevicesDir = new File(container.getRootDir(), ".wine/dosdevices");
            if (!dosdevicesDir.exists()) {
                dosdevicesDir.mkdirs();
            }
            
            // Создаем символические ссылки для всех дисков
            for (Drive drive : container.drivesIterator()) {
                String driveLetter = drive.letter.toLowerCase();
                File driveLink = new File(dosdevicesDir, driveLetter + ":");
                File driveTarget = new File(drive.path);
                
                if (!driveLink.exists()) {
                    if (driveTarget.exists() || driveTarget.mkdirs()) {
                        FileUtils.symlink(driveTarget.getAbsolutePath(), driveLink.getAbsolutePath());
                        Log.d(TAG, "Created drive " + driveLetter + ": symlink to " + drive.path);
                    } else {
                        Log.w(TAG, "Failed to create drive " + driveLetter + ": target path does not exist");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to setup drives symlinks", e);
        }
    }

    private void duplicateContainer(Container srcContainer) {
        int id = maxContainerId + 1;

        File dstDir = new File(homeDir, RootFS.USER+"-"+id);
        if (!dstDir.mkdirs()) return;

        if (!FileUtils.copy(srcContainer.getRootDir(), dstDir, (file) -> FileUtils.chmod(file, 0771))) {
            FileUtils.delete(dstDir);
            return;
        }

        Container dstContainer = new Container(id);
        dstContainer.setRootDir(dstDir);
        dstContainer.setName(srcContainer.getName()+" ("+context.getString(R.string.copy)+")");
        dstContainer.setScreenSize(srcContainer.getScreenSize());
        dstContainer.setEnvVars(srcContainer.getEnvVars());
        dstContainer.setCPUList(srcContainer.getCPUList());
        dstContainer.setCPUListWoW64(srcContainer.getCPUListWoW64());
        dstContainer.setGraphicsDriver(srcContainer.getGraphicsDriver());
        dstContainer.setGraphicsDriverConfig(srcContainer.getGraphicsDriverConfig());
        dstContainer.setDXWrapper(srcContainer.getDXWrapper());
        dstContainer.setDXWrapperConfig(srcContainer.getDXWrapperConfig());
        dstContainer.setAudioDriver(srcContainer.getAudioDriver());
        dstContainer.setAudioDriverConfig(srcContainer.getAudioDriverConfig());
        dstContainer.setWinComponents(srcContainer.getWinComponents());
        dstContainer.setDrives(srcContainer.getDrives());
        dstContainer.setHUDMode(srcContainer.getHUDMode());
        dstContainer.setStartupSelection(srcContainer.getStartupSelection());
        dstContainer.setBox64Preset(srcContainer.getBox64Preset());
        dstContainer.setDesktopTheme(srcContainer.getDesktopTheme());
        dstContainer.saveData();

        maxContainerId++;
        containers.add(dstContainer);
    }

    private void removeContainer(Container container) {
        if (FileUtils.delete(container.getRootDir())) containers.remove(container);
    }

    public ArrayList<Shortcut> loadShortcuts(Shortcut selectedFolder) {
        ArrayList<Shortcut> shortcuts = new ArrayList<>();

        if (selectedFolder != null) {
            File[] files = selectedFolder.file.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".desktop") || file.isDirectory()) {
                        shortcuts.add(new Shortcut(selectedFolder.container, file));
                    }
                }
            }
        }
        else {
            for (Container container : containers) {
                File desktopDir = new File(container.getUserDir(), "Desktop");
                File[] files = desktopDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().endsWith(".desktop") || file.isDirectory()) {
                            shortcuts.add(new Shortcut(container, file));
                        }
                    }
                }
            }
        }

        shortcuts.sort((a, b) -> {
            int value = Boolean.compare(b.file.isDirectory(), a.file.isDirectory());
            if (value == 0) value = a.name.compareTo(b.name);
            return value;
        });
        return shortcuts;
    }

    public ArrayList<FileInfo> loadFiles(Container container, FileInfo parent) {
        ArrayList<FileInfo> fileInfos = new ArrayList<>();

        if (parent != null) {
            fileInfos = parent.list();
        }
        else {
            String rootPath = container.getRootDir().getPath();
            fileInfos.add(new FileInfo(container, "C:", rootPath+"/.wine/drive_c", FileInfo.Type.DRIVE));
            for (Drive drive : container.drivesIterator()) {
                fileInfos.add(new FileInfo(container, drive.letter+":", drive.path, FileInfo.Type.DRIVE));
            }

            File userDir = container.getUserDir();
            File documentsDir = new File(userDir, "Documents");
            File favoritesDir = new File(userDir, "Favorites");

            fileInfos.add(new FileInfo(container, documentsDir.getName(), documentsDir.getPath(), FileInfo.Type.DIRECTORY));
            fileInfos.add(new FileInfo(container, favoritesDir.getName(), favoritesDir.getPath(), FileInfo.Type.DIRECTORY));

            Collections.sort(fileInfos);
        }
        return fileInfos;
    }

    public int getNextContainerId() {
        return maxContainerId + 1;
    }

    public Container getContainerById(int id) {
        for (Container container : containers) if (container.id == id) return container;
        return null;
    }

    private void copyCommonDlls(String srcName, String dstName, JSONObject commonDlls, File containerDir) throws JSONException {
        File srcDir = new File(RootFS.find(context).getRootDir(), "/opt/wine/lib/wine/"+srcName);
        JSONArray dlnames = commonDlls.getJSONArray(dstName);

        for (int i = 0; i < dlnames.length(); i++) {
            String dlname = dlnames.getString(i);
            File dstFile = new File(containerDir, ".wine/drive_c/windows/"+dstName+"/"+dlname);
            FileUtils.copy(new File(srcDir, dlname), dstFile);
        }
    }

    // ИСПРАВЛЕННЫЙ МЕТОД с логированием
    private boolean extractContainerPatternFile(String wineVersion, File containerDir) {
        Log.d(TAG, "Extracting container pattern for wine version: " + wineVersion);
        
        if (WineInfo.isMainWineVersion(wineVersion)) {
            Log.d(TAG, "Using main wine version, extracting container_pattern.tzst from assets");
            boolean result = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, "container_pattern.tzst", containerDir);
            
            if (!result) {
                Log.e(TAG, "FAILED to extract container_pattern.tzst from assets!");
                return false;
            }
            
            Log.d(TAG, "Successfully extracted container_pattern.tzst");
            
            try {
                JSONObject commonDlls = new JSONObject(FileUtils.readString(context, "common_dlls.json"));
                copyCommonDlls("x86_64-windows", "system32", commonDlls, containerDir);
                copyCommonDlls("i386-windows", "syswow64", commonDlls, containerDir);
                Log.d(TAG, "Successfully copied common DLLs");
            }
            catch (JSONException e) {
                Log.e(TAG, "Failed to copy common DLLs", e);
                return false;
            }

            return true;
        }
        else {
            File installedWineDir = RootFS.find(context).getInstalledWineDir();
            WineInfo wineInfo = WineInfo.fromIdentifier(context, wineVersion);
            File file = new File(installedWineDir, "container-pattern-"+wineInfo.fullVersion()+".tzst");
            Log.d(TAG, "Using custom wine version, extracting from: " + file.getAbsolutePath());
            boolean result = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, file, containerDir);
            
            if (!result) {
                Log.e(TAG, "FAILED to extract custom container pattern from: " + file.getAbsolutePath());
            }
            return result;
        }
    }
} 
