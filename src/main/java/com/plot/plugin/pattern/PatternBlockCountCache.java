package com.plot.plugin.pattern;

import com.plot.api.world.ICoordinateService;
import com.plot.api.world.WorldProjectionSnapshot;
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

    public static int blockCount(PatternFootprint footprint, WorldProjectionSnapshot projection) {
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
        int blockCount = PatternGeometryUtils.countProjectedWorldBlocks(
            footprint.getOuterPoints(),
            footprint.getHoles(),
            projection);
        CACHE.put(
            footprint.getId(),
            new Entry(geometryFingerprint, projectionFingerprint, blockCount));
        return blockCount;
    }

    public static int blockCount(PatternFootprint footprint, ICoordinateService coordinates) {
        if (coordinates == null) {
            return blockCount(footprint, WorldProjectionSnapshot.UNKNOWN);
        }
        try {
            return blockCount(footprint, coordinates.captureProjection());
        } catch (RuntimeException ignored) {
            return blockCount(footprint, WorldProjectionSnapshot.UNKNOWN);
        }
    }

    public static int totalBlockCount(
            Iterable<PatternFootprint> footprints,
            WorldProjectionSnapshot projection) {
        int count = 0;
        for (PatternFootprint footprint : footprints) {
            count += blockCount(footprint, projection);
        }
        return count;
    }

    public static void retainOnly(Set<String> footprintIds) {
        if (footprintIds == null || footprintIds.isEmpty()) {
            CACHE.clear();
            return;
        }
        CACHE.entrySet().removeIf(stringEntryEntry -> !footprintIds.contains(stringEntryEntry.getKey()));
    }
}
