package com.masterplanner.plugin;

import com.masterplanner.api.plugin.*;
import com.masterplanner.core.plugin.PluginConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 插件基类，所有插件都需要继承此类
 */
public abstract class Plugin implements IPlugin {
    private final String id;
    private final String name;
    private final String description;
    private final String icon;
    private boolean enabled;
    private IPluginConfig config;
    private PluginState state;
    
    public Plugin(String id, String name, String description, String icon) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.enabled = false;
        this.config = new PluginConfig(id);
        this.state = PluginState.DISABLED;
    }
    
    @Override
    public String getId() {
        return id;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getVersion() {
        return "1.0.0";
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    @Override
    public String getAuthor() {
        return "MasterPlanner";
    }
    
    @Override
    public String getWebsite() {
        return "";
    }
    
    @Override
    public List<PluginDependency> getDependencies() {
        return new ArrayList<>();
    }
    
    @Override
    public IPluginConfig getConfig() {
        return config;
    }
    
    @Override
    public void setConfig(IPluginConfig config) {
        this.config = config;
    }
    
    @Override
    public File getDataFolder() {
        return new File("config/plugins/" + id);
    }
    
    @Override
    public void initialize() throws PluginException {
        try {
            state = PluginState.LOADED;
        } catch (Exception e) {
            throw new PluginException("Failed to initialize plugin: " + id, e);
        }
    }
    
    @Override
    public void enable() throws PluginException {
        if (enabled) {
            return;
        }
        try {
            onEnable();
            enabled = true;
            state = PluginState.ENABLED;
        } catch (Exception e) {
            throw new PluginException("Failed to enable plugin: " + id, e);
        }
    }
    
    @Override
    public void disable() throws PluginException {
        if (!enabled) {
            return;
        }
        try {
            onDisable();
            enabled = false;
            state = PluginState.DISABLED;
        } catch (Exception e) {
            throw new PluginException("Failed to disable plugin: " + id, e);
        }
    }
    
    @Override
    public void unload() throws PluginException {
        try {
            if (enabled) {
                disable();
            }
            state = PluginState.UNLOADED;
        } catch (Exception e) {
            throw new PluginException("Failed to unload plugin: " + id, e);
        }
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    @Override
    public String getApiVersion() {
        return "1.0.0";
    }
    
    @Override
    public PluginState getState() {
        return state;
    }
    
    @Override
    public void onActivate() {
        // 默认实现，子类可以覆盖
    }
    
    @Override
    public void onDeactivate() {
        // 默认实现，子类可以覆盖
    }
    
    // 插件生命周期方法（抽象方法，子类必须实现）
    public abstract void onEnable();
    public abstract void onDisable();
    
    public String getIcon() {
        return icon;
    }
}
