package com.masterplanner.api.plugin;

import java.util.List;

/**
 * 插件仓库接口
 */
public interface IPluginRepository {
    /**
     * 获取可用的插件列表
     * @return 插件列表
     */
    List<IPluginConfig> getAvailablePlugins();

    /**
     * 下载插件
     * @param pluginId 插件ID
     * @param version 版本
     * @return 下载的插件文件路径
     */
    String downloadPlugin(String pluginId, String version);

    /**
     * 更新插件
     * @param plugin 要更新的插件
     * @return 更新后的插件文件路径
     */
    String updatePlugin(IPlugin plugin);

    /**
     * 检查插件更新
     * @param plugin 要检查的插件
     * @return 是否有更新可用
     */
    boolean hasUpdate(IPlugin plugin);

    /**
     * 获取插件的最新版本
     * @param pluginId 插件ID
     * @return 最新版本号
     */
    String getLatestVersion(String pluginId);

    /**
     * 获取插件的所有可用版本
     * @param pluginId 插件ID
     * @return 版本列表
     */
    List<String> getAvailableVersions(String pluginId);

    /**
     * 获取插件详细信息
     * @param pluginId 插件ID
     * @return 插件配置信息
     */
    IPluginConfig getPluginInfo(String pluginId);

    /**
     * 发布插件
     * @param pluginFile 插件文件路径
     * @return 是否发布成功
     */
    boolean publishPlugin(String pluginFile);

    /**
     * 删除插件
     * @param pluginId 插件ID
     * @param version 版本
     * @return 是否删除成功
     */
    boolean removePlugin(String pluginId, String version);

    /**
     * 获取仓库URL
     * @return 仓库URL
     */
    String getRepositoryUrl();

    /**
     * 设置仓库URL
     * @param url 仓库URL
     */
    void setRepositoryUrl(String url);

    /**
     * 获取仓库凭证
     * @return 仓库凭证
     */
    IRepositoryCredentials getCredentials();

    /**
     * 设置仓库凭证
     * @param credentials 仓库凭证
     */
    void setCredentials(IRepositoryCredentials credentials);
}
