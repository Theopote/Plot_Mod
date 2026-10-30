package com.plot.plugin.road.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.WorldCoordinateUtils;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * 用预取的世界列工程地面覆盖客户端采样，避免未刷出/空列把纵断面地面拉到世界底界。
 */
public final class PrefetchedGroundTerrainSampler implements TerrainSampler {

    private final TerrainSampler fallback;
    private final ICoordinateService coordinates;
    private final Map<Long, Integer> groundByColumn;
    private final Set<Long> emptyColumns;

    public PrefetchedGroundTerrainSampler(
            TerrainSampler fallback,
            ICoordinateService coordinates,
            Map<Long, Integer> groundByColumn,
            Set<Long> emptyColumns) {
        this.fallback = fallback;
        this.coordinates = coordinates;
        this.groundByColumn = groundByColumn != null
            ? Map.copyOf(groundByColumn)
            : Map.of();
        this.emptyColumns = emptyColumns != null
            ? Set.copyOf(emptyColumns)
            : Set.of();
    }

    public static long packColumn(int blockX, int blockZ) {
        return ((long) blockX << 32) ^ (blockZ & 0xffffffffL);
    }

    public int cachedColumnCount() {
        return groundByColumn.size();
    }

    public int emptyColumnCount() {
        return emptyColumns.size();
    }

    @Override
    public int worldBottomY() {
        return fallback.worldBottomY();
    }

    @Override
    public int worldTopExclusiveY() {
        return fallback.worldTopExclusiveY();
    }

    @Override
    public int sampleSurfaceY(Vec2d planPoint) {
        OptionalInt loaded = sampleLoadedSurfaceY(planPoint);
        return loaded.orElseGet(() -> fallback.sampleSurfaceY(planPoint));
    }

    @Override
    public OptionalInt findExposedWaterSurface(Vec2d planPoint) {
        return fallback.findExposedWaterSurface(planPoint);
    }

    @Override
    public boolean isSolidBlock(int worldX, int y, int worldZ) {
        return fallback.isSolidBlock(worldX, y, worldZ);
    }

    @Override
    public boolean isWireObstruction(int worldX, int y, int worldZ) {
        return fallback.isWireObstruction(worldX, y, worldZ);
    }

    @Override
    public int sampleColumnTopY(Vec2d planPoint) {
        return fallback.sampleColumnTopY(planPoint);
    }

    @Override
    public boolean isRoadClearableDecoration(int worldX, int y, int worldZ) {
        return fallback.isRoadClearableDecoration(worldX, y, worldZ);
    }

    @Override
    public boolean isConstructionVolumeObstruction(int worldX, int y, int worldZ) {
        return fallback.isConstructionVolumeObstruction(worldX, y, worldZ);
    }

    @Override
    public boolean isChunkLoaded(int worldX, int worldZ) {
        long key = packColumn(worldX, worldZ);
        if (groundByColumn.containsKey(key) || emptyColumns.contains(key)) {
            return groundByColumn.containsKey(key);
        }
        return fallback.isChunkLoaded(worldX, worldZ);
    }

    @Override
    public boolean isChunkLoaded(Vec2d planPoint) {
        if (planPoint == null) {
            return false;
        }
        BlockPos column = WorldCoordinateUtils.canvasToBlockXZ(planPoint, coordinates);
        return isChunkLoaded(column.getX(), column.getZ());
    }

    @Override
    public OptionalInt sampleLoadedSurfaceY(Vec2d planPoint) {
        if (planPoint == null) {
            return OptionalInt.empty();
        }
        BlockPos column = WorldCoordinateUtils.canvasToBlockXZ(planPoint, coordinates);
        long key = packColumn(column.getX(), column.getZ());
        Integer cached = groundByColumn.get(key);
        if (cached != null) {
            return OptionalInt.of(cached);
        }
        if (emptyColumns.contains(key)) {
            return OptionalInt.empty();
        }
        return fallback.sampleLoadedSurfaceY(planPoint);
    }

    /** 可变构建器，供预取阶段填充。 */
    public static final class Builder {
        private final Map<Long, Integer> groundByColumn = new HashMap<>();
        private final Set<Long> emptyColumns = new HashSet<>();

        public void putGround(int blockX, int blockZ, int groundY) {
            long key = packColumn(blockX, blockZ);
            groundByColumn.put(key, groundY);
            emptyColumns.remove(key);
        }

        public void putEmpty(int blockX, int blockZ) {
            long key = packColumn(blockX, blockZ);
            emptyColumns.add(key);
            groundByColumn.remove(key);
        }

        public boolean isEmpty() {
            return groundByColumn.isEmpty() && emptyColumns.isEmpty();
        }

        public PrefetchedGroundTerrainSampler build(
                TerrainSampler fallback,
                ICoordinateService coordinates) {
            return new PrefetchedGroundTerrainSampler(
                fallback,
                coordinates,
                Collections.unmodifiableMap(new HashMap<>(groundByColumn)),
                Collections.unmodifiableSet(new HashSet<>(emptyColumns)));
        }
    }
}
