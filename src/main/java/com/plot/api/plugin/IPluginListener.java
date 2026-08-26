package com.plot.api.plugin;

/**
 * 插件生命周期事件监听器。方法均有默认空实现，按需覆盖。
 */
public interface IPluginListener {
    default void onPluginLoaded(IPlugin plugin) {
    }

    default void onPluginUnloaded(IPlugin plugin) {
    }

    default void onPluginEnabled(IPlugin plugin) {
    }

    default void onPluginDisabled(IPlugin plugin) {
    }

    default void onPluginStateChange(IPlugin plugin, PluginState oldState, PluginState newState) {
    }

    default void onPluginMissingDependency(IPlugin plugin, PluginDependency dependency) {
    }

    default void onPluginIncompatibleVersion(IPlugin plugin, String requiredVersion, String currentVersion) {
    }

    default void onPluginLoadError(String pluginFile, Throwable error) {
    }
}
