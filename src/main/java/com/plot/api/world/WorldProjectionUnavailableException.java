package com.plot.api.world;

/**
 * Canvas 几何无法投影到 Minecraft 世界坐标时抛出。
 * <p>
 * 插件生成/预览应 fail-fast，不得回退为 canvas distance。
 */
public class WorldProjectionUnavailableException extends IllegalStateException {
    public WorldProjectionUnavailableException(String message) {
        super(message);
    }
}
