package com.masterplanner.api.plugin;

/**
 * 插件生命周期事件监听器接口
 */
public interface IPluginListener {
    /**
     * 插件加载完成时调用
     * @param plugin 加载的插件
     */
    void onPluginLoaded(IPlugin plugin);

    /**
     * 插件卸载完成时调用
     * @param plugin 卸载的插件
     */
    void onPluginUnloaded(IPlugin plugin);

    /**
     * 插件启用时调用
     * @param plugin 启用的插件
     */
    void onPluginEnabled(IPlugin plugin);

    /**
     * 插件禁用时调用
     * @param plugin 禁用的插件
     */
    void onPluginDisabled(IPlugin plugin);

    /**
     * 插件状态改变时调用
     * @param plugin 改变状态的插件
     * @param oldState 旧状态
     * @param newState 新状态
     */
    void onPluginStateChange(IPlugin plugin, PluginState oldState, PluginState newState);

    /**
     * 插件依赖缺失时调用
     * @param plugin 缺失依赖的插件
     * @param dependency 缺失的依赖
     */
    void onPluginMissingDependency(IPlugin plugin, PluginDependency dependency);

    /**
     * 插件版本不兼容时调用
     * @param plugin 不兼容的插件
     * @param requiredVersion 需要的版本
     * @param currentVersion 当前版本
     */
    void onPluginIncompatibleVersion(IPlugin plugin, String requiredVersion, String currentVersion);

    /**
     * 插件加载失败时调用
     * @param pluginFile 插件文件路径
     * @param error 错误信息
     */
    void onPluginLoadError(String pluginFile, Throwable error);
}
