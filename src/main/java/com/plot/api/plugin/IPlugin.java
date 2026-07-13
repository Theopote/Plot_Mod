package com.plot.api.plugin;

import java.io.File;
import java.util.List;

/**
 * 插件接口
 */
public interface IPlugin {
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
     * 获取插件网站
     * @return 插件网站
     */
    String getWebsite();

    /**
     * 获取插件依赖
     * @return 依赖列表
     */
    List<PluginDependency> getDependencies();

    /**
     * 获取插件配置
     * @return 插件配置
     */
    IPluginConfig getConfig();

    /**
     * 设置插件配置
     */
    void setConfig(IPluginConfig config);

    /**
     * 获取插件数据目录
     * @return 数据目录
     */
    File getDataFolder();

    /**
     * 初始化插件
     */
    void initialize() throws PluginException;

    /**
     * 启用插件
     */
    void enable() throws PluginException;

    /**
     * 禁用插件
     */
    void disable() throws PluginException;

    /**
     * 卸载插件
     */
    void unload() throws PluginException;

    /**
     * 检查插件是否已启用
     * @return 是否已启用
     */
    boolean isEnabled();

    /**
     * 获取插件API版本
     * @return API版本
     */
    String getApiVersion();

    /**
     * 获取插件状态
     * @return 插件状态
     */
    PluginState getState();

    /**
     * 插件激活时调用
     */
    void onActivate();

    /**
     * 插件停用时调用
     */
    void onDeactivate();

    /**
     * 渲染插件界面
     */
    void render();

    /**
     * 在所有 Dock 窗口 {@code begin/end} 完成后渲染模态弹窗。
     * Dear ImGui 要求模态弹窗不能与其它窗口的 begin 交错调用，否则会触发 blocking_modal 断言。
     */
    default void renderDeferredModals() {
    }
}
