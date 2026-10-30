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
 * 用服务端预取的列快照覆盖客户端采样：地面与裸露水面同源、同有效性，
 * 避免未刷出/空列把纵断面地面拉到世界底界，也避免「地面已预取、水面仍未知」。
 */
public final class PrefetchedGroundTerrainSampler implements TerrainSampler {

    private final TerrainSampler fallback;
    private final ICoordinateService coordinates;
    /** 已在服务端采样过的列（含无地面 / 无水面）。 */
    private final Set<Long> knownColumns;
    private final Map<Long, Integer> groundByColumn;
    private final Map<Long, Integer> waterByColumn;

    public PrefetchedGroundTerrainSampler(
            TerrainSampler fallback,
            ICoordinateService coordinates,
            Set<Long> knownColumns,
            Map<Long, Integer> groundByColumn,
            Map<Long, Integer> waterByColumn) {
        this.fallback = fallback;
        this.coordinates = coordinates;
        this.knownColumns = knownColumns != null ? Set.copyOf(knownColumns) : Set.of();
        this.groundByColumn = groundByColumn != null ? Map.copyOf(groundByColumn) : Map.of();
        this.waterByColumn = waterByColumn != null ? Map.copyOf(waterByColumn) : Map.of();
    }

    public static long packColumn(int blockX, int blockZ) {
        return ((long) blockX << 32) ^ (blockZ & 0xffffffffL);
    }

    public int knownColumnCount() {
        return knownColumns.size();
    }

    public int cachedGroundCount() {
        return groundByColumn.size();
    }

    public int cachedWaterCount() {
        return waterByColumn.size();
    }

    public int emptyGroundCount() {
        return Math.max(0, knownColumns.size() - groundByColumn.size());
    }

    /** @deprecated 使用 {@link #emptyGroundCount()} */
    @Deprecated
    public int emptyColumnCount() {
        return emptyGroundCount();
    }

    /** @deprecated 使用 {@link #cachedGroundCount()} */
    @Deprecated
    public int cachedColumnCount() {
        return cachedGroundCount();
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
        if (planPoint == null) {
            return OptionalInt.empty();
        }
        BlockPos column = WorldCoordinateUtils.canvasToBlockXZ(planPoint, coordinates);
        long key = packColumn(column.getX(), column.getZ());
        if (knownColumns.contains(key)) {
            Integer water = waterByColumn.get(key);
            return water != null ? OptionalInt.of(water) : OptionalInt.empty();
        }
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
        if (knownColumns.contains(key)) {
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
        if (knownColumns.contains(key)) {
            Integer cached = groundByColumn.get(key);
            return cached != null ? OptionalInt.of(cached) : OptionalInt.empty();
        }
        return fallback.sampleLoadedSurfaceY(planPoint);
    }

    /** 可变构建器，供预取阶段填充。 */
    public static final class Builder {
        private final Set<Long> knownColumns = new HashSet<>();
        private final Map<Long, Integer> groundByColumn = new HashMap<>();
        private final Map<Long, Integer> waterByColumn = new HashMap<>();

        public void putColumn(int blockX, int blockZ, OptionalInt ground, OptionalInt water) {
            long key = packColumn(blockX, blockZ);
            knownColumns.add(key);
            if (ground != null && ground.isPresent()) {
                groundByColumn.put(key, ground.getAsInt());
            } else {
                groundByColumn.remove(key);
            }
            if (water != null && water.isPresent()) {
                waterByColumn.put(key, water.getAsInt());
            } else {
                waterByColumn.remove(key);
            }
        }

        public void putGround(int blockX, int blockZ, int groundY) {
            putColumn(blockX, blockZ, OptionalInt.of(groundY), OptionalInt.empty());
        }

        public void putEmpty(int blockX, int blockZ) {
            putColumn(blockX, blockZ, OptionalInt.empty(), OptionalInt.empty());
        }

        public boolean isEmpty() {
            return knownColumns.isEmpty();
        }

        public int knownCount() {
            return knownColumns.size();
        }

        public PrefetchedGroundTerrainSampler build(
                TerrainSampler fallback,
                ICoordinateService coordinates) {
            return new PrefetchedGroundTerrainSampler(
                fallback,
                coordinates,
                Collections.unmodifiableSet(new HashSet<>(knownColumns)),
                Collections.unmodifiableMap(new HashMap<>(groundByColumn)),
                Collections.unmodifiableMap(new HashMap<>(waterByColumn)));
        }
    }
}
