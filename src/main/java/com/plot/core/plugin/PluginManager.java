package com.plot.core.plugin;

import com.plot.api.plugin.*;
import com.plot.core.log.LogManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 插件管理器。内置与外部插件共用同一生命周期管线：
 * <pre>
 * DISCOVERED → LOADED → INITIALIZED → ENABLED → ACTIVE
 *   ⇄ INACTIVE → DISABLED → UNLOADED → DISPOSED
 * </pre>
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

        try {
            Path pluginsPath = FabricLoader.getInstance()
                .getGameDir()
                .resolve("plot")
                .resolve("plugins");

            Files.createDirectories(pluginsPath);
            this.pluginLoader = new PluginLoader(pluginsPath);

            registerBuiltinPlugins();
            loadExternalPlugins();
        } catch (Exception e) {
            LogManager.getInstance().error("Failed to initialize plugin manager", e);
        }
    }

    public static PluginManager getInstance() {
        return INSTANCE;
    }

    private void registerBuiltinPlugins() {
        try {
            installPlugin(new com.plot.plugin.EarthworkPlugin(), true);
            installPlugin(new com.plot.plugin.RoadSystemPlugin(), true);
            installPlugin(new com.plot.plugin.BuildingPlugin(), true);
            LogManager.getInstance().info("Registered {} plugins after builtins", plugins.size());
        } catch (Exception e) {
            LogManager.getInstance().error("Failed to register builtin plugins", e);
        }
    }

    /**
     * 统一安装路径：依赖图 → 依赖检查 → initialize →（可选）enable。
     * 内置与外部插件都必须走此方法，禁止旁路。
     */
    private IPlugin installPlugin(IPlugin plugin, boolean enableAfterInit) throws PluginException {
        if (plugin == null) {
            throw new PluginException("Cannot install null plugin");
        }

        String id = plugin.getId();
        if (plugins.containsKey(id)) {
            LogManager.getInstance().warn("Plugin already installed: {}", id);
            return plugins.get(id);
        }

        PluginState previous = plugin.getState();
        plugin.transitionState(PluginState.LOADING);
        notifyStateChange(plugin, previous, PluginState.LOADING);

        // LOADED：纳入注册表与依赖图
        dependencyGraph.addPlugin(plugin);
        plugins.put(id, plugin);
        transitionAndNotify(plugin, PluginState.LOADED);

        if (dependencyGraph.hasCircularDependencies()) {
            rollbackInstall(plugin, PluginState.FAILED);
            throw new PluginException("Circular dependency detected for plugin: " + id);
        }

        if (!checkDependencies(plugin)) {
            transitionAndNotify(plugin, PluginState.MISSING_DEPENDENCIES);
            notifyMissingDependencies(plugin);
            // 仍保留在注册表中，但不初始化/启用
            return plugin;
        }

        try {
            PluginState beforeInit = plugin.getState();
            plugin.initialize();
            // initialize() 可能已写入 INITIALIZED；仍保证监听器收到变更
            if (plugin.getState() != PluginState.INITIALIZED) {
                plugin.transitionState(PluginState.INITIALIZED);
            }
            notifyStateChange(plugin, beforeInit, PluginState.INITIALIZED);
            notifyListenersLoaded(plugin);
        } catch (Exception e) {
            rollbackInstall(plugin, PluginState.FAILED);
            throw new PluginException("Failed to initialize plugin: " + id, e);
        }

        if (enableAfterInit) {
            enablePlugin(plugin);
        }

        LogManager.getInstance().info("Installed plugin: {} (state={})", id, plugin.getState());
        return plugin;
    }

    private void rollbackInstall(IPlugin plugin, PluginState failureState) {
        String id = plugin.getId();
        try {
            if (plugin.isEnabled()) {
                plugin.disable();
            }
        } catch (Exception ignored) {
            // best-effort
        }
        try {
            plugin.unload();
        } catch (Exception ignored) {
            // best-effort
        }
        plugins.remove(id);
        dependencyGraph.removePlugin(id);
        try {
            if (pluginLoader != null) {
                pluginLoader.unloadPlugin(plugin);
            }
        } catch (Exception ignored) {
            // best-effort
        }
        transitionAndNotify(plugin, failureState);
        try {
            plugin.dispose();
            transitionAndNotify(plugin, PluginState.DISPOSED);
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private void loadExternalPlugins() {
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

        List<IPlugin> discovered = new ArrayList<>();
        for (File file : Objects.requireNonNull(pluginsDir.listFiles())) {
            if (!pluginLoader.isPluginFile(file.getPath())) {
                continue;
            }
            try {
                IPlugin plugin = pluginLoader.loadPlugin(file.getPath());
                if (plugin != null) {
                    plugin.transitionState(PluginState.DISCOVERED);
                    discovered.add(plugin);
                }
            } catch (Exception e) {
                LogManager.getInstance().error("Failed to discover plugin: " + file.getName(), e);
                for (IPluginListener listener : listeners) {
                    try {
                        listener.onPluginLoadError(file.getPath(), e);
                    } catch (Exception notifyError) {
                        LogManager.getInstance().error("Error notifying plugin listener", notifyError);
                    }
                }
            }
        }

        // 先全部纳入依赖图再按拓扑顺序 initialize/enable，保证依赖检查可用
        List<IPlugin> pendingInit = new ArrayList<>();
        for (IPlugin plugin : discovered) {
            if (plugins.containsKey(plugin.getId())) {
                LogManager.getInstance().warn("Skipping external plugin already installed: {}", plugin.getId());
                continue;
            }
            try {
                dependencyGraph.addPlugin(plugin);
                plugins.put(plugin.getId(), plugin);
                transitionAndNotify(plugin, PluginState.LOADED);
                pendingInit.add(plugin);
            } catch (Exception e) {
                LogManager.getInstance().error("Failed to register discovered plugin: " + plugin.getId(), e);
            }
        }

        if (dependencyGraph.hasCircularDependencies()) {
            LogManager.getInstance().error("Circular dependency among external plugins; skipping enable");
            return;
        }

        List<IPlugin> order = dependencyGraph.getLoadOrder();
        for (IPlugin plugin : order) {
            if (!pendingInit.contains(plugin)) {
                continue;
            }
            try {
                if (!checkDependencies(plugin)) {
                    transitionAndNotify(plugin, PluginState.MISSING_DEPENDENCIES);
                    notifyMissingDependencies(plugin);
                    continue;
                }
                PluginState beforeInit = plugin.getState();
                plugin.initialize();
                if (plugin.getState() != PluginState.INITIALIZED) {
                    plugin.transitionState(PluginState.INITIALIZED);
                }
                notifyStateChange(plugin, beforeInit, PluginState.INITIALIZED);
                notifyListenersLoaded(plugin);
                enablePlugin(plugin);
            } catch (Exception e) {
                LogManager.getInstance().error("Failed to activate external plugin: " + plugin.getId(), e);
                transitionAndNotify(plugin, PluginState.FAILED);
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

            IPlugin plugin = pluginLoader.loadPlugin(pluginFile);
            if (plugin == null) {
                throw new PluginException("Failed to load plugin from file: " + pluginFile);
            }

            plugin.transitionState(PluginState.DISCOVERED);
            return installPlugin(plugin, true);
        } catch (Exception e) {
            LogManager.getInstance().error("Failed to load plugin: " + pluginFile, e);
            for (IPluginListener listener : listeners) {
                try {
                    listener.onPluginLoadError(pluginFile, e);
                } catch (Exception notifyError) {
                    LogManager.getInstance().error("Error notifying plugin listener", notifyError);
                }
            }
            return null;
        }
    }

    @Override
    public void unloadPlugin(IPlugin plugin) throws PluginException {
        if (plugin == null || !plugins.containsKey(plugin.getId())) {
            return;
        }

        Set<String> dependents = dependencyGraph.getDependents(plugin.getId());
        if (!dependents.isEmpty()) {
            throw new PluginException("Cannot unload plugin " + plugin.getId()
                + " because it is required by: " + String.join(", ", dependents));
        }

        teardownPlugin(plugin, true);
    }

    /**
     * 完整镜像加载过程的 teardown：
     * ACTIVE/INACTIVE → disable → unload → graph remove → loader unload → notify → dispose
     */
    private void teardownPlugin(IPlugin plugin, boolean notify) throws PluginException {
        String id = plugin.getId();
        try {
            if (activePlugin != null && activePlugin.getId().equals(id)) {
                deactivateActivePlugin();
            }

            transitionAndNotify(plugin, PluginState.UNLOADING);

            if (plugin.isEnabled()) {
                plugin.disable();
                if (notify) {
                    notifyListenersDisabled(plugin);
                }
                transitionAndNotify(plugin, PluginState.DISABLED);
            }

            plugin.unload();
            plugins.remove(id);
            dependencyGraph.removePlugin(id);

            if (pluginLoader != null) {
                pluginLoader.unloadPlugin(plugin);
            }

            transitionAndNotify(plugin, PluginState.UNLOADED);
            if (notify) {
                notifyListenersUnloaded(plugin);
            }

            plugin.dispose();
            transitionAndNotify(plugin, PluginState.DISPOSED);

            LogManager.getInstance().info("Unloaded plugin: {}", id);
        } catch (PluginException e) {
            transitionAndNotify(plugin, PluginState.FAILED);
            LogManager.getInstance().error("Failed to unload plugin: " + id, e);
            throw e;
        } catch (Exception e) {
            transitionAndNotify(plugin, PluginState.FAILED);
            LogManager.getInstance().error("Failed to unload plugin: " + id, e);
            throw new PluginException("Failed to unload plugin: " + e.getMessage(), e);
        }
    }

    @Override
    public void enablePlugin(IPlugin plugin) {
        if (plugin == null || !plugins.containsKey(plugin.getId()) || plugin.isEnabled()) {
            return;
        }

        PluginState state = plugin.getState();
        if (state == PluginState.MISSING_DEPENDENCIES
            || state == PluginState.FAILED
            || state == PluginState.DISPOSED
            || state == PluginState.UNLOADED) {
            LogManager.getInstance().warn("Cannot enable plugin {} in state {}", plugin.getId(), state);
            return;
        }

        if (!checkDependencies(plugin)) {
            transitionAndNotify(plugin, PluginState.MISSING_DEPENDENCIES);
            notifyMissingDependencies(plugin);
            return;
        }

        try {
            PluginState beforeEnable = plugin.getState();
            plugin.enable();
            if (plugin.getState() != PluginState.ENABLED) {
                plugin.transitionState(PluginState.ENABLED);
            }
            notifyStateChange(plugin, beforeEnable, PluginState.ENABLED);
            notifyListenersEnabled(plugin);
            LogManager.getInstance().info("Enabled plugin: {}", plugin.getId());
        } catch (Exception e) {
            transitionAndNotify(plugin, PluginState.FAILED);
            LogManager.getInstance().error("Failed to enable plugin: " + plugin.getId(), e);
        }
    }

    @Override
    public void disablePlugin(IPlugin plugin) {
        if (plugin == null || !plugins.containsKey(plugin.getId()) || !plugin.isEnabled()) {
            return;
        }

        try {
            if (activePlugin != null && activePlugin.getId().equals(plugin.getId())) {
                deactivateActivePlugin();
            }
            PluginState beforeDisable = plugin.getState();
            plugin.disable();
            if (plugin.getState() != PluginState.DISABLED) {
                plugin.transitionState(PluginState.DISABLED);
            }
            notifyStateChange(plugin, beforeDisable, PluginState.DISABLED);
            notifyListenersDisabled(plugin);
            LogManager.getInstance().info("Disabled plugin: {}", plugin.getId());
        } catch (Exception e) {
            transitionAndNotify(plugin, PluginState.FAILED);
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
                    LogManager.getInstance().error("Missing required dependency {} for plugin {}",
                        dependencyId, plugin.getId());
                    return false;
                }
                continue;
            }

            if (!dependency.isVersionCompatible(dependencyPlugin.getVersion())) {
                LogManager.getInstance().error("Incompatible dependency version {} for plugin {}",
                    dependencyId, plugin.getId());
                for (IPluginListener listener : listeners) {
                    try {
                        listener.onPluginIncompatibleVersion(
                            plugin, dependency.getVersion(), dependencyPlugin.getVersion());
                    } catch (Exception e) {
                        LogManager.getInstance().error("Error notifying plugin listener", e);
                    }
                }
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

    private void transitionAndNotify(IPlugin plugin, PluginState newState) {
        PluginState oldState = plugin.getState();
        if (oldState == newState) {
            return;
        }
        plugin.transitionState(newState);
        notifyStateChange(plugin, oldState, newState);
    }

    private void notifyStateChange(IPlugin plugin, PluginState oldState, PluginState newState) {
        for (IPluginListener listener : listeners) {
            try {
                listener.onPluginStateChange(plugin, oldState, newState);
            } catch (Exception e) {
                LogManager.getInstance().error("Error notifying plugin listener", e);
            }
        }
    }

    private void notifyListenersLoaded(IPlugin plugin) {
        for (IPluginListener listener : listeners) {
            try {
                listener.onPluginLoaded(plugin);
            } catch (Exception e) {
                LogManager.getInstance().error("Error notifying plugin listener", e);
            }
        }
    }

    private void notifyListenersUnloaded(IPlugin plugin) {
        for (IPluginListener listener : listeners) {
            try {
                listener.onPluginUnloaded(plugin);
            } catch (Exception e) {
                LogManager.getInstance().error("Error notifying plugin listener", e);
            }
        }
    }

    private void notifyListenersEnabled(IPlugin plugin) {
        for (IPluginListener listener : listeners) {
            try {
                listener.onPluginEnabled(plugin);
            } catch (Exception e) {
                LogManager.getInstance().error("Error notifying plugin listener", e);
            }
        }
    }

    private void notifyListenersDisabled(IPlugin plugin) {
        for (IPluginListener listener : listeners) {
            try {
                listener.onPluginDisabled(plugin);
            } catch (Exception e) {
                LogManager.getInstance().error("Error notifying plugin listener", e);
            }
        }
    }

    private void notifyMissingDependencies(IPlugin plugin) {
        for (PluginDependency dependency : plugin.getDependencies()) {
            if (plugins.get(dependency.getPluginId()) == null && dependency.isRequired()) {
                for (IPluginListener listener : listeners) {
                    try {
                        listener.onPluginMissingDependency(plugin, dependency);
                    } catch (Exception e) {
                        LogManager.getInstance().error("Error notifying plugin listener", e);
                    }
                }
            }
        }
    }

    @Override
    public IPluginLoader getPluginLoader() {
        return pluginLoader;
    }

    @Override
    public void setPluginLoader(IPluginLoader loader) {
        if (loader == null) {
            return;
        }
        // 必须先完整 teardown，再换 loader 并重新走统一生命周期
        unloadAll();
        this.pluginLoader = loader;
        registerBuiltinPlugins();
        loadExternalPlugins();
    }

    @Override
    public IPluginRepository getPluginRepository() {
        return pluginRepository;
    }

    @Override
    public void setPluginRepository(IPluginRepository repository) {
        this.pluginRepository = repository;
    }

    public IPlugin getActivePlugin() {
        return activePlugin;
    }

    public void setActivePlugin(IPlugin plugin) {
        if (activePlugin == plugin) {
            return;
        }
        deactivateActivePlugin();
        activePlugin = plugin;
        if (activePlugin != null) {
            PluginState oldState = activePlugin.getState();
            activePlugin.onActivate();
            if (activePlugin.isEnabled()) {
                activePlugin.transitionState(PluginState.ACTIVE);
                notifyStateChange(activePlugin, oldState, PluginState.ACTIVE);
            }
        }
    }

    private void deactivateActivePlugin() {
        if (activePlugin == null) {
            return;
        }
        IPlugin previous = activePlugin;
        activePlugin = null;
        PluginState oldState = previous.getState();
        previous.onDeactivate();
        if (previous.isEnabled()) {
            previous.transitionState(PluginState.INACTIVE);
            notifyStateChange(previous, oldState, PluginState.INACTIVE);
        }
    }

    /**
     * 卸载全部插件：按依赖逆序完整 teardown（disable → unload → graph → loader → dispose）。
     */
    public void unloadAll() {
        deactivateActivePlugin();

        List<IPlugin> order = new ArrayList<>(dependencyGraph.getLoadOrder());
        Collections.reverse(order);

        // 图中可能缺漏的也一并处理
        for (IPlugin plugin : plugins.values()) {
            if (!order.contains(plugin)) {
                order.add(plugin);
            }
        }

        for (IPlugin plugin : order) {
            if (!plugins.containsKey(plugin.getId())) {
                continue;
            }
            try {
                teardownPlugin(plugin, true);
            } catch (Exception e) {
                LogManager.getInstance().error("Failed to unload plugin during unloadAll: " + plugin.getId(), e);
                plugins.remove(plugin.getId());
                dependencyGraph.removePlugin(plugin.getId());
            }
        }

        plugins.clear();
        dependencyGraph.clear();
        activePlugin = null;
    }
}
