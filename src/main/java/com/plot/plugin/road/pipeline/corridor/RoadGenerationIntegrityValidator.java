package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.solid.RoadSolidPrimitive;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 对 corridor plan 与最终 solids 做离线完整性校验。
 */
public final class RoadGenerationIntegrityValidator {
    private RoadGenerationIntegrityValidator() {
    }

    public record IntegrityReport(
            int missingRoadSurfaceCount,
            int unclearedObstructionCount,
            int orphanSlopePillarCount) {
        public static final IntegrityReport ZERO = new IntegrityReport(0, 0, 0);

        public boolean isClean() {
            return missingRoadSurfaceCount == 0
                && unclearedObstructionCount == 0
                && orphanSlopePillarCount == 0;
        }
    }

    public static IntegrityReport validate(
            RoadCorridorPlan plan,
            RoadSolidModel solids,
            TerrainSampler terrain) {
        if (plan == null || solids == null || terrain == null) {
            return IntegrityReport.ZERO;
        }
        SolidIndex index = SolidIndex.from(solids);
        int missingSurface = countMissingRoadSurfaces(plan, index);
        int uncleared = countUnclearedObstructions(plan, index, terrain);
        int orphanPillars = countOrphanSlopePillars(plan);
        return new IntegrityReport(missingSurface, uncleared, orphanPillars);
    }

    public static IntegrityReport validatePlacements(
            RoadCorridorPlan plan,
            RoadGenerationResult result,
            TerrainSampler terrain) {
        if (plan == null || result == null || terrain == null) {
            return IntegrityReport.ZERO;
        }
        PlacementIndex index = PlacementIndex.from(result);
        int missingSurface = countMissingRoadSurfaces(plan, index);
        int uncleared = countUnclearedObstructions(plan, index, terrain);
        int orphanPillars = countOrphanSlopePillars(plan);
        return new IntegrityReport(missingSurface, uncleared, orphanPillars);
    }

    public static void applyToResult(
            RoadGenerationResult result,
            RoadCorridorPlan plan,
            RoadSolidModel solids,
            TerrainSampler terrain) {
        if (result == null) {
            return;
        }
        applyReportToResult(result, validate(plan, solids, terrain));
    }

    public static void applyPlacementsToResult(
            RoadGenerationResult result,
            RoadCorridorPlan plan,
            TerrainSampler terrain) {
        if (result == null) {
            return;
        }
        applyReportToResult(result, validatePlacements(plan, result, terrain));
    }

    public static void applyReportToResult(RoadGenerationResult result, IntegrityReport report) {
        if (result == null || report == null) {
            return;
        }
        result.missingRoadSurfaceCount = report.missingRoadSurfaceCount();
        result.unclearedObstructionCount = report.unclearedObstructionCount();
        result.orphanSlopePillarCount = report.orphanSlopePillarCount();
    }

    private static int countMissingRoadSurfaces(RoadCorridorPlan plan, SolidIndex index) {
        int missing = 0;
        for (var entry : plan.surfaceMask().entries()) {
            SurfaceCell cell = entry.getValue();
            if (!plan.surfaceMask().isRoadSurfaceLayer(cell.layer())) {
                continue;
            }
            if (!index.hasSurfaceMaterial(cell.planPoint(), cell.surfaceY(), cell.layer())) {
                missing++;
            }
        }
        return missing;
    }

    private static int countMissingRoadSurfaces(RoadCorridorPlan plan, PlacementIndex index) {
        int missing = 0;
        for (var entry : plan.surfaceMask().entries()) {
            SurfaceCell cell = entry.getValue();
            ClearanceCell clearance = plan.clearanceCell(entry.getKey());
            if (clearance != null && clearance.mode() == RoadCorridorConstructionMode.TUNNEL) {
                continue;
            }
            if (!plan.surfaceMask().isRoadSurfaceLayer(cell.layer())) {
                continue;
            }
            if (!index.hasSurfaceMaterial(cell.planPoint(), cell.surfaceY(), cell.layer())) {
                missing++;
            }
        }
        return missing;
    }

    private static int countUnclearedObstructions(
            RoadCorridorPlan plan,
            SolidIndex index,
            TerrainSampler terrain) {
        int uncleared = 0;
        for (var entry : plan.clearanceEntries()) {
            ColumnKey key = entry.getKey();
            ClearanceCell cell = entry.getValue();
            if (cell.mode() == RoadCorridorConstructionMode.BRIDGE
                    || cell.mode() == RoadCorridorConstructionMode.TUNNEL) {
                continue;
            }
            if (!terrain.isChunkLoaded(key.worldX(), key.worldZ())) {
                continue;
            }
            for (int y = cell.designSurfaceY() + 1; y <= cell.clearTopY(); y++) {
                if (!RoadCorridorClearancePass.hasObstruction(terrain, key.worldX(), y, key.worldZ())) {
                    continue;
                }
                if (!index.hasAir(cell.planPoint(), y)) {
                    uncleared++;
                }
            }
        }
        return uncleared;
    }

