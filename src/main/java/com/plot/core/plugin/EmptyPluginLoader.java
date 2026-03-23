package com.plot.core.plugin;

import com.plot.api.plugin.IPlugin;
import com.plot.api.plugin.IPluginLoader;
import com.plot.api.plugin.IPluginDescription;
import com.plot.api.plugin.PluginException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * 空插件加载器实现
 * 用于在插件系统不可用时提供默认行为
 */
public class EmptyPluginLoader implements IPluginLoader {

    private static final String EMPTY_LOADER_MESSAGE = "此操作在空插件加载器中不可用";

    @Override
    public IPlugin loadPlugin(String pluginFile) throws PluginException {
        throw new PluginException(EMPTY_LOADER_MESSAGE);
    }
    
    @Override
    public void unloadPlugin(IPlugin plugin) throws PluginException {
        throw new PluginException(EMPTY_LOADER_MESSAGE);
    }
    
    @Override
    public boolean isPluginFile(String filePath) {
        return false; // 空加载器不支持任何插件文件
    }
    
    @Override
    public IPluginDescription getPluginDescription(String pluginFile) throws PluginException {
        throw new PluginException(EMPTY_LOADER_MESSAGE);
    }
    
    @Override
    public ClassLoader getPluginClassLoader(IPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("插件实例不能为空");
        }
        return plugin.getClass().getClassLoader();
    }
    
    @Override
    public List<IPlugin> getLoadedPlugins() {
        return Collections.emptyList(); // 返回空列表而不是null
    }
    
    @Override
    public Path getPluginsDirectory() {
        return null; // 空加载器没有插件目录
    }
    
    @Override
    public void loadPlugins() {
        // 空实现,不做任何操作
    }
    
    @Override
    public void enablePlugin(String pluginId) {
        // 空实现,不做任何操作
    }
    
    @Override
    public void disablePlugin(String pluginId) {
        // 空实现,不做任何操作
    }
} 