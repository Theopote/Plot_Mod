package com.masterplanner.api.settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置组类
 */
public class SettingsGroup {
    private String name;
    private String description;
    private Map<String, Object> settings;
    private Map<String, Object> defaultValues;
    private List<ISettingsListener> listeners;
    private boolean expanded;

    public SettingsGroup(String name) {
        this.name = name;
        this.settings = new HashMap<>();
        this.defaultValues = new HashMap<>();
        this.listeners = new ArrayList<>();
        this.expanded = false;
    }

    /**
     * 获取组名
     * @return 组名
     */
    public String getName() {
        return name;
    }

    /**
     * 设置组名
     * @param name 组名
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取描述
     * @return 描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置描述
     * @param description 描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取配置值
     * @param key 配置键
     * @return 配置值
     */
    public Object getValue(String key) {
        return settings.get(key);
    }

    /**
     * 设置配置值
     * @param key 配置键
     * @param value 配置值
     */
    public void setValue(String key, Object value) {
        Object oldValue = settings.get(key);
        settings.put(key, value);
        notifyListeners(key, oldValue, value);
    }

    /**
     * 获取默认值
     * @param key 配置键
     * @return 默认值
     */
    public Object getDefaultValue(String key) {
        return defaultValues.get(key);
    }

    /**
     * 设置默认值
     * @param key 配置键
     * @param value 默认值
     */
    public void setDefaultValue(String key, Object value) {
        defaultValues.put(key, value);
    }

    /**
     * 重置到默认值
     */
    public void resetToDefault() {
        for (Map.Entry<String, Object> entry : defaultValues.entrySet()) {
            setValue(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 添加监听器
     * @param listener 监听器
     */
    public void addListener(ISettingsListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * 移除监听器
     * @param listener 监听器
     */
    public void removeListener(ISettingsListener listener) {
        listeners.remove(listener);
    }

    /**
     * 通知监听器
     * @param key 配置键
     * @param oldValue 旧值
     * @param newValue 新值
     */
    private void notifyListeners(String key, Object oldValue, Object newValue) {
        for (ISettingsListener listener : listeners) {
            listener.onSettingChanged(this, key, oldValue, newValue);
        }
    }

    /**
     * 获取是否展开
     * @return 是否展开
     */
    public boolean isExpanded() {
        return expanded;
    }

    /**
     * 设置是否展开
     * @param expanded 是否展开
     */
    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    /**
     * 切换展开状态
     */
    public void toggleExpanded() {
        this.expanded = !this.expanded;
    }
}
