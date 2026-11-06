package com.plot.plugin.road.pipeline.corridor;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.construction.RoadEarthworkPolicy;
import com.plot.plugin.road.tunnel.TunnelPortalExcavationMask;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.solid.RoadSolidPrimitive;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
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
            int needsRetainingWallCount,
            int roadShoulderSeamGapCount,
            int shoulderTraceGapCount,
            int structureInteriorSlopeCellCount,
            /** 开放走廊路面下方无填方/地形支撑（悬空候选；桥隧列不计）。 */
            int unsupportedPavementCount) {
        public static final IntegrityReport ZERO = new IntegrityReport(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        public boolean isClean() {
            return missingRoadSurfaceCount == 0
                && unclearedObstructionCount == 0
                && orphanSlopePillarCount == 0
                && unsupportedPavementCount == 0;
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
        int seamGaps = countRoadShoulderSeamGaps(plan);
        int unsupportedPavement = countUnsupportedPavement(plan, index, terrain);
        plan.setBrokenSlopeSurfaceCount(brokenSlopes + lateralGaps);
        plan.setRoadShoulderSeamGapCount(seamGaps);
        return new IntegrityReport(
            missingSurface,
            uncleared,
            orphanPillars,
            plan.slopeBatterWidthCapCount(),
            brokenSlopes + lateralGaps,
            plan.unresolvedSlopeConstructionCount(),
            plan.needsRetainingWallCount(),
            seamGaps,
            traceGaps,
            structureInteriorSlopes,
            unsupportedPavement);
    }

    public static IntegrityReport validatePlacements(
            RoadCorridorPlan plan,
            RoadGenerationResult result,
            TerrainSampler terrain) {
        return validatePlacements(plan, result, terrain, null);
    }

    public static IntegrityReport validatePlacements(
            RoadCorridorPlan plan,
            RoadGenerationResult result,
            TerrainSampler terrain,
            RoadCorridorPlanBuilder.PlanHost host) {
        if (plan == null || result == null || terrain == null) {
            return IntegrityReport.ZERO;
        }
        PlacementIndex index = PlacementIndex.from(result);
        int missingSurface = countMissingRoadSurfaces(plan, index);
        int uncleared = countUnclearedObstructions(plan, index, terrain, host);
        int orphanPillars = countOrphanSlopePillars(plan);
        int brokenSlopes = countBrokenSlopeSurfaces(plan);
        int lateralGaps = countSlopeLateralGaps(plan);
        int traceGaps = SlopeShoulderTerrainTraceValidator.countShoulderToTerrainGaps(plan, terrain);
        int structureInteriorSlopes = countStructureInteriorSlopeCells(plan);
        int seamGaps = countRoadShoulderSeamGaps(plan);
        int unsupportedPavement = countUnsupportedPavement(plan, index, terrain);
        plan.setBrokenSlopeSurfaceCount(brokenSlopes + lateralGaps);
        plan.setRoadShoulderSeamGapCount(seamGaps);
        return new IntegrityReport(
            missingSurface,
            uncleared,
            orphanPillars,
            plan.slopeBatterWidthCapCount(),
            brokenSlopes + lateralGaps,
            plan.unresolvedSlopeConstructionCount(),
            plan.needsRetainingWallCount(),
            seamGaps,
            traceGaps,
            structureInteriorSlopes,
            unsupportedPavement);
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
        applyPlacementsToResult(result, plan, terrain, null);
    }

    public static void applyPlacementsToResult(
            RoadGenerationResult result,
            RoadCorridorPlan plan,
            TerrainSampler terrain,
            RoadCorridorPlanBuilder.PlanHost host) {
        if (result == null) {
            return;
        }
        applyReportToResult(result, validatePlacements(plan, result, terrain, host));
    }

    /** 测试诊断：列出未清除的妨碍体坐标。 */
    static List<String> describeUnclearedPlacements(
            RoadCorridorPlan plan,
            RoadGenerationResult result,
            TerrainSampler terrain,
            RoadCorridorPlanBuilder.PlanHost host) {
        if (plan == null || result == null || terrain == null) {
            return List.of();
        }
        PlacementIndex index = PlacementIndex.from(result);
        List<String> lines = new ArrayList<>();
        for (var entry : plan.clearanceEntries()) {
            ColumnKey key = entry.getKey();
            ClearanceCell cell = entry.getValue();
            if (cell.mode() == RoadCorridorConstructionMode.TUNNEL) {
                continue;
            }
            if (!terrain.isChunkLoaded(key.worldX(), key.worldZ())) {
                continue;
            }
            for (int y : clearanceYsToValidate(plan, key, cell, host)) {
                RoadBlockExpectation expectation = RoadBlockExpectation.clearance(key, y);
                if (!RoadCorridorClearancePass.hasObstruction(terrain, key.worldX(), y, key.worldZ())) {
                    continue;
                }
                if (!index.expectationResolved(expectation)) {
                    BlockPos pos = columnPos(key, y);
                    lines.add(pos + " mode=" + cell.mode() + " type=" + cell.constructionType()
                        + " designY=" + cell.designSurfaceY() + " clearTop=" + cell.clearTopY()
                        + " placed=" + result.placementRecords.getOrDefault(pos, null)
                        + " layer=" + result.placementLayers.get(pos));
                }
            }
        }
        return lines;
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
        result.needsRetainingWallCount = report.needsRetainingWallCount();
        result.roadShoulderSeamGapCount = report.roadShoulderSeamGapCount();
        result.shoulderTraceGapCount = report.shoulderTraceGapCount();
        result.structureInteriorSlopeCellCount = report.structureInteriorSlopeCellCount();
        result.unsupportedPavementCount = report.unsupportedPavementCount();
    }

    /**
     * Detects holes in the lateral road surface band where a ROAD column lacks an adjacent
     * surface cell despite facing another covered column across the gap.
     */
    static int countRoadShoulderSeamGaps(RoadCorridorPlan plan) {
        int gaps = 0;
        for (var entry : plan.surfaceMask().entries()) {
            SurfaceCell cell = entry.getValue();
            if (cell.layer() != RoadSolidLayer.ROAD) {
                continue;
            }
            ColumnKey key = entry.getKey();
            for (int[] offset : NEIGHBOR_OFFSETS) {
                ColumnKey neighborKey = new ColumnKey(key.worldX() + offset[0], key.worldZ() + offset[1]);
                if (plan.surfaceMask().get(neighborKey) != null) {
                    continue;
                }
                ColumnKey oppositeKey = new ColumnKey(key.worldX() - offset[0], key.worldZ() - offset[1]);
                SurfaceCell opposite = plan.surfaceMask().get(oppositeKey);
                if (opposite == null || !plan.surfaceMask().isRoadSurfaceLayer(opposite.layer())) {
                    continue;
                }
                if (!isLateralBandHole(plan, neighborKey, offset)) {
                    continue;
                }
                if (!touchesShoulderBand(plan, neighborKey)) {
                    continue;
                }
                gaps++;
            }
        }
        return gaps;
    }

    /**
     * True when the empty cell sits in a lateral band (both along-road neighbors covered),
     * as opposed to an open end of the corridor trace.
     */
    private static boolean isLateralBandHole(RoadCorridorPlan plan, ColumnKey emptyKey, int[] gapOffset) {
        int alongDx = gapOffset[1];
        int alongDz = gapOffset[0];
        ColumnKey before = new ColumnKey(emptyKey.worldX() - alongDx, emptyKey.worldZ() - alongDz);
        ColumnKey after = new ColumnKey(emptyKey.worldX() + alongDx, emptyKey.worldZ() + alongDz);
        return hasRoadBandSurface(plan, before) && hasRoadBandSurface(plan, after);
    }

    private static boolean hasRoadBandSurface(RoadCorridorPlan plan, ColumnKey key) {
        SurfaceCell cell = plan.surfaceMask().get(key);
        return cell != null && plan.surfaceMask().isRoadSurfaceLayer(cell.layer());
    }

    private static boolean touchesShoulderBand(RoadCorridorPlan plan, ColumnKey emptyKey) {
        for (int[] offset : NEIGHBOR_OFFSETS) {
            ColumnKey neighbor = new ColumnKey(emptyKey.worldX() + offset[0], emptyKey.worldZ() + offset[1]);
            SurfaceCell cell = plan.surfaceMask().get(neighbor);
            if (cell != null && cell.layer() == RoadSolidLayer.SHOULDER) {
                return true;
            }
        }
        return false;
    }

    /**
     * Counts outer SHOULDER columns that lack a slope cell on the same column or a cardinal neighbor.
     */
    static int countShoulderToSlopeStartGaps(RoadCorridorPlan plan) {
        int gaps = 0;
        for (var entry : plan.surfaceMask().entries()) {
            SurfaceCell cell = entry.getValue();
            if (cell.layer() != RoadSolidLayer.SHOULDER) {
                continue;
            }
            if (!isExposedOuterShoulder(plan, entry.getKey())) {
                continue;
            }
            if (hasAdjacentSlopeCell(plan, entry.getKey())) {
                continue;
            }
            gaps++;
        }
        return gaps;
    }

    private static boolean isExposedOuterShoulder(RoadCorridorPlan plan, ColumnKey key) {
        for (int[] offset : NEIGHBOR_OFFSETS) {
            ColumnKey neighbor = new ColumnKey(key.worldX() + offset[0], key.worldZ() + offset[1]);
            SurfaceCell cell = plan.surfaceMask().get(neighbor);
            if (cell == null || !plan.surfaceMask().isRoadSurfaceLayer(cell.layer())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAdjacentSlopeCell(RoadCorridorPlan plan, ColumnKey key) {
        if (plan.slopeCell(key) != null) {
            return true;
        }
        for (int[] offset : NEIGHBOR_OFFSETS) {
            ColumnKey neighbor = new ColumnKey(key.worldX() + offset[0], key.worldZ() + offset[1]);
            if (plan.slopeCell(neighbor) != null) {
                return true;
            }
        }
        return false;
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

    /**
     * 路面下方无有效支撑则计数。
     * <ul>
     *   <li>OPEN：生成空气优先；否则生成实体或原世界实体</li>
     *   <li>BRIDGE：必须有本次生成的桥面板/结构实体（不认谷底原地形）</li>
     *   <li>TUNNEL/PORTAL/SHELTER：腔体语义另验，此处跳过</li>
     * </ul>
     */
    static int countUnsupportedPavement(
            RoadCorridorPlan plan,
            SolidIndex index,
            TerrainSampler terrain) {
        int unsupported = 0;
        for (var entry : plan.surfaceMask().entries()) {
            SurfaceCell cell = entry.getValue();
            ClearanceCell clearance = plan.clearanceCell(entry.getKey());
            if (!shouldCheckPavementSupport(cell, clearance)) {
                continue;
            }
            ColumnKey key = entry.getKey();
            if (!terrain.isChunkLoaded(key.worldX(), key.worldZ())) {
                continue;
            }
            if (hasPavementSupport(key, cell, clearance, index, terrain)) {
                continue;
            }
            unsupported++;
        }
        return unsupported;
    }

    static int countUnsupportedPavement(
            RoadCorridorPlan plan,
            PlacementIndex index,
            TerrainSampler terrain) {
        int unsupported = 0;
        for (var entry : plan.surfaceMask().entries()) {
            SurfaceCell cell = entry.getValue();
            ClearanceCell clearance = plan.clearanceCell(entry.getKey());
            if (!shouldCheckPavementSupport(cell, clearance)) {
                continue;
            }
            ColumnKey key = entry.getKey();
            if (!terrain.isChunkLoaded(key.worldX(), key.worldZ())) {
                continue;
            }
            if (hasPavementSupport(key, cell, clearance, index, terrain)) {
                continue;
            }
            unsupported++;
        }
        return unsupported;
    }

    /**
     * OPEN 检查行车道与外侧条带；BRIDGE 仅检查行车道（桥面板按桥面总宽铺，路肩列由接缝测试覆盖）。
     */
    private static boolean shouldCheckPavementSupport(SurfaceCell cell, ClearanceCell clearance) {
        if (cell == null) {
            return false;
        }
        if (isCavityStructureMode(clearance)) {
            return false;
        }
        if (isBridgeMode(clearance)) {
            return cell.layer() == RoadSolidLayer.ROAD;
        }
        return cell.layer() == RoadSolidLayer.ROAD
            || cell.layer() == RoadSolidLayer.SHOULDER
            || cell.layer() == RoadSolidLayer.SIDEWALK
            || cell.layer() == RoadSolidLayer.BIKE_LANE;
    }

    /** 隧/洞门/棚洞：底板与衬砌由专用质量测试覆盖。 */
    private static boolean isCavityStructureMode(ClearanceCell clearance) {
        if (clearance == null) {
            return false;
        }
        return clearance.mode() == RoadCorridorConstructionMode.TUNNEL
            || clearance.mode() == RoadCorridorConstructionMode.PORTAL
            || clearance.mode() == RoadCorridorConstructionMode.SHELTER;
    }

    private static boolean isBridgeMode(ClearanceCell clearance) {
        return clearance != null && clearance.mode() == RoadCorridorConstructionMode.BRIDGE;
    }

    private static boolean hasPavementSupport(
            ColumnKey key,
            SurfaceCell cell,
            ClearanceCell clearance,
            SolidIndex index,
            TerrainSampler terrain) {
        int belowY = cell.surfaceY() - 1;
        // 生成记录优先：本格已有写入时（含空气/流体/装饰）不再回退原世界。
        if (index.hasGeneratedOccupant(key, belowY)) {
            return index.hasStructuralSupport(key, belowY);
        }
        // 桥面板要求本列承重；局部漏板不得被邻列掩盖。
        if (isBridgeMode(clearance)) {
            return false;
        }
        return terrain.isSolidBlock(key.worldX(), belowY, key.worldZ());
    }

    private static boolean hasPavementSupport(
            ColumnKey key,
            SurfaceCell cell,
            ClearanceCell clearance,
            PlacementIndex index,
            TerrainSampler terrain) {
        int belowY = cell.surfaceY() - 1;
        if (index.hasGeneratedOccupant(key, belowY)) {
            return index.hasStructuralSupport(key, belowY);
        }
        if (isBridgeMode(clearance)) {
            return false;
        }
        return terrain.isSolidBlock(key.worldX(), belowY, key.worldZ());
    }

    private static int countUnclearedObstructions(
            RoadCorridorPlan plan,
            SolidIndex index,
            TerrainSampler terrain) {
        return countUnclearedObstructions(plan, index, terrain, null);
    }

    private static int countUnclearedObstructions(
            RoadCorridorPlan plan,
            PlacementIndex index,
            TerrainSampler terrain) {
        return countUnclearedObstructions(plan, index, terrain, null);
    }

    private static int countUnclearedObstructions(
            RoadCorridorPlan plan,
            SolidIndex index,
            TerrainSampler terrain,
            RoadCorridorPlanBuilder.PlanHost host) {
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
            for (int y : clearanceYsToValidate(plan, key, cell, host)) {
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
            TerrainSampler terrain,
            RoadCorridorPlanBuilder.PlanHost host) {
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
            for (int y : clearanceYsToValidate(plan, key, cell, host)) {
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
     * Portal / transition clearance Ys must match {@link RoadCorridorClearancePass}:
     * PORTAL columns only clear cavity AIR; OPEN columns near a portal roof are capped
     * by {@link PortalTransitionGuard} so peaked shells are not required to excavate above the crown.
     * Final surface mask is the sole clearance floor when present.
     */
    private static List<Integer> clearanceYsToValidate(
            RoadCorridorPlan plan,
            ColumnKey key,
            ClearanceCell cell,
            RoadCorridorPlanBuilder.PlanHost host) {
        int designY = cell.designSurfaceY();
        if (plan != null && key != null) {
            SurfaceCell surface = plan.surfaceMask().get(key);
            if (surface != null) {
                designY = surface.surfaceY();
            }
        }
        int topY = Math.max(cell.clearTopY(), designY);
        List<Integer> ys = new ArrayList<>();
        if (cell.mode() == RoadCorridorConstructionMode.PORTAL
                && cell.corridorLateral() != null
                && host != null) {
            int envelopeWidth = cell.envelopeWidthBlocks() != null && cell.envelopeWidthBlocks() > 0
                ? cell.envelopeWidthBlocks()
                : RoadCorridorWidth.gradingEnvelopeWidthBlocks(
                    ResolvedCrossSection.fromConfig(host.config()));
            TunnelPortalExcavationMask mask = TunnelPortalExcavationMask.forPortal(
                host.tunnelStyle(), envelopeWidth, designY);
            for (int y = designY + 1; y <= topY; y++) {
                if (mask.allowsClearance(cell.corridorLateral(), y)) {
                    ys.add(y);
                }
            }
            return ys;
        }
        if (host != null && plan != null && cell.mode() != RoadCorridorConstructionMode.PORTAL) {
            ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(host.config());
            int roadHalfWidth = Math.abs(
                com.plot.plugin.road.RoadDimensionUtils.maxLateralOffset(
                    RoadCorridorWidth.gradingEnvelopeWidthBlocks(crossSection)));
            PortalTransitionGuard portalGuard = PortalTransitionGuard.from(plan);
            topY = portalGuard.capClearTopY(cell.planPoint(), topY, host, roadHalfWidth);
        }
        for (int y = designY + 1; y <= topY; y++) {
            ys.add(y);
        }
        return ys;
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
                if (RoadPavementSupportMaterials.isAirId(primitive.materialId())
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

        boolean hasGeneratedOccupant(ColumnKey column, int y) {
            return byPosition.containsKey(columnPos(column, y));
        }

        boolean hasAir(ColumnKey column, int y) {
            if (airPositions.contains(columnPos(column, y))) {
                return true;
            }
            RoadSolidPrimitive primitive = byPosition.get(columnPos(column, y));
            return primitive != null && RoadPavementSupportMaterials.isAirId(primitive.materialId());
        }

        boolean hasStructuralSupport(ColumnKey column, int y) {
            RoadSolidPrimitive primitive = byPosition.get(columnPos(column, y));
            return primitive != null
                && RoadPavementSupportMaterials.isGeneratedStructuralSupport(
                    primitive.layer(), primitive.materialId());
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

        boolean hasGeneratedOccupant(ColumnKey column, int y) {
            return records.containsKey(columnPos(column, y));
        }

        boolean hasAir(ColumnKey column, int y) {
            BlockRecord record = records.get(columnPos(column, y));
            return record != null && RoadPavementSupportMaterials.isAirId(record.newBlockId);
        }

        boolean hasStructuralSupport(ColumnKey column, int y) {
            BlockPos pos = columnPos(column, y);
            BlockRecord record = records.get(pos);
            if (record == null) {
                return false;
            }
            return RoadPavementSupportMaterials.isGeneratedStructuralSupport(
                layers.get(pos), record.newBlockId);
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
