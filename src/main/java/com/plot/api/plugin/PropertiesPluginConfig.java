package com.plot.api.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 基于 Properties 的轻量插件配置实现，位于 API 层以避免依赖 core 实现。
 */
public class PropertiesPluginConfig implements IPluginConfig {
    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final String author;
    private final String[] dependencies;
    private final Map<String, Object> parameters = new HashMap<>();

    public PropertiesPluginConfig(Properties props) {
        this.id = value(props, "plugin-id");
        this.name = value(props, "plugin-name");
        this.version = value(props, "plugin-version");
        this.description = value(props, "description");
        this.author = value(props, "author");
        this.dependencies = parseDependencies(value(props, "dependencies"));

        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("config.")) {
                parameters.put(key.substring("config.".length()), props.getProperty(key));
            }
        }
    }

    private static String value(Properties props, String key) {
        return props.getProperty(key, "").trim();
    }

    private static String[] parseDependencies(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[0];
        }
        return raw.lines()
                .flatMap(line -> java.util.Arrays.stream(line.split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(dep -> dep.split(":")[0].trim())
                .toArray(String[]::new);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getAuthor() {
        return author;
    }

    @Override
    public String[] getDependencies() {
        return dependencies.clone();
    }

    @Override
    public Map<String, Object> getParameters() {
        return new HashMap<>(parameters);
    }

    @Override
    public Object getParameter(String key) {
        return parameters.get(key);
    }

    @Override
    public void setParameter(String key, Object value) {
        parameters.put(key, value);
    }

    @Override
    public boolean validate() {
        return !id.isEmpty() && !name.isEmpty() && !version.isEmpty();
    }
}
