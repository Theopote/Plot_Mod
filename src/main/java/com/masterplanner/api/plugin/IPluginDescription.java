package com.masterplanner.api.plugin;

import java.util.List;

/**
 * 插件描述信息接口
 */
public interface IPluginDescription {
    String getId();
    String getName();
    String getVersion();
    String getMainClass();
    String getDescription();
    String getAuthor();
    String getWebsite();
    List<PluginDependency> getDependencies();
    IPluginConfig getConfig();
} 