package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Post-merge validation: aggregate placement records must match junction design intent.
 */
public final class JunctionFinalVoxelValidator {
    private JunctionFinalVoxelValidator() {
    }

    public record PostMergeReport(
            int missingSurfaceColumns,
            int surfaceSemanticMismatches,
            int protectedLosses,
            int disconnectedTunnelComponents,
            int crossLayerLeaks,
            /** 期望路面 Y 缺失，但同列另有路面层——典型 merge/Y 错位镂空。 */
            int surfaceYMismatches,
            /** 设计路面以上未授权实体（落顶衬砌 / 悬空残留等）。 */
            int undesignedProtrusions) {
        public static final PostMergeReport CLEAN = new PostMergeReport(0, 0, 0, 0, 0, 0, 0);
    }

    public static PostMergeReport validateNode(
            JunctionValidationManifest manifest,
            RoadGenerationResult aggregate,
            PlacementRecordColumnIndex index,
            ICoordinateService coordinateTransformer) {
        if (manifest == null || aggregate == null || index == null) {
            return PostMergeReport.CLEAN;
        }
        Set<ColumnKey> footprintColumns = footprintColumns(manifest, coordinateTransformer);
        SurfaceExpectationCounts surface = countSurfaceExpectations(manifest, aggregate, index);
        SurfaceCounts expectedPavement = countExpectedPavement(aggregate, footprintColumns);
        int protectedLosses = countProtectedLosses(manifest, aggregate);
        int crossLayerLeaks = countCrossLayerLeaks(
            manifest, aggregate, index, footprintColumns);
        int disconnected = countDisconnectedTunnelComponents(
            manifest, aggregate, index, footprintColumns);
        int protrusions = countUndesignedProtrusions(manifest, aggregate, index);
        return new PostMergeReport(
            surface.missingColumns() + expectedPavement.missingColumns(),
            surface.semanticMismatches() + expectedPavement.semanticMismatches(),
            protectedLosses,
            disconnected,
            crossLayerLeaks,
            surface.yMismatches(),
            protrusions);
    }

    /**
     * 对照设计期望路面（{@link ExpectedRoadSurface}）：缺失或错材质计入缺口/语义偏差。
     */
    static SurfaceCounts countExpectedPavement(
            RoadGenerationResult aggregate,
            Set<ColumnKey> footprintColumns) {
        if (aggregate == null
                || aggregate.expectedRoadSurfaces.isEmpty()
                || footprintColumns == null
                || footprintColumns.isEmpty()) {
            return new SurfaceCounts(0, 0);
        }
        Set<ColumnKey> missing = new HashSet<>();
        Set<ColumnKey> wrongMaterial = new HashSet<>();
        for (ExpectedRoadSurface expected : aggregate.expectedRoadSurfaces.values()) {
            if (expected == null || !expected.isCarriageway()) {
                continue;
            }
            BlockPos pos = expected.position();
            ColumnKey column = new ColumnKey(pos.getX(), pos.getZ());
            if (!footprintColumns.contains(column)) {
                continue;
            }
            BlockRecord actual = aggregate.placementRecords.get(pos);
            if (actual == null || "minecraft:air".equals(actual.newBlockId)) {
                missing.add(column);
                continue;
            }
            RoadSolidLayer layer = aggregate.placementLayers.get(pos);
            if (layer == RoadSolidLayer.MARKING) {
                continue;
            }
            if (!expected.materialId().equals(actual.newBlockId)) {
                wrongMaterial.add(column);
            }
        }
        return new SurfaceCounts(missing.size(), wrongMaterial.size());
    }

    record SurfaceCounts(int missingColumns, int semanticMismatches) {
    }

    record SurfaceExpectationCounts(int missingColumns, int semanticMismatches, int yMismatches) {
    }

