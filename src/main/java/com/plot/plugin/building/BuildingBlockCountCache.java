package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.plugin.building.model.BuildingFootprint;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 建筑轮廓方块数缓存（几何 + 当前视图投影）。 */
public final class BuildingBlockCountCache {
    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    private record Entry(int geometryFingerprint, int projectionFingerprint, int blockCount) {
    }

    private BuildingBlockCountCache() {
    }

    public static int blockCount(BuildingFootprint footprint, WorldProjectionSnapshot projection) {
        if (footprint == null) {
            return 0;
        }
        int geometryFingerprint = footprint.geometryFingerprint();
        int projectionFingerprint = projection != null && projection.isValid()
            ? projection.uiFingerprint()
            : 0;
        Entry cached = CACHE.get(footprint.getId());
        if (cached != null
                && cached.geometryFingerprint == geometryFingerprint
                && cached.projectionFingerprint == projectionFingerprint) {
            return cached.blockCount;
        }
        int blockCount = PolygonRegionUtils.countProjectedWorldBlocks(
            footprint.getOuterPoints(),
            List.of(),
            projection);
        CACHE.put(
            footprint.getId(),
            new Entry(geometryFingerprint, projectionFingerprint, blockCount));
        return blockCount;
    }

    public static int blockCount(BuildingFootprint footprint, ICoordinateService coordinates) {
        if (coordinates == null) {
            return blockCount(footprint, WorldProjectionSnapshot.UNKNOWN);
        }
        try {
            return blockCount(footprint, coordinates.captureProjection());
        } catch (RuntimeException ignored) {
            return blockCount(footprint, WorldProjectionSnapshot.UNKNOWN);
        }
    }

    public static int blockCount(List<Vec2d> outerPoints, WorldProjectionSnapshot projection) {
        return PolygonRegionUtils.countProjectedWorldBlocks(outerPoints, List.of(), projection);
    }

    public static int totalBlockCount(
            Iterable<BuildingFootprint> footprints,
            WorldProjectionSnapshot projection) {
        int count = 0;
        for (BuildingFootprint footprint : footprints) {
            count += blockCount(footprint, projection);
        }
        return count;
    }

    public static void retainOnly(Set<String> buildingIds) {
        if (buildingIds == null || buildingIds.isEmpty()) {
            CACHE.clear();
            return;
        }
        CACHE.entrySet().removeIf(entry -> !buildingIds.contains(entry.getKey()));
    }
}
