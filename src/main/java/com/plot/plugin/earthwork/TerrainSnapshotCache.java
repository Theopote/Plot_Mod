package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.plugin.earthwork.model.GradingRegion;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 按区域缓存 {@link TerrainSnapshot}，参数微调时复用地形采样，避免重复查询世界。
 */
public final class TerrainSnapshotCache {
    private static final class Entry {
        final long outlineFingerprint;
        final String worldKey;
        final TerrainSnapshot snapshot;

        Entry(long outlineFingerprint, String worldKey, TerrainSnapshot snapshot) {
            this.outlineFingerprint = outlineFingerprint;
            this.worldKey = worldKey;
            this.snapshot = snapshot;
        }

        boolean matches(long outlineFingerprint, String worldKey) {
            return this.outlineFingerprint == outlineFingerprint && this.worldKey.equals(worldKey);
        }
    }

    private final Map<String, Entry> byRegionId = new HashMap<>();

    public TerrainSnapshot getOrCapture(
            GradingRegion region,
            World world,
            ICoordinateService transformer) {
        if (region == null) {
            return TerrainSnapshot.empty();
        }
        List<Vec2d> outerPoints = region.getOuterPoints();
        if (outerPoints.size() < 3) {
            return TerrainSnapshot.empty();
        }

        String regionId = region.getId();
        long outlineFingerprint = outlineFingerprint(outerPoints);
        String worldKey = worldKey(world);
        Entry cached = byRegionId.get(regionId);
        if (cached != null && cached.matches(outlineFingerprint, worldKey)) {
            return cached.snapshot;
        }

        Polygon polygon = EarthworkGeometryUtils.toPolygon(outerPoints);
        TerrainSnapshot snapshot = TerrainSnapshot.capture(world, polygon, outerPoints, transformer);
        byRegionId.put(regionId, new Entry(outlineFingerprint, worldKey, snapshot));
        return snapshot;
    }

    public void invalidateRegion(String regionId) {
        if (regionId != null && !regionId.isBlank()) {
            byRegionId.remove(regionId);
        }
    }

    public void clear() {
        byRegionId.clear();
    }

    public boolean isCached(String regionId) {
        return regionId != null && byRegionId.containsKey(regionId);
    }

    static long outlineFingerprint(List<Vec2d> outerPoints) {
        long hash = 17L;
        if (outerPoints != null) {
            for (Vec2d point : outerPoints) {
                if (point == null) {
                    continue;
                }
                hash = 31L * hash + Double.hashCode(point.x);
                hash = 31L * hash + Double.hashCode(point.y);
            }
        }
        return hash;
    }

    static String worldKey(World world) {
        if (world == null) {
            return "null";
        }
        RegistryKey<World> registryKey = world.getRegistryKey();
        return registryKey != null ? registryKey.getValue().toString() : ("world@" + System.identityHashCode(world));
    }
}
