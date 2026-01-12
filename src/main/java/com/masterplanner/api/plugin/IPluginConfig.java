package com.masterplanner.api.plugin;

import java.util.Map;

/**
 * 插件配置接口
 */
public interface IPluginConfig {
    /**
     * 获取插件ID
     * @return 插件ID
     */
    String getId();

    /**
     * 获取插件名称
     * @return 插件名称
     */
    String getName();

    /**
     * 获取插件版本
     * @return 插件版本
     */
    String getVersion();

    /**
     * 获取插件描述
     * @return 插件描述
     */
    String getDescription();

    /**
     * 获取插件作者
     * @return 插件作者
     */
    String getAuthor();

    /**
     * 获取插件依赖
     * @return 插件依赖列表
     */
    String[] getDependencies();

    /**
     * 获取插件配置参数
     * @return 配置参数Map
     */
    Map<String, Object> getParameters();

    /**
     * 获取配置参数值
     * @param key 参数键
     * @return 参数值
     */
    Object getParameter(String key);

    /**
     * 设置配置参数值
     * @param key 参数键
     * @param value 参数值
     */
    void setParameter(String key, Object value);

    /**
     * 验证配置是否有效
     * @return 是否有效
     */
    boolean validate();
}
