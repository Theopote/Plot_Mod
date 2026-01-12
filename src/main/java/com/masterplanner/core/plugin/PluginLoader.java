package com.masterplanner.core.plugin;

import com.masterplanner.api.plugin.*;
import com.masterplanner.core.log.LogManager;

import java.io.File;
import java.nio.file.Path;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 插件加载器
 */
public class PluginLoader implements IPluginLoader {
    private final Path pluginsDirectory;
    private final Map<String, IPlugin> loadedPlugins;
    private final Map<String, ClassLoader> pluginClassLoaders;

    public PluginLoader(Path pluginsDirectory) {
        this.pluginsDirectory = pluginsDirectory;
        this.loadedPlugins = new HashMap<>();
        this.pluginClassLoaders = new HashMap<>();
    }

    @Override
    public Path getPluginsDirectory() {
        return pluginsDirectory;
    }

    @Override
    public boolean isPluginFile(String filePath) {
        return filePath != null && filePath.toLowerCase().endsWith(".jar");
    }

    @Override
    public IPlugin loadPlugin(String pluginPath) throws PluginException {
        try {
            File pluginFile = new File(pluginPath);
            if (!pluginFile.exists()) {
                throw new PluginException("Plugin file does not exist: " + pluginPath);
            }

            // 读取插件描述
            IPluginDescription description = getPluginDescription(pluginPath);
            if (description == null) {
                throw new PluginException("Failed to load plugin description from: " + pluginPath);
            }

            // 创建类加载器
            URLClassLoader classLoader = new URLClassLoader(
                new URL[]{pluginFile.toURI().toURL()},
                getClass().getClassLoader()
            );

            // 加载主类
            Class<?> mainClass = classLoader.loadClass(description.getMainClass());
            if (!IPlugin.class.isAssignableFrom(mainClass)) {
                throw new PluginException("Plugin class does not implement IPlugin: " + mainClass.getName());
            }

            // 创建插件实例
            IPlugin plugin = (IPlugin) mainClass.getDeclaredConstructor().newInstance();
            plugin.setConfig(description.getConfig());

            // 保存类加载器和插件实例
            pluginClassLoaders.put(plugin.getId(), classLoader);
            loadedPlugins.put(plugin.getId(), plugin);

            return plugin;

        } catch (Exception e) {
            throw new PluginException("Failed to load plugin: " + pluginPath, e);
        }
    }

    @Override
    public IPluginDescription getPluginDescription(String pluginFile) throws PluginException {
        try (JarFile jar = new JarFile(new File(pluginFile))) {
            JarEntry entry = jar.getJarEntry("plugin.properties");
            if (entry == null) {
                throw new PluginException("Missing plugin.properties in: " + pluginFile);
            }

            Properties props = new Properties();
            props.load(jar.getInputStream(entry));
            return new PluginDescription(props);

        } catch (IOException e) {
            throw new PluginException("Failed to read plugin description: " + pluginFile, e);
        }
    }

    @Override
    public void unloadPlugin(IPlugin plugin) throws PluginException {
        if (plugin == null) return;

        try {
            // 执行插件的卸载逻辑
            plugin.unload();

            // 关闭类加载器
            ClassLoader classLoader = pluginClassLoaders.remove(plugin.getId());
            if (classLoader != null) {
                ((URLClassLoader) classLoader).close();
            }

            // 移除插件实例
            loadedPlugins.remove(plugin.getId());

        } catch (Exception e) {
            throw new PluginException("Failed to unload plugin: " + plugin.getId(), e);
        }
    }

    //@Override
    public void cleanupPlugin(IPlugin plugin) throws PluginException {
        try {
            plugin.unload();
        } catch (Exception e) {
            throw new PluginException("Failed to cleanup plugin: " + plugin.getId(), e);
        }
    }

    @Override
    public ClassLoader getPluginClassLoader(IPlugin plugin) {
        return pluginClassLoaders.get(plugin.getId());
    }

    @Override
    public List<IPlugin> getLoadedPlugins() {
        return new ArrayList<>(loadedPlugins.values());
    }

    @Override
    public void loadPlugins() {
        if (!pluginsDirectory.toFile().exists() || !pluginsDirectory.toFile().isDirectory()) {
            return;
        }

        File[] files = pluginsDirectory.toFile().listFiles((dir, name) -> name.endsWith(".jar"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            try {
                IPlugin plugin = loadPlugin(file.getPath());
                if (plugin != null) {
                    loadedPlugins.put(plugin.getId(), plugin);
                }
            } catch (Exception e) {
                LogManager.getInstance().error("Failed to load plugin: " + file.getName(), e);
            }
        }
    }

    @Override
    public void enablePlugin(String pluginId) {
        IPlugin plugin = loadedPlugins.get(pluginId);
        if (plugin != null && !plugin.isEnabled()) {
            try {
                plugin.enable();
                LogManager.getInstance().info("Enabled plugin: " + pluginId);
            } catch (Exception e) {
                LogManager.getInstance().error("Failed to enable plugin: " + pluginId, e);
            }
        }
    }

    @Override
    public void disablePlugin(String pluginId) {
        IPlugin plugin = loadedPlugins.get(pluginId);
        if (plugin != null && plugin.isEnabled()) {
            try {
                plugin.disable();
                LogManager.getInstance().info("Disabled plugin: " + pluginId);
            } catch (Exception e) {
                LogManager.getInstance().error("Failed to disable plugin: " + pluginId, e);
            }
        }
    }
}
