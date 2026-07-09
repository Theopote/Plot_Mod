package com.plot.core.tool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.plot.api.tool.IToolConfig;
import com.plot.core.log.LogManager;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具配置实现类
 */
public class ToolConfig implements IToolConfig {
    private static final Gson GSON = new GsonBuilder().create();

    private final Map<String, Object> config;
    private boolean enabled;
    private String shortcutKey;
    private String icon;
    private String description;
    private String tooltip;
    private int priority;

    public ToolConfig() {
        this.config = new HashMap<>();
        this.enabled = true;
        this.priority = 0;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setShortcutKey(String shortcutKey) {
        this.shortcutKey = shortcutKey;
    }

    @Override
    public String getShortcutKey() {
        return shortcutKey;
    }

    @Override
    public void setIcon(String icon) {
        this.icon = icon;
    }

    @Override
    public String getIcon() {
        return icon;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setTooltip(String tooltip) {
        this.tooltip = tooltip;
    }

    @Override
    public String getTooltip() {
        return tooltip;
    }

    @Override
    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public ToolConfig clone() {
        ToolConfig clone = new ToolConfig();
        clone.config.putAll(this.config);
        clone.enabled = this.enabled;
        clone.shortcutKey = this.shortcutKey;
        clone.icon = this.icon;
        clone.description = this.description;
        clone.tooltip = this.tooltip;
        clone.priority = this.priority;
        return clone;
    }

    @Override
    public Object getValue(String key) {
        return config.get(key);
    }

    @Override
    public void setValue(String key, Object value) {
        config.put(key, value);
    }

    @Override
    public Map<String, Object> getAllValues() {
        return new HashMap<>(config);
    }

    @Override
    public void resetToDefault() {
        config.clear();
        enabled = true;
        shortcutKey = null;
        icon = null;
        description = null;
        tooltip = null;
        priority = 0;
    }

    @Override
    public String saveToJson() {
        ConfigSnapshot snapshot = new ConfigSnapshot();
        snapshot.enabled = enabled;
        snapshot.shortcutKey = shortcutKey;
        snapshot.icon = icon;
        snapshot.description = description;
        snapshot.tooltip = tooltip;
        snapshot.priority = priority;
        snapshot.values = new HashMap<>(config);
        return GSON.toJson(snapshot);
    }

    @Override
    public void loadFromJson(String json) {
        if (json == null || json.isBlank()) {
            return;
        }

        try {
            ConfigSnapshot snapshot = GSON.fromJson(json, ConfigSnapshot.class);
            if (snapshot == null) {
                return;
            }

            enabled = snapshot.enabled;
            shortcutKey = snapshot.shortcutKey;
            icon = snapshot.icon;
            description = snapshot.description;
            tooltip = snapshot.tooltip;
            priority = snapshot.priority;

            config.clear();
            if (snapshot.values != null) {
                config.putAll(snapshot.values);
            }
        } catch (JsonSyntaxException e) {
            LogManager.getInstance().warn("Failed to parse tool config JSON: {}", e.getMessage());
        }
    }

    private static final class ConfigSnapshot {
        private boolean enabled = true;
        private String shortcutKey;
        private String icon;
        private String description;
        private String tooltip;
        private int priority;
        private Map<String, Object> values;
    }
}
