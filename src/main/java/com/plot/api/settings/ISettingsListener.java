package com.plot.api.settings;

/**
 * 配置监听器接口
 */
public interface ISettingsListener {
    /**
     * 配置值改变时调用
     * @param group 配置组
     * @param key 配置键
     * @param oldValue 旧值
     * @param newValue 新值
     */
    void onSettingChanged(SettingsGroup group, String key, Object oldValue, Object newValue);

    /**
     * 配置重置时调用
     * @param group 配置组
     */
    void onSettingsReset(SettingsGroup group);

    /**
     * 配置加载时调用
     * @param group 配置组
     */
    void onSettingsLoaded(SettingsGroup group);

    /**
     * 配置保存时调用
     * @param group 配置组
     */
    void onSettingsSaved(SettingsGroup group);
}
