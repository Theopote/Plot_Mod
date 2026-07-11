package com.plot.core.plugin;

import com.plot.api.plugin.*;
import com.plot.core.log.LogManager;
import com.plot.api.plugin.PluginException;
import com.plot.api.plugin.IPluginListener;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 插件管理器实现类
 */
public class PluginManager implements IPluginManager {
    private static final PluginManager INSTANCE = new PluginManager();
    
    private final Map<String, IPlugin> plugins;
    private final List<IPluginListener> listeners;
    private final PluginDependencyGraph dependencyGraph;
    private IPluginLoader pluginLoader;
    private IPluginRepository pluginRepository;
    private IPlugin activePlugin;

    private PluginManager() {
        this.plugins = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.dependencyGraph = new PluginDependencyGraph();
        
        // 初始化插件加载器
        try {
            Path pluginsPath = FabricLoader.getInstance()
                .getGameDir()
                .resolve("plot")
                .resolve("plugins");
                
            // 确保目录存在
            Files.createDirectories(pluginsPath);
            
            this.pluginLoader = new PluginLoader(pluginsPath);
            
            // 注册内置插件
            registerBuiltinPlugins();
            
            // 加载外部插件
            loadPlugins();
        } catch (Exception e) {
            LogManager.getInstance().error("Failed to initialize plugin manager", e);
        }
    }
    
    /**
     * 注册内置插件
     */
    private void registerBuiltinPlugins() {
        try {
            // 注册土方平衡插件
            com.plot.plugin.EarthworkPlugin earthworkPlugin = new com.plot.plugin.EarthworkPlugin();
            registerBuiltinPlugin(earthworkPlugin);
            
            // 注册图片工具插件
            com.plot.plugin.ImageToolsPlugin imageToolsPlugin = new com.plot.plugin.ImageToolsPlugin();
            registerBuiltinPlugin(imageToolsPlugin);
            
            // 注册道路系统插件
            com.plot.plugin.RoadSystemPlugin roadSystemPlugin = new com.plot.plugin.RoadSystemPlugin();
            registerBuiltinPlugin(roadSystemPlugin);
            
            LogManager.getInstance().info("Registered {} builtin plugins", plugins.size());
        } catch (Exception e) {
            LogManager.getInstance().error("Failed to register builtin plugins", e);
        }
    }
    
    /**
     * 注册单个内置插件
     */
    private void registerBuiltinPlugin(IPlugin plugin) {
        try {
            if (plugin == null) {
                LogManager.getInstance().warn("Cannot register null plugin");
                return;
            }
            
            // 检查是否已加载
            if (plugins.containsKey(plugin.getId())) {
                LogManager.getInstance().warn("Plugin already registered: " + plugin.getId());
                return;
            }
            
            // 初始化插件
            plugin.initialize();
            
            // 添加到依赖图
            dependencyGraph.addPlugin(plugin);
            
            // 添加到插件列表
            plugins.put(plugin.getId(), plugin);
            
            // 默认启用插件
            try {
                plugin.enable();
            } catch (Exception e) {
                LogManager.getInstance().warn("Failed to enable plugin " + plugin.getId() + " on registration", e);
            }
            
            notifyListeners(plugin, PluginState.LOADED);
            LogManager.getInstance().info("Registered builtin plugin: " + plugin.getId());
        } catch (Exception e) {
            LogManager.getInstance().error("Failed to register builtin plugin: " + (plugin != null ? plugin.getId() : "null"), e);
        }
    }

    public static PluginManager getInstance() {
        return INSTANCE;
    }

    private void loadPlugins() {
        if (pluginLoader == null) {
            LogManager.getInstance().warn("Plugin loader is not initialized");
            return;
        }

        Path pluginsPath = pluginLoader.getPluginsDirectory();
        if (pluginsPath == null) {
            LogManager.getInstance().warn("Plugins directory path is null");
            return;
        }

        File pluginsDir = pluginsPath.toFile();
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs();
            return;
        }

