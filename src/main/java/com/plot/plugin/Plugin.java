package com.plot.plugin;

import com.plot.api.plugin.*;
import com.plot.core.plugin.PluginConfig;
import com.plot.utils.PlotI18n;
import net.minecraft.util.Identifier;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 插件基类，所有插件都需要继承此类。
 * <p>
 * 状态由 {@link com.plot.core.plugin.PluginManager} 统一推进，子类勿旁路跳过生命周期。
 * </p>
 */
public abstract class Plugin implements IPlugin {
    private final String id;
    private final String name;
    private final String description;
    private final Identifier icon;
    private boolean enabled;
    private IPluginConfig config;
    private PluginState state;

    public Plugin(String id, String name, String description, Identifier icon) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.enabled = false;
        this.config = new PluginConfig(id);
        this.state = PluginState.DISCOVERED;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name.startsWith("plugin.") ? PlotI18n.tr(name) : name;
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return description.startsWith("plugin.") ? PlotI18n.tr(description) : description;
    }

    @Override
    public String getAuthor() {
        return "Plot";
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
            state = PluginState.INITIALIZED;
        } catch (Exception e) {
            state = PluginState.FAILED;
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
            state = PluginState.FAILED;
            throw new PluginException("Failed to enable plugin: " + id, e);
        }
    }

    @Override
    public void disable() throws PluginException {
        if (!enabled) {
            if (state != PluginState.DISABLED && state != PluginState.UNLOADED && state != PluginState.DISPOSED) {
                state = PluginState.DISABLED;
            }
            return;
        }
        try {
            onDisable();
            enabled = false;
            state = PluginState.DISABLED;
        } catch (Exception e) {
            state = PluginState.FAILED;
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
            state = PluginState.FAILED;
            throw new PluginException("Failed to unload plugin: " + id, e);
        }
    }

    @Override
    public void dispose() throws PluginException {
        try {
            if (enabled) {
                disable();
            }
            if (state != PluginState.UNLOADED && state != PluginState.DISPOSED) {
                state = PluginState.UNLOADED;
            }
            state = PluginState.DISPOSED;
        } catch (Exception e) {
            state = PluginState.FAILED;
            throw new PluginException("Failed to dispose plugin: " + id, e);
        }
    }

    @Override
    public void transitionState(PluginState newState) {
        if (newState != null) {
            this.state = newState;
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
        if (enabled) {
            state = PluginState.ACTIVE;
        }
    }

    @Override
    public void onDeactivate() {
        if (enabled) {
            state = PluginState.INACTIVE;
        }
    }

    public abstract void onEnable();

    public abstract void onDisable();

    public Identifier getIcon() {
        return icon;
    }
}
