package com.masterplanner.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.masterplanner.core.log.LogManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置管理器
 * 负责管理应用程序的配置信息，包括保存和加载配置
 */
public class ConfigManager {
    private static final String CONFIG_FILE_NAME = "masterplanner_config.json";
    private static volatile ConfigManager INSTANCE;
    private static final Object INSTANCE_LOCK = new Object();
    private final Map<String, Object> configMap;
    private final Gson gson;
    private final Path configFilePath;
    
    private ConfigManager() {
        this.configMap = new ConcurrentHashMap<>();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        
        // 获取配置文件路径
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path configDir = gameDir.resolve("masterplanner");
        
        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            
            this.configFilePath = configDir.resolve(CONFIG_FILE_NAME);
            
            // 如果配置文件存在，则加载配置
            if (Files.exists(configFilePath)) {
                loadConfig();
            }
        } catch (IOException e) {
            LogManager.getInstance().error("初始化配置管理器失败", e);
            throw new RuntimeException("初始化配置管理器失败", e);
        }
    }
    
    public static ConfigManager getInstance() {
        if (INSTANCE == null) {
            synchronized (INSTANCE_LOCK) {
                if (INSTANCE == null) {
                    INSTANCE = new ConfigManager();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * 加载配置文件
     */
    public void loadConfig() {
        try (FileReader reader = new FileReader(configFilePath.toFile())) {
            Type type = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> loadedConfig = gson.fromJson(reader, type);
            
            if (loadedConfig != null) {
                configMap.clear();
                configMap.putAll(loadedConfig);
                LogManager.getInstance().info("配置加载成功");
            }
        } catch (IOException e) {
            LogManager.getInstance().error("加载配置文件失败", e);
        }
    }
    
    /**
     * 保存配置到文件
     */
    public void saveConfig() {
        try (FileWriter writer = new FileWriter(configFilePath.toFile())) {
            gson.toJson(configMap, writer);
            LogManager.getInstance().info("配置保存成功");
        } catch (IOException e) {
            LogManager.getInstance().error("保存配置文件失败", e);
        }
    }
    
    /**
     * 设置字符串配置
     */
    public void setString(String key, String value) {
        configMap.put(key, value);
    }
    
    /**
     * 获取字符串配置
     */
    public String getString(String key, String defaultValue) {
        Object value = configMap.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return defaultValue;
    }
    
    /**
     * 设置整数配置
     */
    public void setInt(String key, int value) {
        configMap.put(key, value);
    }
    
    /**
     * 获取整数配置
     */
    public int getInt(String key, int defaultValue) {
        Object value = configMap.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    /**
     * 设置浮点数配置
     */
    public void setFloat(String key, float value) {
        configMap.put(key, value);
    }
    
    /**
     * 获取浮点数配置
     */
    public float getFloat(String key, float defaultValue) {
        Object value = configMap.get(key);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return defaultValue;
    }
    
    /**
     * 设置布尔值配置
     */
    public void setBoolean(String key, boolean value) {
        configMap.put(key, value);
    }
    
    /**
     * 获取布尔值配置
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = configMap.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
    
    /**
     * 设置对象配置
     */
    public void setObject(String key, Object value) {
        configMap.put(key, value);
    }
    
    /**
     * 获取对象配置
     */
    public Object getObject(String key, Object defaultValue) {
        Object value = configMap.get(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * 检查配置是否存在
     */
    public boolean hasConfig(String key) {
        return configMap.containsKey(key);
    }
    
    /**
     * 移除配置
     */
    public void removeConfig(String key) {
        configMap.remove(key);
    }
    
    /**
     * 清空所有配置
     */
    public void clearConfig() {
        configMap.clear();
    }
} 