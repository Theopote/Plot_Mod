package com.masterplanner.api.settings;

import java.util.List;
import java.util.Map;

/**
 * 配置管理器接口
 */
public interface ISettingsManager {
    /**
     * 获取配置值
     * @param key 配置键
     * @return 配置值
     */
    Object getValue(String key);

    /**
     * 获取配置值，如果不存在则返回默认值
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    Object getValue(String key, Object defaultValue);

    /**
     * 设置配置值
     * @param key 配置键
     * @param value 配置值
     */
    void setValue(String key, Object value);

    /**
     * 获取布尔值配置
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 布尔值
     */
    boolean getBoolean(String key, boolean defaultValue);

    /**
     * 获取整数配置
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 整数值
     */
    int getInt(String key, int defaultValue);

    /**
     * 获取浮点数配置
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 浮点数值
     */
    double getDouble(String key, double defaultValue);

    /**
     * 获取字符串配置
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 字符串值
     */
    String getString(String key, String defaultValue);

    /**
     * 获取所有配置
     * @return 配置映射
     */
    Map<String, Object> getAllSettings();

    /**
     * 保存配置
     */
    void saveSettings();

    /**
     * 加载配置
     */
    void loadSettings();

    /**
     * 重置配置到默认值
     */
    void resetToDefault();

    /**
     * 添加配置监听器
     * @param listener 监听器
     */
    void addSettingsListener(ISettingsListener listener);

    /**
     * 移除配置监听器
     * @param listener 监听器
     */
    void removeSettingsListener(ISettingsListener listener);

    /**
     * 获取配置组
     * @return 配置组列表
     */
    List<SettingsGroup> getSettingsGroups();

    /**
     * 创建配置组
     * @param name 组名
     * @return 配置组
     */
    SettingsGroup createSettingsGroup(String name);

    /**
     * 移除配置组
     * @param group 配置组
     */
    void removeSettingsGroup(SettingsGroup group);

    /**
     * 验证配置值
     * @param key 配置键
     * @param value 配置值
     * @return 是否有效
     */
    boolean validateValue(String key, Object value);
}
