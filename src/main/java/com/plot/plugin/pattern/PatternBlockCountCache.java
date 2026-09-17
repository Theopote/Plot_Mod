package com.plot.plugin.pattern;

import com.plot.api.world.ICoordinateService;
import com.plot.plugin.pattern.model.PatternFootprint;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 区域方块数缓存（几何 + 当前视图投影）。 */
public final class PatternBlockCountCache {
    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    private record Entry(int geometryFingerprint, int projectionFingerprint, int blockCount) {
    }

    private PatternBlockCountCache() {
    }

    public static int blockCount(PatternFootprint footprint, ICoordinateService coordinates) {
        if (footprint == null) {
            return 0;
        }
        int geometryFingerprint = footprint.geometryFingerprint();
        int projectionFingerprint = projectionFingerprint(coordinates);
        Entry cached = CACHE.get(footprint.getId());
        if (cached != null
                && cached.geometryFingerprint == geometryFingerprint
                && cached.projectionFingerprint == projectionFingerprint) {
            return cached.blockCount;
        }
        int blockCount = PatternGeometryUtils.countProjectedWorldBlocks(
            footprint.getOuterPoints(),
            footprint.getHoles(),
            coordinates);
        CACHE.put(
            footprint.getId(),
            new Entry(geometryFingerprint, projectionFingerprint, blockCount));
        return blockCount;
    }

    public static void retainOnly(Set<String> footprintIds) {
        if (footprintIds == null || footprintIds.isEmpty()) {
            CACHE.clear();
            return;
        }
        Iterator<Map.Entry<String, Entry>> iterator = CACHE.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!footprintIds.contains(iterator.next().getKey())) {
                iterator.remove();
            }
        }
    }

    private static int projectionFingerprint(ICoordinateService coordinates) {
        if (coordinates == null) {
            return 0;
        }
        try {
            return coordinates.captureProjection().fingerprint();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }
}