        for (File file : Objects.requireNonNull(pluginsDir.listFiles())) {
            if (pluginLoader.isPluginFile(file.getPath())) {
                try {
                    IPlugin plugin = pluginLoader.loadPlugin(file.getPath());
                    plugins.put(plugin.getId(), plugin);
                } catch (Exception e) {
                    LogManager.getInstance().error("Failed to load plugin: " + file.getName(), e);
                }
            }
        }
    }

    @Override
    public IPlugin loadPlugin(String pluginFile) {
        try {
            if (pluginFile == null || pluginFile.isEmpty()) {
                LogManager.getInstance().error("Invalid plugin file path");
                return null;
            }

            // 加载插件
            IPlugin plugin = pluginLoader.loadPlugin(pluginFile);
            if (plugin == null) {
                throw new PluginException("Failed to load plugin from file: " + pluginFile);
            }

            // 检查是否已加载
            if (plugins.containsKey(plugin.getId())) {
                LogManager.getInstance().warn("Plugin already loaded: " + plugin.getId());
                return plugins.get(plugin.getId());
            }

            // 添加到依赖图
            dependencyGraph.addPlugin(plugin);

            // 检查循环依赖
            if (dependencyGraph.hasCircularDependencies()) {
                throw new PluginException("Circular dependency detected for plugin: " + plugin.getId());
            }

            // 检查依赖
            if (!checkDependencies(plugin)) {
                throw new PluginException("Missing dependencies for plugin: " + plugin.getId());
            }

            // 初始化插件
            plugin.initialize();
            plugins.put(plugin.getId(), plugin);
            notifyListeners(plugin, PluginState.LOADED);
            LogManager.getInstance().info("Loaded plugin: " + plugin.getId());
            return plugin;
        } catch (Exception e) {
            LogManager.getInstance().error("Failed to load plugin: " + pluginFile, e);
            return null;
        }
    }

    @Override
    public void unloadPlugin(IPlugin plugin) throws PluginException {
        if (plugin == null || !plugins.containsKey(plugin.getId())) {
            return;
        }

        try {
            // 检查依赖该插件的其他插件
            Set<String> dependents = dependencyGraph.getDependents(plugin.getId());
            if (!dependents.isEmpty()) {
                throw new PluginException("Cannot unload plugin " + plugin.getId() + 
                                        " because it is required by: " + String.join(", ", dependents));
            }

            plugin.disable();
            plugin.unload();
            plugins.remove(plugin.getId());
            dependencyGraph.removePlugin(plugin.getId());
            pluginLoader.unloadPlugin(plugin);
            notifyListeners(plugin, PluginState.UNLOADED);
            LogManager.getInstance().info("Unloaded plugin: " + plugin.getId());

        } catch (Exception e) {
            LogManager.getInstance().error("Failed to unload plugin: " + plugin.getId(), e);
            throw new PluginException("Failed to unload plugin: " + e.getMessage(), e);
        }
    }

    @Override
    public void enablePlugin(IPlugin plugin) {
        if (plugin == null || !plugins.containsKey(plugin.getId()) || plugin.isEnabled()) {
            return;
        }

        try {
            plugin.enable();
            notifyListeners(plugin, PluginState.ENABLED);
            LogManager.getInstance().info("Enabled plugin: " + plugin.getId());
        } catch (Exception e) {
            LogManager.getInstance().error("Failed to enable plugin: " + plugin.getId(), e);
        }
    }

    @Override
    public void disablePlugin(IPlugin plugin) {
        if (plugin == null || !plugins.containsKey(plugin.getId()) || !plugin.isEnabled()) {
            return;
        }

        try {
            plugin.disable();
            notifyListeners(plugin, PluginState.DISABLED);
            LogManager.getInstance().info("Disabled plugin: " + plugin.getId());
        } catch (Exception e) {
            LogManager.getInstance().error("Failed to disable plugin: " + plugin.getId(), e);
        }
    }

    @Override
    public IPlugin reloadPlugin(IPlugin plugin) throws PluginException {
        if (plugin == null || !plugins.containsKey(plugin.getId())) {
            return null;
        }

        try {
            String pluginFile = plugin.getDataFolder() + File.separator + plugin.getId() + ".jar";
            unloadPlugin(plugin);
            return loadPlugin(pluginFile);
        } catch (Exception e) {
            LogManager.getInstance().error("Failed to reload plugin: " + plugin.getId(), e);
            throw new PluginException("Failed to reload plugin: " + e.getMessage(), e);
        }
    }

    @Override
    public List<IPlugin> getPlugins() {
        return new ArrayList<>(plugins.values());
    }

    @Override
    public IPlugin getPlugin(String pluginId) {
        return plugins.get(pluginId);
    }

    @Override
    public boolean isPluginLoaded(String pluginId) {
        return plugins.containsKey(pluginId);
    }

    @Override
    public boolean checkDependencies(IPlugin plugin) {
        for (PluginDependency dependency : plugin.getDependencies()) {
            String dependencyId = dependency.getPluginId();
            IPlugin dependencyPlugin = plugins.get(dependencyId);
            
            if (dependencyPlugin == null) {
                if (dependency.isRequired()) {
                    LogManager.getInstance().error("Missing required dependency " + dependencyId + 
                                                " for plugin " + plugin.getId());
                    return false;
                }
                continue;
            }

            if (!dependency.isVersionCompatible(dependencyPlugin.getVersion())) {
                LogManager.getInstance().error("Incompatible dependency version " + dependencyId + 
                                            " for plugin " + plugin.getId());
                return false;
            }
        }
        return true;
    }

    @Override
    public PluginDependencyGraph getDependencyGraph() {
        return dependencyGraph;
    }

    @Override
    public void addPluginListener(IPluginListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void removePluginListener(IPluginListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(IPlugin plugin, PluginState newState) {
        for (IPluginListener listener : listeners) {
            try {
                switch (newState) {
                    case LOADED:
                        listener.onPluginLoaded(plugin);
                        break;
                    case UNLOADED:
                        listener.onPluginUnloaded(plugin);
                        break;
                    case ENABLED:
                        listener.onPluginEnabled(plugin);
                        break;
                    case DISABLED:
                        listener.onPluginDisabled(plugin);
                        break;
                }
            } catch (Exception e) {
                LogManager.getInstance().error("Error notifying plugin listener", e);
            }
        }
    }

    @Override
    public IPluginLoader getPluginLoader() {
        return pluginLoader;
    }

    @Override
    public void setPluginLoader(IPluginLoader loader) {
        if (loader != null) {
            this.pluginLoader = loader;
            // 重新加载插件
            plugins.clear();
            loadPlugins();
        }
    }

    @Override
    public IPluginRepository getPluginRepository() {
        return pluginRepository;
    }

    @Override
    public void setPluginRepository(IPluginRepository repository) {
        this.pluginRepository = repository;
    }

    /**
     * 获取当前激活的插件
     */
    public IPlugin getActivePlugin() {
        return activePlugin;
    }

    /**
     * 设置当前激活的插件
     */
    public void setActivePlugin(IPlugin plugin) {
        if (activePlugin != null) {
            activePlugin.onDeactivate();
        }
        activePlugin = plugin;
        if (activePlugin != null) {
            activePlugin.onActivate();
        }
    }

    /**
     * 卸载所有插件
     */
    public void unloadAll() {
        setActivePlugin(null);
        for (IPlugin plugin : plugins.values()) {
            try {
                if (plugin.isEnabled()) {
                    plugin.disable();
                }
                pluginLoader.unloadPlugin(plugin);
            } catch (Exception e) {
                LogManager.getInstance().error("Failed to unload plugin: " + plugin.getId(), e);
            }
        }
        plugins.clear();
    }
}
