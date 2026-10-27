package com.plot.plugin.earthwork;

import com.plot.api.world.ICoordinateService;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.plugin.earthwork.model.GradingRegion;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 土方分区占地方块数缓存（几何 + 当前视图投影），按插件会话实例隔离。 */
public final class EarthworkRegionBlockCountCache {
    private final Map<String, Entry> cache = new HashMap<>();

    private record Entry(int geometryFingerprint, int projectionFingerprint, int blockCount) {
    }

    public int blockCount(GradingRegion region, WorldProjectionSnapshot projection) {
        if (region == null) {
            return 0;
        }
        int geometryFingerprint = geometryFingerprint(region);
        int projectionFingerprint = projection != null && projection.isValid()
            ? projection.uiFingerprint()
            : 0;
        Entry cached = cache.get(region.getId());
        if (cached != null
                && cached.geometryFingerprint == geometryFingerprint
                && cached.projectionFingerprint == projectionFingerprint) {
            return cached.blockCount;
        }
        int blockCount = PolygonRegionUtils.countProjectedWorldBlocks(
            region.getOuterPoints(),
            region.getHoles(),
            projection);
        cache.put(region.getId(), new Entry(geometryFingerprint, projectionFingerprint, blockCount));
        return blockCount;
    }

    public int blockCount(GradingRegion region, ICoordinateService coordinates) {
        if (coordinates == null) {
            return blockCount(region, WorldProjectionSnapshot.UNKNOWN);
        }
        try {
            return blockCount(region, coordinates.captureProjection());
        } catch (RuntimeException ignored) {
            return blockCount(region, WorldProjectionSnapshot.UNKNOWN);
        }
    }

    public int totalBlockCount(Iterable<GradingRegion> regions, WorldProjectionSnapshot projection) {
        int count = 0;
        if (regions == null) {
            return 0;
        }
        for (GradingRegion region : regions) {
            count += blockCount(region, projection);
        }
        return count;
    }

    public void retainOnly(Set<String> regionIds) {
        if (regionIds == null || regionIds.isEmpty()) {
            cache.clear();
            return;
        }
        cache.entrySet().removeIf(entry -> !regionIds.contains(entry.getKey()));
    }

    private static int geometryFingerprint(GradingRegion region) {
        return Objects.hash(region.getOuterPoints(), region.getHoles());
    }
}
