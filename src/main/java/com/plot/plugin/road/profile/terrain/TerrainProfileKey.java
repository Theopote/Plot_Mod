package com.plot.plugin.road.profile.terrain;

import java.util.Objects;

/**
 * Level-1 地形缓存 / 任务发布的统一身份。
 * 几何、世界、坐标变换、地形世代与采样间距任一不匹配即视为过期。
 */
public record TerrainProfileKey(
        String worldId,
        String roadId,
        long geometryFingerprint,
        long transformFingerprint,
        long terrainEpoch,
        double sampleSpacing) {

    public TerrainProfileKey {
        worldId = worldId != null ? worldId : "";
        Objects.requireNonNull(roadId, "roadId");
        if (!Double.isFinite(sampleSpacing) || sampleSpacing <= 0) {
            throw new IllegalArgumentException("invalid sampleSpacing");
        }
    }

    public boolean matches(TerrainProfileKey other) {
        return other != null
            && worldId.equals(other.worldId)
            && roadId.equals(other.roadId)
            && geometryFingerprint == other.geometryFingerprint
            && transformFingerprint == other.transformFingerprint
            && terrainEpoch == other.terrainEpoch
            && Math.abs(sampleSpacing - other.sampleSpacing) < 1e-9;
    }
}
