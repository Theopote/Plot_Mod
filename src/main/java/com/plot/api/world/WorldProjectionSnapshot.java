package com.plot.api.world;

/**
 * 当前画布 → Minecraft 投影快照。
 * <p>
 * 预览/生成应捕获一次并在同一次操作中复用，视图范围或相机变化后须失效。
 */
public record WorldProjectionSnapshot(
        WorldViewBounds worldBounds,
        float viewDistance,
        float viewScale,
        float canvasWidth,
        float canvasHeight) {

    public static final WorldProjectionSnapshot UNKNOWN =
        new WorldProjectionSnapshot(null, 0f, 0f, 0f, 0f);

    /** 投影可用于插件生成/预览。 */
    public boolean isValid() {
        return worldBounds != null
            && canvasWidth > 0f
            && canvasHeight > 0f
            && viewDistance > 0f;
    }

    public int fingerprint() {
        int hash = 1;
        if (worldBounds != null) {
            hash = 31 * hash + Double.hashCode(worldBounds.minX());
            hash = 31 * hash + Double.hashCode(worldBounds.maxX());
            hash = 31 * hash + Double.hashCode(worldBounds.minZ());
            hash = 31 * hash + Double.hashCode(worldBounds.maxZ());
        }
        hash = 31 * hash + Float.hashCode(viewDistance);
        hash = 31 * hash + Float.hashCode(viewScale);
        hash = 31 * hash + Float.hashCode(canvasWidth);
        hash = 31 * hash + Float.hashCode(canvasHeight);
        return hash;
    }

    public static WorldProjectionSnapshot fromBounds(WorldViewBounds bounds) {
        return new WorldProjectionSnapshot(bounds, 0f, 0f, 0f, 0f);
    }
}
