package com.plot.core.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.api.plugin.IPluginConfig;
import com.plot.api.plugin.PluginDependency;
import com.plot.core.log.LogManager;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;
import com.google.gson.reflect.TypeToken;

/**
 * 插件配置实现类
 * 提供基于内存的配置管理和文件持久化功能
 */
public class PluginConfig implements IPluginConfig {
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final String pluginId;
    private final Path configPath;
    private final Map<String, Object> config;

    /**
     * 从Properties创建配置
     */
    public PluginConfig(Properties props) {
        this.pluginId = props.getProperty("plugin-id");
        this.configPath = getConfigDirectory().resolve(pluginId + ".json");
        this.config = new HashMap<>();
        
        // 加载配置项
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("config.")) {
                String configKey = key.substring("config.".length());
                config.put(configKey, props.getProperty(key));
            }
        }
        
        // 如果配置文件存在，加载文件中的配置
        loadFromFile();
    }

    /**
     * 使用插件ID创建配置
     */
    public PluginConfig(String pluginId) {
        this.pluginId = pluginId;
        this.configPath = getConfigDirectory().resolve(pluginId + ".json");
        this.config = new HashMap<>();
        loadFromFile();
    }

    /**
     * 获取配置目录
     */
    private static Path getConfigDirectory() {
        Path configDir = Paths.get("config", "plugins");
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LogManager.getInstance().error("Failed to create config directory", e);
        }
        return configDir;
    }

    /**
     * 获取配置值
     */
    private Object getValue(String key) {
        return config.get(key);
    }

    /**
     * 设置配置值
     */
    private void setValue(String key, Object value) {
        config.put(key, value);
    }

    @Override
    public Object getParameter(String key) {
        return getValue(key);
    }

    @Override
    public void setParameter(String key, Object value) {
        setValue(key, value);
    }

    @Override
    public Map<String, Object> getParameters() {
        return new HashMap<>(config);
    }

    @Override
    public String getName() {
        return (String) getValue("name");
    }

    @Override
    public String getVersion() {
        return (String) getValue("version");
    }

    @Override
    public String getDescription() {
        return (String) getValue("description");
    }

    @Override
    public String getAuthor() {
        return (String) getValue("author");
    }

    //@Override
    public String getWebsite() {
        return (String) getValue("website");
    }

    //@Override
    public String getApiVersion() {
        return (String) getValue("api-version");
    }

    @Override
    public String[] getDependencies() {
        List<PluginDependency> deps = getDependencyList();
        return deps.stream()
            .map(PluginDependency::getPluginId)
            .toArray(String[]::new);
    }

    private List<PluginDependency> getDependencyList() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> deps = (List<Map<String, Object>>) getValue("dependencies");
        if (deps == null) {
            return new ArrayList<>();
        }
        
        List<PluginDependency> dependencies = new ArrayList<>();
        for (Map<String, Object> dep : deps) {
            String id = (String) dep.get("id");
            String version = (String) dep.get("version");
            boolean required = (boolean) dep.getOrDefault("required", true);
            dependencies.add(new PluginDependency(id, version, required));
        }
        return dependencies;
    }

    @Override
    public boolean validate() {
        return getId() != null && !getId().isEmpty() &&
               getName() != null && !getName().isEmpty() &&
               getVersion() != null && !getVersion().isEmpty();
    }

    @Override
    public String toString() {
        return String.format("PluginConfig[pluginId=%s, path=%s]", pluginId, configPath);
    }

    /**
     * 从文件加载配置
     */
    public void loadFromFile() {
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                Type type = new TypeToken<Map<String, Object>>(){}.getType();
                Map<String, Object> loadedConfig = GSON.fromJson(json, type);
                if (loadedConfig != null) {
                    config.putAll(loadedConfig);
                }
            } catch (IOException e) {
                LogManager.getInstance().error("Failed to load config file: " + configPath, e);
            }
        }
    }

    /**
     * 保存配置到文件
     */
    public void saveToFile() {
        try {
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(config);
            Files.writeString(configPath, json);
        } catch (IOException e) {
            LogManager.getInstance().error("Failed to save config file: " + configPath, e);
        }
    }

    /**
     * 获取配置文件路径
     */
    public Path getConfigPath() {
        return configPath;
    }

    /**
     * 获取插件ID
     */
    public String getPluginId() {
        return pluginId;
    }

    @Override
    public String getId() {
        return pluginId;
    }
}
