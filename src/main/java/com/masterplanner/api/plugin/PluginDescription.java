package com.masterplanner.api.plugin;

import com.masterplanner.core.plugin.PluginConfig;

import java.util.Properties;
import java.util.ArrayList;
import java.util.List;

/**
 * 插件描述信息
 */
public class PluginDescription implements IPluginDescription {
    private final String id;
    private final String name;
    private final String version;
    private final String mainClass;
    private final String description;
    private final String author;
    private final String website;
    private final List<PluginDependency> dependencies;
    private final IPluginConfig config;

    public PluginDescription(Properties props) throws PluginException {
        this.id = getRequiredProperty(props, "plugin-id");
        this.name = getRequiredProperty(props, "plugin-name");
        this.version = getRequiredProperty(props, "plugin-version");
        this.mainClass = getRequiredProperty(props, "main-class");
        this.description = props.getProperty("description", "");
        this.author = props.getProperty("author", "");
        this.website = props.getProperty("website", "");
        this.dependencies = parseDependencies(props.getProperty("dependencies", ""));
        this.config = new PluginConfig(props);
    }

    private String getRequiredProperty(Properties props, String key) throws PluginException {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new PluginException("Missing required property: " + key);
        }
        return value.trim();
    }

    private List<PluginDependency> parseDependencies(String depsStr) {
        List<PluginDependency> deps = new ArrayList<>();
        if (depsStr == null || depsStr.trim().isEmpty()) {
            return deps;
        }

        for (String dep : depsStr.split(",")) {
            String[] parts = dep.trim().split(":");
            if (parts.length >= 1) {
                String pluginId = parts[0].trim();
                String version = parts.length > 1 ? parts[1].trim() : "";
                boolean required = parts.length <= 2 || Boolean.parseBoolean(parts[2].trim());
                deps.add(new PluginDependency(pluginId, version, required));
            }
        }
        return deps;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getMainClass() { return mainClass; }
    public String getDescription() { return description; }
    public String getAuthor() { return author; }
    public String getWebsite() { return website; }
    public List<PluginDependency> getDependencies() { return dependencies; }
    public IPluginConfig getConfig() { return config; }

    @Override
    public String toString() {
        return String.format("PluginDescription[id=%s, name=%s, version=%s]", id, name, version);
    }
} 