package com.masterplanner.core.plugin;

import com.masterplanner.api.plugin.IPlugin;
import com.masterplanner.api.plugin.PluginDependency;

import java.util.*;

/**
 * 插件依赖图
 */
public class PluginDependencyGraph {
    private final Map<String, Set<String>> dependencies;
    private final Map<String, Set<String>> dependents;
    private final Map<String, IPlugin> plugins;

    public PluginDependencyGraph() {
        this.dependencies = new HashMap<>();
        this.dependents = new HashMap<>();
        this.plugins = new HashMap<>();
    }

    /**
     * 添加插件到依赖图
     * @param plugin 要添加的插件
     */
    public void addPlugin(IPlugin plugin) {
        String pluginId = plugin.getId();
        plugins.put(pluginId, plugin);
        
        // 初始化依赖集合
        dependencies.putIfAbsent(pluginId, new HashSet<>());
        dependents.putIfAbsent(pluginId, new HashSet<>());
        
        // 添加依赖关系
        for (PluginDependency dependency : plugin.getDependencies()) {
            String dependencyId = dependency.getPluginId();
            dependencies.get(pluginId).add(dependencyId);
            dependents.computeIfAbsent(dependencyId, k -> new HashSet<>()).add(pluginId);
        }
    }

    /**
     * 移除插件
     * @param pluginId 要移除的插件ID
     */
    public void removePlugin(String pluginId) {
        plugins.remove(pluginId);
        
        // 移除依赖关系
        Set<String> deps = dependencies.remove(pluginId);
        if (deps != null) {
            for (String dep : deps) {
                dependents.getOrDefault(dep, Collections.emptySet()).remove(pluginId);
            }
        }
        
        // 移除被依赖关系
        Set<String> deps2 = dependents.remove(pluginId);
        if (deps2 != null) {
            for (String dep : deps2) {
                dependencies.getOrDefault(dep, Collections.emptySet()).remove(pluginId);
            }
        }
    }

    /**
     * 获取插件的直接依赖
     * @param pluginId 插件ID
     * @return 依赖集合
     */
    public Set<String> getDependencies(String pluginId) {
        return new HashSet<>(dependencies.getOrDefault(pluginId, Collections.emptySet()));
    }

    /**
     * 获取依赖该插件的插件
     * @param pluginId 插件ID
     * @return 依赖集合
     */
    public Set<String> getDependents(String pluginId) {
        return new HashSet<>(dependents.getOrDefault(pluginId, Collections.emptySet()));
    }

    /**
     * 检查是否存在循环依赖
     * @return 是否存在循环依赖
     */
    public boolean hasCircularDependencies() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        
        for (String pluginId : plugins.keySet()) {
            if (hasCircularDependencies(pluginId, visited, recursionStack)) {
                return true;
            }
        }
        
        return false;
    }

    private boolean hasCircularDependencies(String pluginId, Set<String> visited, Set<String> recursionStack) {
        if (!visited.contains(pluginId)) {
            visited.add(pluginId);
            recursionStack.add(pluginId);
            
            for (String dependency : dependencies.getOrDefault(pluginId, Collections.emptySet())) {
                if (!visited.contains(dependency) && hasCircularDependencies(dependency, visited, recursionStack)) {
                    return true;
                } else if (recursionStack.contains(dependency)) {
                    return true;
                }
            }
        }
        recursionStack.remove(pluginId);
        return false;
    }

    /**
     * 获取加载顺序
     * @return 插件加载顺序列表
     */
    public List<IPlugin> getLoadOrder() {
        List<IPlugin> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        // 使用拓扑排序获取加载顺序
        for (String pluginId : plugins.keySet()) {
            if (!visited.contains(pluginId)) {
                topologicalSort(pluginId, visited, order);
            }
        }
        
        return order;
    }

    private void topologicalSort(String pluginId, Set<String> visited, List<IPlugin> order) {
        visited.add(pluginId);
        
        for (String dependency : dependencies.getOrDefault(pluginId, Collections.emptySet())) {
            if (!visited.contains(dependency)) {
                topologicalSort(dependency, visited, order);
            }
        }
        
        IPlugin plugin = plugins.get(pluginId);
        if (plugin != null) {
            order.add(plugin);
        }
    }

    /**
     * 获取所有插件
     * @return 插件映射
     */
    public Map<String, IPlugin> getPlugins() {
        return new HashMap<>(plugins);
    }
}
