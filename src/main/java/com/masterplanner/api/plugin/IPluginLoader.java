package com.masterplanner.api.plugin;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * 插件加载器接口
 */
public interface IPluginLoader {
    /**
     * 加载插件
     * @param pluginFile 插件文件路径
     * @return 加载的插件实例
     * @throws PluginException 如果加载失败
     */
    IPlugin loadPlugin(String pluginFile) throws PluginException;

    /**
     * 卸载插件
     * @param plugin 要卸载的插件
     * @throws PluginException 如果卸载失败
     */
    void unloadPlugin(IPlugin plugin) throws PluginException;

    /**
     * 检查文件是否是有效的插件文件
     * @param filePath 文件路径
     * @return 是否是有效的插件文件
     */
    boolean isPluginFile(String filePath);

    /**
     * 获取插件描述信息
     * @param pluginFile 插件文件路径
     * @return 插件描述信息
     * @throws PluginException 如果读取失败
     */
    IPluginDescription getPluginDescription(String pluginFile) throws PluginException;

    /**
     * 获取插件的类加载器
     * @param plugin 插件实例
     * @return 类加载器
     */
    ClassLoader getPluginClassLoader(IPlugin plugin);

    /**
     * 获取已加载的插件列表
     * @return 插件列表
     */
    List<IPlugin> getLoadedPlugins();

    /**
     * 获取插件目录
     * @return 插件目录
     */
    Path getPluginsDirectory();

    /**
     * 加载所有插件
     */
    void loadPlugins();

    /**
     * 启用插件
     */
    void enablePlugin(String pluginId);

    /**
     * 禁用插件
     */
    void disablePlugin(String pluginId);
}