    private static int countUnclearedObstructions(
            RoadCorridorPlan plan,
            PlacementIndex index,
            TerrainSampler terrain) {
        int uncleared = 0;
        for (var entry : plan.clearanceEntries()) {
            ColumnKey key = entry.getKey();
            ClearanceCell cell = entry.getValue();
            if (cell.mode() == RoadCorridorConstructionMode.BRIDGE
                    || cell.mode() == RoadCorridorConstructionMode.TUNNEL) {
                continue;
            }
            if (!terrain.isChunkLoaded(key.worldX(), key.worldZ())) {
                continue;
            }
            for (int y = cell.designSurfaceY() + 1; y <= cell.clearTopY(); y++) {
                if (!RoadCorridorClearancePass.hasObstruction(terrain, key.worldX(), y, key.worldZ())) {
                    continue;
                }
                if (!index.hasAir(cell.planPoint(), y)) {
                    uncleared++;
                }
            }
        }
        return uncleared;
    }

    private static int countOrphanSlopePillars(RoadCorridorPlan plan) {
        Map<ColumnKey, Integer> columnHeights = new HashMap<>();
        for (var entry : plan.slopeEntries()) {
            SlopeColumnCell cell = entry.getValue();
            int height = cell.hasCut()
                ? cell.cutSurfaceY()
                : cell.hasFill() ? cell.fillSurfaceY() : Integer.MIN_VALUE;
            if (height != Integer.MIN_VALUE) {
                columnHeights.put(entry.getKey(), height);
            }
        }
        int orphans = 0;
        for (var entry : columnHeights.entrySet()) {
            ColumnKey key = entry.getKey();
            int height = entry.getValue();
            int neighborCount = 0;
            int smoothNeighbors = 0;
            for (int[] offset : NEIGHBOR_OFFSETS) {
                ColumnKey neighborKey = new ColumnKey(key.worldX() + offset[0], key.worldZ() + offset[1]);
                Integer neighborHeight = columnHeights.get(neighborKey);
                if (neighborHeight == null) {
                    continue;
                }
                neighborCount++;
                if (Math.abs(neighborHeight - height) <= 1) {
                    smoothNeighbors++;
                }
            }
            if (neighborCount > 0 && smoothNeighbors == 0 && Math.abs(height) > 0) {
                orphans++;
            }
        }
        return orphans;
    }

    private static final int[][] NEIGHBOR_OFFSETS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private static final class SolidIndex {
        private final Map<Long, RoadSolidPrimitive> byPosition = new HashMap<>();
        private final Set<Long> airPositions = new HashSet<>();

        static SolidIndex from(RoadSolidModel solids) {
            SolidIndex index = new SolidIndex();
            for (RoadSolidPrimitive primitive : solids.primitives()) {
                long key = posKey(primitive.planPoint(), primitive.elevation());
                index.byPosition.put(key, primitive);
                if ("minecraft:air".equals(primitive.materialId())
                        && primitive.materialPriority() == com.plot.plugin.road.solid.RoadSolidMaterialPriority.AIR) {
                    index.airPositions.add(key);
                }
            }
            return index;
        }

        boolean hasSurfaceMaterial(Vec2d planPoint, int y, RoadSolidLayer layer) {
            RoadSolidPrimitive primitive = byPosition.get(posKey(planPoint, y));
            return primitive != null
                && primitive.layer() == layer
                && primitive.materialId() != null
                && !"minecraft:air".equals(primitive.materialId());
        }

        boolean hasAir(Vec2d planPoint, int y) {
            return airPositions.contains(posKey(planPoint, y));
        }

        private static long posKey(Vec2d planPoint, int y) {
            return PlacementIndex.posKey(planPoint, y);
        }
    }

    private static final class PlacementIndex {
        private final Map<Long, BlockRecord> records = new HashMap<>();
        private final Map<Long, RoadSolidLayer> layers = new HashMap<>();

        static PlacementIndex from(RoadGenerationResult result) {
            PlacementIndex index = new PlacementIndex();
            for (Map.Entry<BlockPos, BlockRecord> entry : result.placementRecords.entrySet()) {
                BlockPos pos = entry.getKey();
                long key = posKey(pos);
                index.records.put(key, entry.getValue());
                RoadSolidLayer layer = result.placementLayers.get(pos);
                if (layer != null) {
                    index.layers.put(key, layer);
                }
            }
            return index;
        }

        boolean hasSurfaceMaterial(Vec2d planPoint, int y, RoadSolidLayer layer) {
            long key = posKey(planPoint, y);
            BlockRecord record = records.get(key);
            return record != null
                && record.newBlockId != null
                && !"minecraft:air".equals(record.newBlockId);
        }

        boolean hasAir(Vec2d planPoint, int y) {
            BlockRecord record = records.get(posKey(planPoint, y));
            return record != null
                && record.newBlockId != null
                && !record.newBlockId.isBlank();
        }

        private static long posKey(BlockPos pos) {
            return ((long) pos.getX() << 32) ^ (pos.getZ() & 0xFFFFFFFFL) ^ ((long) pos.getY() << 16);
        }

        private static long posKey(Vec2d planPoint, int y) {
            int x = (int) Math.round(planPoint.x);
            int z = (int) Math.round(planPoint.y);
            return ((long) x << 32) ^ (z & 0xFFFFFFFFL) ^ ((long) y << 16);
        }
    }
}