    private static SurfaceExpectationCounts countSurfaceExpectations(
            JunctionValidationManifest manifest,
            RoadGenerationResult aggregate,
            PlacementRecordColumnIndex index) {
        Set<ColumnKey> missingColumns = new HashSet<>();
        Set<ColumnKey> semanticColumns = new HashSet<>();
        Set<ColumnKey> yMismatchColumns = new HashSet<>();
        for (JunctionSurfaceExpectation expectation : manifest.surfaceExpectations()) {
            if (expectation == null || expectation.position() == null) {
                continue;
            }
            BlockPos pos = expectation.position();
            ColumnKey column = new ColumnKey(pos.getX(), pos.getZ());
            if (hasStructuralSurface(aggregate, pos)) {
                if (!hasSemanticSurface(aggregate, pos, expectation.expectedLayer())) {
                    semanticColumns.add(column);
                }
                continue;
            }
            // 期望 Y 无路面：同列若另有路面，记为 Y/merge 错位，而非「整列缺铺」
            if (hasStructuralSurfaceElsewhereInColumn(aggregate, index, column, pos.getY())) {
                yMismatchColumns.add(column);
            } else {
                missingColumns.add(column);
            }
        }
        return new SurfaceExpectationCounts(
            missingColumns.size(), semanticColumns.size(), yMismatchColumns.size());
    }

    /**
     * 同世界列在非期望 Y 上是否仍有广场路面层（诊断 merge 把路面留在错误高度）。
     */
    static boolean hasStructuralSurfaceElsewhereInColumn(
            RoadGenerationResult aggregate,
            PlacementRecordColumnIndex index,
            ColumnKey column,
            int expectedY) {
        if (aggregate == null || index == null || column == null) {
            return false;
        }
        for (BlockPos candidate : index.positionsInColumn(column.worldX(), column.worldZ())) {
            if (candidate == null || candidate.getY() == expectedY) {
                continue;
            }
            if (hasStructuralSurface(aggregate, candidate)) {
                return true;
            }
        }
        return false;
    }

    /** Non-air placement in the plaza surface family (including MARKING overlay). */
    static boolean hasStructuralSurface(RoadGenerationResult aggregate, BlockPos pos) {
        BlockRecord record = aggregate.placementRecords.get(pos);
        if (record == null || "minecraft:air".equals(record.newBlockId)) {
            return false;
        }
        RoadSolidLayer layer = aggregate.placementLayers.get(pos);
        if (layer == null) {
            return true;
        }
        return isPlazaSurfaceLayer(layer);
    }

    /**
     * Exact expected layer, or MARKING overlay on a plaza surface expectation.
     * ROAD must not silently substitute for SIDEWALK/SHOULDER/etc.
     */
    static boolean hasSemanticSurface(
            RoadGenerationResult aggregate,
            BlockPos pos,
            RoadSolidLayer expectedLayer) {
        RoadSolidLayer layer = aggregate.placementLayers.get(pos);
        if (layer == null) {
            return expectedLayer == RoadSolidLayer.ROAD;
        }
        if (layer == expectedLayer) {
            return true;
        }
        return layer == RoadSolidLayer.MARKING && isPlazaSurfaceLayer(expectedLayer);
    }

    private static boolean isPlazaSurfaceLayer(RoadSolidLayer layer) {
        return layer == RoadSolidLayer.ROAD
            || layer == RoadSolidLayer.SIDEWALK
            || layer == RoadSolidLayer.SHOULDER
            || layer == RoadSolidLayer.BIKE_LANE
            || layer == RoadSolidLayer.MEDIAN
            || layer == RoadSolidLayer.MARKING;
    }

    private static int countProtectedLosses(
            JunctionValidationManifest manifest,
            RoadGenerationResult aggregate) {
        int losses = 0;
        for (BlockPos expected : manifest.expectedProtectedBlocks()) {
            BlockRecord record = aggregate.placementRecords.get(expected);
            if (record == null || "minecraft:air".equals(record.newBlockId)) {
                losses++;
            }
        }
        return losses;
    }

    /**
     * 设计路面以上的未授权实体：不计 MARKING、广场面层（已由 Y mismatch 覆盖）、
     * 受保护结构与期望隧道衬砌。
     */
    static int countUndesignedProtrusions(
            JunctionValidationManifest manifest,
            RoadGenerationResult aggregate,
            PlacementRecordColumnIndex index) {
        if (manifest == null || aggregate == null || index == null) {
            return 0;
        }
        Set<BlockPos> protectedBlocks = new HashSet<>(manifest.expectedProtectedBlocks());
        Set<BlockPos> expectedLining = aggregate.expectedTunnelLining != null
            ? aggregate.expectedTunnelLining
            : Set.of();
        Set<BlockPos> protrusions = new HashSet<>();
        for (JunctionSurfaceExpectation expectation : manifest.surfaceExpectations()) {
            if (expectation == null || expectation.position() == null) {
                continue;
            }
            BlockPos surface = expectation.position();
            int expectedY = surface.getY();
            for (BlockPos candidate : index.positionsInColumn(surface.getX(), surface.getZ())) {
                if (candidate == null || candidate.getY() <= expectedY) {
                    continue;
                }
                BlockRecord record = aggregate.placementRecords.get(candidate);
                if (record == null || "minecraft:air".equals(record.newBlockId)) {
                    continue;
                }
                RoadSolidLayer layer = aggregate.placementLayers.get(candidate);
                if (layer == RoadSolidLayer.MARKING || isPlazaSurfaceLayer(layer)) {
                    continue;
                }
                if (protectedBlocks.contains(candidate) || expectedLining.contains(candidate)) {
                    continue;
                }
                protrusions.add(candidate);
            }
        }
        return protrusions.size();
    }

