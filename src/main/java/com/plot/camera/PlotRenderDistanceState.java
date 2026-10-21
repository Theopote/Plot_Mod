package com.plot.camera;

/**
 * Plot 临时渲染距离状态（无客户端依赖，供集成服 mixin 读取）。
 * <p>
 * 上限必须为原版 32：再高会撑破 {@code LevelPropagator} 内部表，导致
 * {@code ArrayIndexOutOfBoundsException} 崩服。
 */
public final class PlotRenderDistanceState {
    /** 与原版 {@code ServerChunkLoadingManager.setViewDistance} 硬顶一致 */
    public static final int MAX_CHUNKS = 32;

    private static volatile int overrideChunks = -1;

    private PlotRenderDistanceState() {
    }

    public static int getOverrideChunks() {
        return overrideChunks;
    }

    public static void setOverrideChunks(int chunks) {
        if (chunks > 0) {
            chunks = Math.min(MAX_CHUNKS, chunks);
        }
        overrideChunks = chunks;
    }

    public static void clear() {
        overrideChunks = -1;
    }
}
