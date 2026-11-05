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
        TerrainProfileKey key,
        double totalStation,
        List<Double> stations,
        List<Double> terrainY,
        List<Double> waterY,
        List<Boolean> loaded) {

    public RoadTerrainProfileData {
        Objects.requireNonNull(key, "key");
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

    public String roadId() {
        return key.roadId();
    }

    public long geometryFingerprint() {
        return key.geometryFingerprint();
    }

    public long terrainEpoch() {
        return key.terrainEpoch();
    }

    /** 至少一个真实地形采样点。 */
    public boolean hasAnyValidSample() {
        for (int i = 0; i < loaded.size(); i++) {
            if (Boolean.TRUE.equals(loaded.get(i)) && Double.isFinite(terrainY.get(i))) {
                return true;
            }
        }
        return false;
    }

    /** 至少一组相邻有效采样点，折线才能画出可见灰线。 */
    public boolean hasDrawableSegment() {
        for (int i = 1; i < loaded.size(); i++) {
            if (Boolean.TRUE.equals(loaded.get(i - 1))
                    && Boolean.TRUE.equals(loaded.get(i))
                    && Double.isFinite(terrainY.get(i - 1))
                    && Double.isFinite(terrainY.get(i))) {
                return true;
            }
        }
        return false;
    }

    /** 可渲染：需要至少一段连续有效地形折线。 */
    public boolean hasRenderableTerrain() {
        return hasDrawableSegment();
    }

    public boolean fullyLoaded() {
        for (Boolean flag : loaded) {
            if (!Boolean.TRUE.equals(flag)) {
                return false;
            }
        }
        return true;
    }

    public boolean matches(TerrainProfileKey expected) {
        return key.matches(expected);
    }

    public double nearestFiniteTerrainY(double station) {
        if (stations.isEmpty()) {
            return Double.NaN;
        }
        int best = -1;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < stations.size(); i++) {
            if (!Boolean.TRUE.equals(loaded.get(i)) || !Double.isFinite(terrainY.get(i))) {
                continue;
            }
            double dist = Math.abs(stations.get(i) - station);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best >= 0 ? terrainY.get(best) : Double.NaN;
    }
}
