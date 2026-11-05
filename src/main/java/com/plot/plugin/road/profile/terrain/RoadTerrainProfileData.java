package com.plot.plugin.road.profile.terrain;

import java.util.List;
import java.util.Objects;

/**
 * Level-1 地形纵断面：仅路径中心线工程地表（与可选水面），不含道路设计线 / PVI / 建造方块。
 * <p>
 * {@code waterY} 无水面时为 {@link Double#NaN}；{@code loaded} 为 false 表示该站区块未加载，
 * {@code terrainY} 亦为 NaN，图表应画成缺口而非假海平面。
 */
public record RoadTerrainProfileData(
        String roadId,
        double totalStation,
        List<Double> stations,
        List<Double> terrainY,
        List<Double> waterY,
        List<Boolean> loaded,
        long geometryFingerprint,
        long terrainRevision) {

    public RoadTerrainProfileData {
        Objects.requireNonNull(roadId, "roadId");
        stations = List.copyOf(Objects.requireNonNull(stations, "stations"));
        terrainY = List.copyOf(Objects.requireNonNull(terrainY, "terrainY"));
        waterY = List.copyOf(Objects.requireNonNull(waterY, "waterY"));
        loaded = List.copyOf(Objects.requireNonNull(loaded, "loaded"));
        if (stations.size() < 2
                || terrainY.size() != stations.size()
                || waterY.size() != stations.size()
                || loaded.size() != stations.size()) {
            throw new IllegalArgumentException("terrain profile series length mismatch");
        }
        if (!Double.isFinite(totalStation) || totalStation < 0) {
            throw new IllegalArgumentException("invalid totalStation");
        }
    }

    public boolean hasRenderableTerrain() {
        int loadedCount = 0;
        for (Boolean flag : loaded) {
            if (Boolean.TRUE.equals(flag)) {
                loadedCount++;
            }
        }
        return loadedCount >= 2;
    }

    public boolean fullyLoaded() {
        for (Boolean flag : loaded) {
            if (!Boolean.TRUE.equals(flag)) {
                return false;
            }
        }
        return true;
    }

    public boolean matches(long geometryFingerprint, long terrainRevision) {
        return this.geometryFingerprint == geometryFingerprint
            && this.terrainRevision == terrainRevision;
    }
}
