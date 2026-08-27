package com.plot.api.world;

/**
 * 世界修改前置检查结果（创造模式 / 权限等）。
 * 位于 api，避免插件/PluginContext 依赖 client 侧具体实现。
 */
public record PlacementReadiness(boolean ready, String message) {
    public static PlacementReadiness ok() {
        return new PlacementReadiness(true, "");
    }

    public static PlacementReadiness fail(String message) {
        return new PlacementReadiness(false, message != null ? message : "");
    }
}
