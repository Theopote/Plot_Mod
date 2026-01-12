package com.masterplanner.core.tool;

import com.masterplanner.api.tool.IToolConfig;
import java.util.HashMap;
import java.util.Map;

/**
 * 工具配置实现类
 */
public class ToolConfig implements IToolConfig {
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

    //@Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    //@Override
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
        // TODO: 实现配置保存到JSON的逻辑
        return "{}"; // 临时返回空的JSON对象字符串
    }

    @Override
    public void loadFromJson(String json) {
        // TODO: 实现从JSON加载配置的逻辑
    }
}
