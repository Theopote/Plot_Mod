package com.plot.api.plugin;

import com.plot.core.plugin.PluginDependencyGraph;
import com.plot.api.plugin.PluginException;

import java.util.List;

/**
 * 插件管理器接口
 */
public interface IPluginManager {
    /**
     * 加载插件
     * @param pluginFile 插件文件路径
     * @return 加载的插件实例
     * @throws PluginException 如果加载过程中发生错误
     */
    IPlugin loadPlugin(String pluginFile) throws PluginException;

    /**
     * 卸载插件
     * @param plugin 要卸载的插件
     * @throws PluginException 如果卸载过程中发生错误
     */
    void unloadPlugin(IPlugin plugin) throws PluginException;

    /**
     * 启用插件
     * @param plugin 要启用的插件
     */
    void enablePlugin(IPlugin plugin);

    /**
     * 禁用插件
     * @param plugin 要禁用的插件
     */
    void disablePlugin(IPlugin plugin);

    /**
     * 重新加载插件
     * @param plugin 要重新加载的插件
     * @return 重新加载后的插件
     * @throws PluginException 如果重新加载过程中发生错误
     */
    IPlugin reloadPlugin(IPlugin plugin) throws PluginException;

    /**
     * 获取所有插件
     * @return 插件列表
     */
    List<IPlugin> getPlugins();

    /**
     * 根据ID获取插件
     * @param pluginId 插件ID
     * @return 插件实例
     */
    IPlugin getPlugin(String pluginId);

    /**
     * 检查插件是否已加载
     * @param pluginId 插件ID
     * @return 是否已加载
     */
    boolean isPluginLoaded(String pluginId);

    /**
     * 检查插件依赖
     * @param plugin 要检查的插件
     * @return 是否满足依赖要求
     */
    boolean checkDependencies(IPlugin plugin);

    /**
     * 获取插件依赖图
     * @return 依赖图
     */
    PluginDependencyGraph getDependencyGraph();

    /**
     * 添加插件监听器
     * @param listener 插件监听器
     */
    void addPluginListener(IPluginListener listener);

    /**
     * 移除插件监听器
     * @param listener 插件监听器
     */
    void removePluginListener(IPluginListener listener);

    /**
     * 获取插件加载器
     * @return 插件加载器
     */
    IPluginLoader getPluginLoader();

    /**
     * 设置插件加载器
     * @param loader 插件加载器
     */
    void setPluginLoader(IPluginLoader loader);

    /**
     * 获取插件仓库
     * @return 插件仓库
     */
    IPluginRepository getPluginRepository();

    /**
     * 设置插件仓库
     * @param repository 插件仓库
     */
    void setPluginRepository(IPluginRepository repository);
}
