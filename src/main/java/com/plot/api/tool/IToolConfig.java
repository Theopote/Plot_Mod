package com.plot.api.tool;

import java.util.Map;

/**
 * 工具配置接口
 */
public interface IToolConfig {
    /**
     * 获取配置值
     * @param key 配置键
     * @return 配置值
     */
    Object getValue(String key);

    /**
     * 设置配置值
     * @param key 配置键
     * @param value 配置值
     */
    void setValue(String key, Object value);

    /**
     * 获取所有配置
     * @return 配置映射
     */
    Map<String, Object> getAllValues();

    /**
     * 重置配置到默认值
     */
    void resetToDefault();

    /**
     * 从JSON加载配置
     * @param json JSON字符串
     */
    void loadFromJson(String json);

    /**
     * 保存配置到JSON
     * @return JSON字符串
     */
    String saveToJson();

    /**
     * 克隆配置
     * @return 配置副本
     */
    IToolConfig clone();

    /**
     * 设置工具描述
     */
    void setDescription(String description);
    
    /**
     * 设置工具提示
     */
    void setTooltip(String tooltip);
    
    /**
     * 设置工具图标
     */
    void setIcon(String icon);
    
    /**
     * 设置快捷键
     */
    void setShortcutKey(String key);
    
    /**
     * 设置优先级
     */
    void setPriority(int priority);
    
    /**
     * 获取工具描述
     */
    String getDescription();
    
    /**
     * 获取工具提示
     */
    String getTooltip();
    
    /**
     * 获取工具图标
     */
    String getIcon();
    
    /**
     * 获取快捷键
     */
    String getShortcutKey();
    
    /**
     * 获取优先级
     */
    int getPriority();
}
