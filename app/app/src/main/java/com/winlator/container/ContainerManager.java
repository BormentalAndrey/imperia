package com.winlator.container;

import android.content.Context;
import android.os.Handler;

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
    private final ArrayList<Container> containers = new ArrayList<>();
    private int maxContainerId = 0;
    private final File homeDir;
    private final Context context;
    
    // Добавляем типы пресетов
    public enum ContainerPreset {
        MALI_NFS_UG2("NFS Underground 2 Mali", "Mali GPU optimized for NFS UG2"),
        RTS_EMPEROR("Emperor: Battle for Dune", "RTS games optimized"),
        RTS_CANDC("C&C Generals/Zero Hour", "RTS SAGE engine games"),
        RTS_GENERIC("Generic RTS", "General RTS games setup");
        
        public final String name;
        public final String description;
        
        ContainerPreset(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }

    public ContainerManager(Context context) {
        this.context = context;
        File rootDir = RootFS.find(context).getRootDir();
        homeDir = new File(rootDir, "home");
        loadContainers();
        
        if (containers.isEmpty()) {
            // Создаем контейнер с предустановками Mali по умолчанию
            createDefaultContainerSync(ContainerPreset.MALI_NFS_UG2);
        }
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
                            File configFile = new File(file, ".container");
                            if (!configFile.exists()) continue;
                            
                            String configData = FileUtils.readString(configFile);
                            if (configData == null || configData.isEmpty()) continue;
                            
                            Container container = new Container(Integer.parseInt(file.getName().replace(RootFS.USER+"-", "")));
                            container.setRootDir(new File(homeDir, RootFS.USER+"-"+container.id));
                            JSONObject data = new JSONObject(configData);
                            container.loadData(data);
                            containers.add(container);
                            maxContainerId = Math.max(maxContainerId, container.id);
                        }
                    }
                }
            }
        }
        catch (JSONException e) {}
    }

    /**
     * Синхронное создание контейнера с выбранным пресетом
     */
    private void createDefaultContainerSync(ContainerPreset preset) {
        try {
            JSONObject data = new JSONObject();
            
            switch (preset) {
                case MALI_NFS_UG2:
                    configureMaliPreset(data);
                    break;
                case RTS_EMPEROR:
                    configureRTSPreset(data, "Emperor: Battle for Dune", "1024x768");
                    break;
                case RTS_CANDC:
                    configureRTSPreset(data, "C&C Generals", "1280x720");
                    break;
                case RTS_GENERIC:
                    configureRTSPreset(data, "RTS Game", "800x600");
                    break;
            }
            
            Container container = createContainer(data);
            if (container != null) {
                activateContainer(container);
                FileUtils.chmod(container.getRootDir(), 0771);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Конфигурация для Mali GPU (NFS Underground 2)
     */
    private void configureMaliPreset(JSONObject data) throws JSONException {
        data.put("name", "NFS Underground 2 Mali");
        data.put("graphicsDriver", "virgl,virgl");
        data.put("dxwrapper", "wined3d");
        data.put("resolution", "800x600");
        data.put("box64Preset", "Compatibility");
        
        // Специфичные для Mali настройки
        JSONObject envVars = new JSONObject();
        envVars.put("MESA_GL_VERSION_OVERRIDE", "3.3");
        envVars.put("MESA_GLSL_VERSION_OVERRIDE", "330");
        envVars.put("vblank_mode", "0");
        envVars.put("MESA_EXTENSION_OVERRIDE", "GL_EXT_texture_compression_s3tc");
        envVars.put("force_glsl_extensions_warn", "true");
        envVars.put("LIBGL_ALWAYS_SOFTWARE", "false");
        data.put("envVars", envVars);
        
        // WinComponents для NFS UG2
        JSONObject winComponents = new JSONObject();
        winComponents.put("directmusic", "native");
        winComponents.put("directplay", "native");
        data.put("winComponents", winComponents);
        
        // Дополнительные настройки Mali
        data.put("enableCsmt", true);
        data.put("enableEsync", false);
        data.put("wineVersion", "wine-8.0.1");
        data.put("screenWidth", 800);
        data.put("screenHeight", 600);
    }
    
    /**
     * Конфигурация для RTS игр
     */
    private void configureRTSPreset(JSONObject data, String gameName, String resolution) throws JSONException {
        data.put("name", gameName);
        data.put("graphicsDriver", "virgl,virgl");
        data.put("dxwrapper", "dxvk");
        data.put("resolution", resolution);
        data.put("box64Preset", "Performance");
        
        // Парсим разрешение
        String[] resParts = resolution.split("x");
        if (resParts.length == 2) {
            data.put("screenWidth", Integer.parseInt(resParts[0]));
            data.put("screenHeight", Integer.parseInt(resParts[1]));
        }
        
        // Переменные окружения для RTS
        JSONObject envVars = new JSONObject();
        envVars.put("MESA_GL_VERSION_OVERRIDE", "4.6");
        envVars.put("MESA_GLSL_VERSION_OVERRIDE", "460");
        envVars.put("vblank_mode", "0");
        envVars.put("mesa_glthread", "true");
        envVars.put("DXVK_ASYNC", "1");
        envVars.put("DXVK_HUD", "0");
        envVars.put("PULSE_LATENCY_MSEC", "60");
        envVars.put("WINEDEBUG", "-all");
        envVars.put("DXVK_FRAME_RATE", "60");
        
        // Специфичные для стратегий оптимизации
        envVars.put("STAGING_SHARED_MEMORY", "1");
        envVars.put("WINEESYNC", "1");
        envVars.put("DXVK_CONFIG_FILE", "/dev/null");
        envVars.put("DXVK_LOG_LEVEL", "none");
        
        data.put("envVars", envVars);
        
        // WinComponents для RTS
        JSONObject winComponents = new JSONObject();
        winComponents.put("directmusic", "native");
        winComponents.put("directplay", "native");
        winComponents.put("d3dx9_43", "native");
        winComponents.put("d3dx9_42", "native");
        winComponents.put("d3dx9_36", "native");
        winComponents.put("d3dcompiler_43", "native");
        winComponents.put("d3dcompiler_47", "native");
        data.put("winComponents", winComponents);
        
        // Дополнительные настройки
        data.put("enableCsmt", true);
        data.put("enableEsync", true);
        data.put("wineVersion", "wine-8.0.1");
        data.put("inputMode", "desktop");
        data.put("showFps", false);
        data.put("enableWineDebug", false);
        
        // Специфичные настройки для каждой игры
        if (gameName.contains("Emperor")) {
            envVars.put("DXVK_FRAME_RATE", "30"); // Emperor работает лучше на 30 FPS
            data.put("envVars", envVars);
        } else if (gameName.contains("Generals")) {
            envVars.put("DXVK_FRAME_RATE", "60");
            data.put("envVars", envVars);
        }
    }

    /**
     * Создание контейнера с выбором пресета (публичный метод)
     */
    public void createContainerWithPreset(ContainerPreset preset, Callback<Container> callback) {
        createContainerWithPresetAsync(preset, callback);
    }
    
    private void createContainerWithPresetAsync(ContainerPreset preset, Callback<Container> callback) {
        final Handler handler = new Handler();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                JSONObject data = new JSONObject();
                
                switch (preset) {
                    case MALI_NFS_UG2:
                        configureMaliPreset(data);
                        break;
                    case RTS_EMPEROR:
                        configureRTSPreset(data, "Emperor: Battle for Dune", "1024x768");
                        break;
                    case RTS_CANDC:
                        configureRTSPreset(data, "C&C Generals", "1280x720");
                        break;
                    case RTS_GENERIC:
                        configureRTSPreset(data, "RTS Game", "800x600");
                        break;
                }
                
                final Container container = createContainer(data);
                handler.post(() -> callback.call(container));
            } catch (JSONException e) {
                handler.post(() -> callback.call(null));
            }
        });
    }

    /**
     * Применение пресета к существующему контейнеру
     */
    public void applyPresetToContainer(Container container, ContainerPreset preset) {
        try {
            switch (preset) {
                case MALI_NFS_UG2:
                    container.setName("NFS Underground 2 Mali");
                    container.setGraphicsDriver("virgl,virgl");
                    container.setDXWrapper("wined3d");
                    container.setScreenSize(800, 600);
                    container.setBox64Preset("Compatibility");
                    break;
                case RTS_EMPEROR:
                    container.setName("Emperor: Battle for Dune");
                    container.setGraphicsDriver("virgl,virgl");
                    container.setDXWrapper("dxvk");
                    container.setScreenSize(1024, 768);
                    container.setBox64Preset("Performance");
                    break;
                case RTS_CANDC:
                    container.setName("C&C Generals");
                    container.setGraphicsDriver("virgl,virgl");
                    container.setDXWrapper("dxvk");
                    container.setScreenSize(1280, 720);
                    container.setBox64Preset("Performance");
                    break;
                case RTS_GENERIC:
                    container.setName("RTS Game");
                    container.setGraphicsDriver("virgl,virgl");
                    container.setDXWrapper("dxvk");
                    container.setScreenSize(800, 600);
                    container.setBox64Preset("Performance");
                    break;
            }
            container.saveData();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void activateContainer(Container container) {
        container.setRootDir(new File(homeDir, RootFS.USER+"-"+container.id));
        File file = new File(homeDir, RootFS.USER);
        file.delete();
        FileUtils.symlink(RootFS.USER+"-"+container.id, file.getPath());
    }

    public void createContainerAsync(final JSONObject data, Callback<Container> callback) {
        final Handler handler = new Handler();
        Executors.newSingleThreadExecutor().execute(() -> {
            final Container container = createContainer(data);
            handler.post(() -> callback.call(container));
        });
    }

    public void duplicateContainerAsync(Container container, Runnable callback) {
        final Handler handler = new Handler();
        Executors.newSingleThreadExecutor().execute(() -> {
            duplicateContainer(container);
            handler.post(callback);
        });
    }

    public void removeContainerAsync(Container container, Runnable callback) {
        final Handler handler = new Handler();
        Executors.newSingleThreadExecutor().execute(() -> {
            removeContainer(container);
            handler.post(callback);
        });
    }

    private Container createContainer(JSONObject data) {
        try {
            int id = maxContainerId + 1;
            data.put("id", id);

            File containerDir = new File(homeDir, RootFS.USER+"-"+id);
            if (!containerDir.mkdirs()) return null;

            Container container = new Container(id);
            container.setRootDir(containerDir);
            container.loadData(data);

            boolean isMainWineVersion = !data.has("wineVersion") || WineInfo.isMainWineVersion(data.getString("wineVersion"));
            if (!isMainWineVersion) container.setWineVersion(data.getString("wineVersion"));

            if (!extractContainerPatternFile(container.getWineVersion(), containerDir)) {
                FileUtils.delete(containerDir);
                return null;
            }

            FileUtils.chmod(containerDir, 0771);
            FileUtils.chmod(container.getConfigFile(), 0666);

            container.saveData();
            maxContainerId++;
            containers.add(container);
            return container;
        }
        catch (JSONException e) {}
        return null;
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

    private boolean extractContainerPatternFile(String wineVersion, File containerDir) {
        if (WineInfo.isMainWineVersion(wineVersion)) {
            boolean result = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, "container_pattern.tzst", containerDir);

            if (result) {
                try {
                    JSONObject commonDlls = new JSONObject(FileUtils.readString(context, "common_dlls.json"));
                    copyCommonDlls("x86_64-windows", "system32", commonDlls, containerDir);
                    copyCommonDlls("i386-windows", "syswow64", commonDlls, containerDir);
                }
                catch (JSONException e) {
                    return false;
                }
            }

            return result;
        }
        else {
            File installedWineDir = RootFS.find(context).getInstalledWineDir();
            WineInfo wineInfo = WineInfo.fromIdentifier(context, wineVersion);
            File file = new File(installedWineDir, "container-pattern-"+wineInfo.fullVersion()+".tzst");
            return TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, file, containerDir);
        }
    }
}
