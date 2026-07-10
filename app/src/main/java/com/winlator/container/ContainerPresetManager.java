package com.winlator.container;

import android.content.Context;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

public class ContainerPresetManager {
    
    public static class PresetConfig {
        public String name;
        public String description;
        public String graphicsDriver;
        public String dxWrapper;
        public String resolution;
        public String box64Preset;
        public Map<String, String> envVars;
        public Map<String, String> winComponents;
        
        public PresetConfig() {
            envVars = new LinkedHashMap<>();
            winComponents = new LinkedHashMap<>();
        }
        
        public JSONObject toJSON() throws JSONException {
            JSONObject data = new JSONObject();
            data.put("name", name);
            data.put("graphicsDriver", graphicsDriver);
            data.put("dxwrapper", dxWrapper);
            data.put("resolution", resolution);
            data.put("box64Preset", box64Preset);
            
            if (!envVars.isEmpty()) {
                JSONObject envJson = new JSONObject();
                for (Map.Entry<String, String> entry : envVars.entrySet()) {
                    envJson.put(entry.getKey(), entry.getValue());
                }
                data.put("envVars", envJson);
            }
            
            if (!winComponents.isEmpty()) {
                JSONObject compJson = new JSONObject();
                for (Map.Entry<String, String> entry : winComponents.entrySet()) {
                    compJson.put(entry.getKey(), entry.getValue());
                }
                data.put("winComponents", compJson);
            }
            
            return data;
        }
    }
    
    // Пресет для Mali GPU
    public static PresetConfig createMaliNFSUG2Preset() {
        PresetConfig config = new PresetConfig();
        config.name = "NFS Underground 2 (Mali)";
        config.description = "Оптимизировано для Mali GPU";
        config.graphicsDriver = "virgl,virgl";
        config.dxWrapper = "wined3d";
        config.resolution = "800x600";
        config.box64Preset = "Compatibility";
        
        config.envVars.put("MESA_GL_VERSION_OVERRIDE", "3.3");
        config.envVars.put("MESA_GLSL_VERSION_OVERRIDE", "330");
        config.envVars.put("vblank_mode", "0");
        config.envVars.put("MESA_EXTENSION_OVERRIDE", "GL_EXT_texture_compression_s3tc");
        config.envVars.put("LIBGL_ALWAYS_SOFTWARE", "false");
        
        return config;
    }
    
    // Пресет для Emperor: Battle for Dune
    public static PresetConfig createEmperorBFDPreset() {
        PresetConfig config = new PresetConfig();
        config.name = "Emperor: Battle for Dune";
        config.description = "Westwood 3D RTS";
        config.graphicsDriver = "virgl,virgl";
        config.dxWrapper = "dxvk";
        config.resolution = "1024x768";
        config.box64Preset = "Performance";
        
        config.envVars.put("MESA_GL_VERSION_OVERRIDE", "4.6");
        config.envVars.put("MESA_GLSL_VERSION_OVERRIDE", "460");
        config.envVars.put("vblank_mode", "0");
        config.envVars.put("mesa_glthread", "true");
        config.envVars.put("DXVK_ASYNC", "1");
        config.envVars.put("PULSE_LATENCY_MSEC", "60");
        config.envVars.put("WINEDEBUG", "-all");
        config.envVars.put("STAGING_SHARED_MEMORY", "1");
        
        config.winComponents.put("directmusic", "native");
        
        return config;
    }
    
    // Пресет для C&C Generals
    public static PresetConfig createCAndCGeneralsPreset() {
        PresetConfig config = new PresetConfig();
        config.name = "C&C: Generals / Zero Hour";
        config.description = "SAGE engine RTS";
        config.graphicsDriver = "virgl,virgl";
        config.dxWrapper = "dxvk";
        config.resolution = "1280x720";
        config.box64Preset = "Performance";
        
        config.envVars.put("MESA_GL_VERSION_OVERRIDE", "4.6");
        config.envVars.put("MESA_GLSL_VERSION_OVERRIDE", "460");
        config.envVars.put("vblank_mode", "0");
        config.envVars.put("mesa_glthread", "true");
        config.envVars.put("DXVK_ASYNC", "1");
        config.envVars.put("PULSE_LATENCY_MSEC", "60");
        config.envVars.put("WINEDEBUG", "-all");
        config.envVars.put("DXVK_FRAME_RATE", "60");
        config.envVars.put("STAGING_SHARED_MEMORY", "1");
        config.envVars.put("WINEESYNC", "1");
        
        config.winComponents.put("directplay", "native");
        
        return config;
    }
    
    // Пресет для Red Alert 2
    public static PresetConfig createRedAlert2Preset() {
        PresetConfig config = new PresetConfig();
        config.name = "C&C: Red Alert 2 / YR";
        config.description = "2D изометрическая RTS";
        config.graphicsDriver = "virgl,virgl";
        config.dxWrapper = "wined3d";
        config.resolution = "1024x768";
        config.box64Preset = "Intermediate";
        
        config.envVars.put("MESA_GL_VERSION_OVERRIDE", "3.3");
        config.envVars.put("MESA_GLSL_VERSION_OVERRIDE", "330");
        config.envVars.put("vblank_mode", "0");
        config.envVars.put("WINEDEBUG", "-all");
        config.envVars.put("STAGING_SHARED_MEMORY", "1");
        config.envVars.put("DDRAW", "opengl");  // Для старых 2D игр
        
        return config;
    }
    
    // Универсальный пресет для старых RTS
    public static PresetConfig createGenericRTSPreset() {
        PresetConfig config = new PresetConfig();
        config.name = "Generic RTS Game";
        config.description = "Универсальные настройки для старых RTS";
        config.graphicsDriver = "virgl,virgl";
        config.dxWrapper = "dxvk";
        config.resolution = "800x600";
        config.box64Preset = "Performance";
        
        config.envVars.put("MESA_GL_VERSION_OVERRIDE", "4.6");
        config.envVars.put("MESA_GLSL_VERSION_OVERRIDE", "460");
        config.envVars.put("vblank_mode", "0");
        config.envVars.put("mesa_glthread", "true");
        config.envVars.put("DXVK_ASYNC", "1");
        config.envVars.put("PULSE_LATENCY_MSEC", "60");
        config.envVars.put("WINEDEBUG", "-all");
        config.envVars.put("STAGING_SHARED_MEMORY", "1");
        
        config.winComponents.put("directmusic", "native");
        config.winComponents.put("directplay", "native");
        
        return config;
    }
}