    private static int countCrossLayerLeaks(
            JunctionValidationManifest manifest,
            RoadGenerationResult aggregate,
            PlacementRecordColumnIndex index,
            Set<ColumnKey> footprintColumns) {
        if (!manifest.hasGradeSeparation() || footprintColumns.isEmpty()) {
            return 0;
        }
        int leaks = 0;
        // The manifest lists actual structure blocks. Empty space above a deck is
        // legitimate upper-road clearance, even when its record uses the TUNNEL layer.
        for (BlockPos pos : new HashSet<>(manifest.expectedProtectedBlocks())) {
            if (footprintColumns.contains(new ColumnKey(pos.getX(), pos.getZ()))
                    && index.isTunnelAir(pos, aggregate)) leaks++;
        }
        return leaks;
    }

    private static int countDisconnectedTunnelComponents(
            JunctionValidationManifest manifest,
            RoadGenerationResult aggregate,
            PlacementRecordColumnIndex index,
            Set<ColumnKey> footprintColumns) {
        if (!manifest.hasTunnelValidation() || footprintColumns.isEmpty()) {
            return 0;
        }
        List<BlockPos> seeds = new ArrayList<>();
        for (TunnelPortalSeed seed : manifest.tunnelPortalSeeds()) {
            if (seed != null && seed.walkableAirPosition() != null) {
                seeds.add(seed.walkableAirPosition());
            }
        }
        if (seeds.size() < 2) {
            return 0;
        }
        Set<BlockPos> openVolume = collectPostMergeOpenVolume(
            manifest, aggregate, index, footprintColumns);
        if (openVolume.isEmpty()) {
            return seeds.size();
        }
        int invalidSeeds = 0;
        List<BlockPos> validSeeds = new ArrayList<>();
        for (BlockPos seed : seeds) {
            if (openVolume.contains(seed)) {
                validSeeds.add(seed);
            } else {
                invalidSeeds++;
            }
        }
        if (validSeeds.isEmpty()) {
            return seeds.size();
        }
        TunnelJunctionAirConnectivityValidator.Result flood =
            TunnelJunctionAirConnectivityValidator.validate(openVolume, validSeeds);
        return flood.disconnectedComponentCount() + invalidSeeds;
    }

    private static Set<BlockPos> collectPostMergeOpenVolume(
            JunctionValidationManifest manifest,
            RoadGenerationResult aggregate,
            PlacementRecordColumnIndex index,
            Set<ColumnKey> footprintColumns) {
        Set<BlockPos> openVolume = new HashSet<>();
        for (ColumnKey column : footprintColumns) {
            for (BlockPos pos : index.positionsInColumn(column.worldX(), column.worldZ())) {
                if (pos.getY() < manifest.underpassYMin() || pos.getY() > manifest.underpassYMax()) {
                    continue;
                }
                if (index.isTunnelAir(pos, aggregate)) {
                    openVolume.add(pos);
                }
            }
        }
        return openVolume;
    }

    private static Set<ColumnKey> footprintColumns(
            JunctionValidationManifest manifest,
            ICoordinateService coordinateTransformer) {
        Set<ColumnKey> columns = new HashSet<>();
        if (manifest.footprintPolygon() == null || manifest.footprintPolygon().size() < 3) {
            return columns;
        }
        List<Vec2d> cells = RoadGeometryUtils.collectWorldCellPlanPoints(
            manifest.footprintPolygon(), coordinateTransformer);
        for (Vec2d cell : cells) {
            if (cell == null) {
                continue;
            }
            BlockPos xz = RoadGeometryUtils.canvasToBlockXZ(cell, coordinateTransformer);
            columns.add(new ColumnKey(xz.getX(), xz.getZ()));
        }
        return columns;
    }

}
