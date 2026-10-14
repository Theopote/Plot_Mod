package com.plot.plugin.road.pipeline.corridor;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.pipeline.construction.RoadEarthworkPolicy;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.solid.RoadSolidPrimitive;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
            int orphanSlopePillarCount,
            int slopeBatterWidthCapCount,
            int brokenSlopeSurfaceCount,
            int unresolvedSlopeConstructionCount,
            int shoulderTraceGapCount,
            int structureInteriorSlopeCellCount) {
        public static final IntegrityReport ZERO = new IntegrityReport(0, 0, 0, 0, 0, 0, 0, 0);

        public boolean isClean() {
            return missingRoadSurfaceCount == 0
                && unclearedObstructionCount == 0
                && orphanSlopePillarCount == 0;
        }

        /** Slope did not reach terrain or left a lateral gap in the batter mask. */
        public boolean hasUnresolvedSlopeConstruction() {
            return unresolvedSlopeConstructionCount > 0
                || brokenSlopeSurfaceCount > 0
                || shoulderTraceGapCount > 0;
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
        int brokenSlopes = countBrokenSlopeSurfaces(plan);
        int lateralGaps = countSlopeLateralGaps(plan);
        int traceGaps = SlopeShoulderTerrainTraceValidator.countShoulderToTerrainGaps(plan, terrain);
        int structureInteriorSlopes = countStructureInteriorSlopeCells(plan);
        plan.setBrokenSlopeSurfaceCount(brokenSlopes + lateralGaps);
        return new IntegrityReport(
            missingSurface,
            uncleared,
            orphanPillars,
            plan.slopeBatterWidthCapCount(),
            brokenSlopes + lateralGaps,
            plan.unresolvedSlopeConstructionCount(),
            traceGaps,
            structureInteriorSlopes);
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
        int brokenSlopes = countBrokenSlopeSurfaces(plan);
        int lateralGaps = countSlopeLateralGaps(plan);
        int traceGaps = SlopeShoulderTerrainTraceValidator.countShoulderToTerrainGaps(plan, terrain);
        int structureInteriorSlopes = countStructureInteriorSlopeCells(plan);
        plan.setBrokenSlopeSurfaceCount(brokenSlopes + lateralGaps);
        return new IntegrityReport(
            missingSurface,
            uncleared,
            orphanPillars,
            plan.slopeBatterWidthCapCount(),
            brokenSlopes + lateralGaps,
            plan.unresolvedSlopeConstructionCount(),
            traceGaps,
            structureInteriorSlopes);
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
        result.slopeBatterWidthCapCount = report.slopeBatterWidthCapCount();
        result.brokenSlopeSurfaceCount = report.brokenSlopeSurfaceCount();
        result.unresolvedSlopeConstructionCount = report.unresolvedSlopeConstructionCount();
        result.shoulderTraceGapCount = report.shoulderTraceGapCount();
        result.structureInteriorSlopeCellCount = report.structureInteriorSlopeCellCount();
    }

    static int countStructureInteriorSlopeCells(RoadCorridorPlan plan) {
        int count = 0;
        for (var entry : plan.slopeEntries()) {
            ClearanceCell clearance = plan.clearanceCell(entry.getKey());
            RoadConstructionType type = clearance != null ? clearance.constructionType() : null;
            if (type != null && RoadEarthworkPolicy.of(type) == RoadEarthworkPolicy.STRUCTURE_ONLY) {
                count++;
            }
        }
        return count;
    }

    private static int countMissingRoadSurfaces(RoadCorridorPlan plan, SolidIndex index) {
        int missing = 0;
        for (var entry : plan.surfaceMask().entries()) {
            SurfaceCell cell = entry.getValue();
            if (!plan.surfaceMask().isRoadSurfaceLayer(cell.layer())) {
                continue;
            }
            if (!index.hasSurfaceMaterial(entry.getKey(), cell.surfaceY(), cell.layer(), cell.materialId())) {
                missing++;
            }
        }
        return missing;
    }

    private static int countMissingRoadSurfaces(RoadCorridorPlan plan, PlacementIndex index) {
        int missing = 0;
        for (var entry : plan.surfaceMask().entries()) {
            SurfaceCell cell = entry.getValue();
            if (!plan.surfaceMask().isRoadSurfaceLayer(cell.layer())) {
                continue;
            }
            if (!index.hasSurfaceMaterial(entry.getKey(), cell.surfaceY(), cell.layer(), cell.materialId())) {
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
            if (cell.mode() == RoadCorridorConstructionMode.TUNNEL) {
                continue;
            }
            if (!terrain.isChunkLoaded(key.worldX(), key.worldZ())) {
                continue;
            }
            int topY = Math.max(cell.clearTopY(), cell.designSurfaceY());
            for (int y = cell.designSurfaceY() + 1; y <= topY; y++) {
                RoadBlockExpectation expectation = RoadBlockExpectation.clearance(key, y);
                if (!RoadCorridorClearancePass.hasObstruction(terrain, key.worldX(), y, key.worldZ())) {
                    continue;
                }
                if (!index.expectationResolved(expectation)) {
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
            if (cell.mode() == RoadCorridorConstructionMode.TUNNEL) {
                continue;
            }
            if (!terrain.isChunkLoaded(key.worldX(), key.worldZ())) {
                continue;
            }
            int topY = Math.max(cell.clearTopY(), cell.designSurfaceY());
            for (int y = cell.designSurfaceY() + 1; y <= topY; y++) {
                RoadBlockExpectation expectation = RoadBlockExpectation.clearance(key, y);
                if (!RoadCorridorClearancePass.hasObstruction(terrain, key.worldX(), y, key.worldZ())) {
                    continue;
                }
                if (!index.expectationResolved(expectation)) {
                    uncleared++;
                }
            }
        }
        return uncleared;
    }

    /**
     * Slope columns whose height differs by more than one block from every neighboring slope column.
     */
    private static int countBrokenSlopeSurfaces(RoadCorridorPlan plan) {
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
        int broken = 0;
        for (var entry : columnHeights.entrySet()) {
            ColumnKey key = entry.getKey();
            int height = entry.getValue();
            int neighborCount = 0;
            int connectedNeighbors = 0;
            for (int[] offset : NEIGHBOR_OFFSETS) {
                ColumnKey neighborKey = new ColumnKey(key.worldX() + offset[0], key.worldZ() + offset[1]);
                Integer neighborHeight = columnHeights.get(neighborKey);
                if (neighborHeight == null) {
                    continue;
                }
                neighborCount++;
                if (Math.abs(neighborHeight - height) <= 1) {
                    connectedNeighbors++;
                }
            }
            if (neighborCount == 0) {
                broken++;
                continue;
            }
            if (connectedNeighbors == 0) {
                broken++;
            }
        }
        return broken;
    }

    /**
     * Detects a horizontal gap between two slope runs along the same row or column
     * (common when span batter was missing before mergeSlopeSpan).
     */
    private static int countSlopeLateralGaps(RoadCorridorPlan plan) {
        Map<Integer, Set<Integer>> slopeXsByZ = new HashMap<>();
        Map<Integer, Set<Integer>> slopeZsByX = new HashMap<>();
        for (var entry : plan.slopeEntries()) {
            ColumnKey key = entry.getKey();
            slopeXsByZ.computeIfAbsent(key.worldZ(), ignored -> new HashSet<>()).add(key.worldX());
            slopeZsByX.computeIfAbsent(key.worldX(), ignored -> new HashSet<>()).add(key.worldZ());
        }
        int gaps = 0;
        for (Set<Integer> xs : slopeXsByZ.values()) {
            gaps += countIntegerGaps(xs);
        }
        for (Set<Integer> zs : slopeZsByX.values()) {
            gaps += countIntegerGaps(zs);
        }
        return gaps;
    }

    private static int countIntegerGaps(Set<Integer> values) {
        if (values == null || values.size() < 4) {
            return 0;
        }
        List<Integer> sorted = values.stream().sorted().toList();
        int gaps = 0;
        for (int i = 1; i < sorted.size(); i++) {
            int delta = sorted.get(i) - sorted.get(i - 1);
            if (delta > 1 && delta <= 6) {
                gaps++;
            }
        }
        return gaps;
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

    private static BlockPos columnPos(ColumnKey column, int y) {
        return new BlockPos(column.worldX(), y, column.worldZ());
    }

    private static boolean isRoadSurfaceLayer(RoadSolidLayer layer) {
        return layer == RoadSolidLayer.ROAD
            || layer == RoadSolidLayer.SHOULDER
            || layer == RoadSolidLayer.SIDEWALK
            || layer == RoadSolidLayer.BIKE_LANE;
    }

    private static boolean matchesPlannedSurface(
            String placedMaterialId,
            RoadSolidLayer placedLayer,
            RoadSolidLayer expectedLayer,
            String expectedMaterialId) {
        if (expectedMaterialId == null || placedMaterialId == null || placedMaterialId.isBlank()) {
            return false;
        }
        if ("minecraft:air".equals(placedMaterialId)) {
            return false;
        }
        if (expectedMaterialId.equals(placedMaterialId)
                && (placedLayer == null || placedLayer == expectedLayer)) {
            return true;
        }
        if (!isRoadSurfaceLayer(expectedLayer)) {
            return false;
        }
        // 标线 / 桥面 / 桥台等结构在栅格化阶段会覆盖同高路面，材质与图层可能不同。
        if (placedLayer == RoadSolidLayer.MARKING || placedLayer == RoadSolidLayer.BRIDGE) {
            return true;
        }
        return placedLayer == RoadSolidLayer.TUNNEL;
    }

    private static boolean isStructuralClearanceReplacement(RoadSolidLayer layer) {
        return layer == RoadSolidLayer.TUNNEL
            || layer == RoadSolidLayer.BRIDGE
            || layer == RoadSolidLayer.MARKING;
    }

    private static final class SolidIndex {
        private final Map<BlockPos, RoadSolidPrimitive> byPosition = new HashMap<>();
        private final Set<BlockPos> airPositions = new HashSet<>();

        static SolidIndex from(RoadSolidModel solids) {
            SolidIndex index = new SolidIndex();
            for (RoadSolidPrimitive primitive : solids.primitives()) {
                BlockPos pos = new BlockPos(
                    (int) Math.round(primitive.planPoint().x),
                    primitive.elevation(),
                    (int) Math.round(primitive.planPoint().y));
                index.byPosition.put(pos, primitive);
                if ("minecraft:air".equals(primitive.materialId())
                        && primitive.materialPriority() == com.plot.plugin.road.solid.RoadSolidMaterialPriority.AIR) {
                    index.airPositions.add(pos);
                }
            }
            return index;
        }

        boolean hasSurfaceMaterial(
                ColumnKey column,
                int y,
                RoadSolidLayer expectedLayer,
                String expectedMaterialId) {
            RoadSolidPrimitive primitive = byPosition.get(columnPos(column, y));
            return primitive != null
                && matchesPlannedSurface(
                    primitive.materialId(), primitive.layer(), expectedLayer, expectedMaterialId);
        }

        boolean hasAir(ColumnKey column, int y) {
            return airPositions.contains(columnPos(column, y));
        }

        boolean obstructionClearanceResolved(ColumnKey column, int y) {
            if (hasAir(column, y)) {
                return true;
            }
            RoadSolidPrimitive primitive = byPosition.get(columnPos(column, y));
            return primitive != null && isStructuralClearanceReplacement(primitive.layer());
        }

        boolean expectationResolved(RoadBlockExpectation expectation) {
            BlockPos pos = expectation.position();
            ColumnKey column = new ColumnKey(pos.getX(), pos.getZ());
            int y = pos.getY();
            if (expectation.role() == RoadBlockExpectation.ExpectedRole.SURFACE) {
                return hasSurfaceMaterial(
                    column, y, expectation.expectedLayer(), expectation.expectedMaterialId())
                    || obstructionClearanceResolved(column, y);
            }
            return obstructionClearanceResolved(column, y);
        }
    }

    private static final class PlacementIndex {
        private final Map<BlockPos, BlockRecord> records = new HashMap<>();
        private final Map<BlockPos, RoadSolidLayer> layers = new HashMap<>();

        static PlacementIndex from(RoadGenerationResult result) {
            PlacementIndex index = new PlacementIndex();
            for (Map.Entry<BlockPos, BlockRecord> entry : result.placementRecords.entrySet()) {
                BlockPos pos = entry.getKey();
                index.records.put(pos, entry.getValue());
                RoadSolidLayer layer = result.placementLayers.get(pos);
                if (layer != null) {
                    index.layers.put(pos, layer);
                }
            }
            return index;
        }

        boolean hasSurfaceMaterial(
                ColumnKey column,
                int y,
                RoadSolidLayer expectedLayer,
                String expectedMaterialId) {
            BlockPos pos = columnPos(column, y);
            BlockRecord record = records.get(pos);
            RoadSolidLayer placedLayer = layers.get(pos);
            return record != null
                && matchesPlannedSurface(
                    record.newBlockId, placedLayer, expectedLayer, expectedMaterialId);
        }

        boolean hasAir(ColumnKey column, int y) {
            BlockRecord record = records.get(columnPos(column, y));
            return record != null && "minecraft:air".equals(record.newBlockId);
        }

        boolean obstructionClearanceResolved(ColumnKey column, int y) {
            if (hasAir(column, y)) {
                return true;
            }
            BlockPos pos = columnPos(column, y);
            BlockRecord record = records.get(pos);
            if (record == null) {
                return false;
            }
            return isStructuralClearanceReplacement(layers.get(pos));
        }

        boolean expectationResolved(RoadBlockExpectation expectation) {
            BlockPos pos = expectation.position();
            ColumnKey column = new ColumnKey(pos.getX(), pos.getZ());
            int y = pos.getY();
            if (expectation.role() == RoadBlockExpectation.ExpectedRole.SURFACE) {
                return hasSurfaceMaterial(
                    column, y, expectation.expectedLayer(), expectation.expectedMaterialId())
                    || obstructionClearanceResolved(column, y);
            }
            return obstructionClearanceResolved(column, y);
        }
    }
}
