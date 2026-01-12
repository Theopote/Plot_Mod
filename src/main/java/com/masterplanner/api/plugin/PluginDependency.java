package com.masterplanner.api.plugin;

/**
 * 插件依赖类
 */
public class PluginDependency {
    private final String pluginId;
    private final String version;
    private final boolean required;

    public PluginDependency(String pluginId, String version, boolean required) {
        this.pluginId = pluginId;
        this.version = version;
        this.required = required;
    }

    /**
     * 获取插件ID
     * @return 插件ID
     */
    public String getPluginId() {
        return pluginId;
    }

    /**
     * 获取版本要求
     * @return 版本要求
     */
    public String getVersion() {
        return version;
    }

    /**
     * 是否为必需依赖
     * @return 是否必需
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * 检查版本是否兼容
     * @param targetVersion 目标版本
     * @return 是否兼容
     */
    public boolean isVersionCompatible(String targetVersion) {
        // TODO: 实现版本兼容性检查逻辑
        return true;
    }

    @Override
    public String toString() {
        return String.format("%s@%s%s", pluginId, version, required ? " (required)" : "");
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PluginDependency that = (PluginDependency) obj;
        return required == that.required &&
               pluginId.equals(that.pluginId) &&
               version.equals(that.version);
    }

    @Override
    public int hashCode() {
        int result = pluginId.hashCode();
        result = 31 * result + version.hashCode();
        result = 31 * result + (required ? 1 : 0);
        return result;
    }
}
