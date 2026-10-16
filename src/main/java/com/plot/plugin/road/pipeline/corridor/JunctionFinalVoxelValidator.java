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
            int crossLayerLeaks) {
        public static final PostMergeReport CLEAN = new PostMergeReport(0, 0, 0, 0, 0);
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
        SurfaceCounts surface = countSurfaceExpectations(manifest, aggregate);
        int protectedLosses = countProtectedLosses(manifest, aggregate);
        int crossLayerLeaks = countCrossLayerLeaks(
            manifest, aggregate, index, footprintColumns);
        int disconnected = countDisconnectedTunnelComponents(
            manifest, aggregate, index, footprintColumns);
        return new PostMergeReport(
            surface.missingColumns(),
            surface.semanticMismatches(),
            protectedLosses,
            disconnected,
            crossLayerLeaks);
    }

    private record SurfaceCounts(int missingColumns, int semanticMismatches) {
    }

    private static SurfaceCounts countSurfaceExpectations(
            JunctionValidationManifest manifest,
            RoadGenerationResult aggregate) {
        Set<ColumnKey> missingColumns = new HashSet<>();
        Set<ColumnKey> semanticColumns = new HashSet<>();
        for (JunctionSurfaceExpectation expectation : manifest.surfaceExpectations()) {
            if (expectation == null || expectation.position() == null) {
                continue;
            }
            BlockPos pos = expectation.position();
            ColumnKey column = new ColumnKey(pos.getX(), pos.getZ());
            if (!hasStructuralSurface(aggregate, pos)) {
                missingColumns.add(column);
                continue;
            }
            if (!hasSemanticSurface(aggregate, pos, expectation.expectedLayer())) {
                semanticColumns.add(column);
            }
        }
        return new SurfaceCounts(missingColumns.size(), semanticColumns.size());
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

    private static int countCrossLayerLeaks(
            JunctionValidationManifest manifest,
            RoadGenerationResult aggregate,
            PlacementRecordColumnIndex index,
            Set<ColumnKey> footprintColumns) {
        if (!manifest.hasGradeSeparation() || footprintColumns.isEmpty()) {
            return 0;
        }
        int floorY = manifest.elevatedDeckY() != null
            ? manifest.elevatedDeckY() - 1
            : manifest.junctionY();
        int upperBandEnd = manifest.elevatedDeckY() != null
            ? manifest.elevatedDeckY() + 8
            : Integer.MAX_VALUE;
        Set<BlockPos> protectedVolume = new HashSet<>(manifest.expectedProtectedBlocks());
        int leaks = 0;
        for (ColumnKey column : footprintColumns) {
            for (BlockPos pos : index.positionsInColumn(column.worldX(), column.worldZ())) {
                if (pos.getY() <= floorY || pos.getY() > upperBandEnd) {
                    continue;
                }
                if (!index.isTunnelAir(pos, aggregate)) {
                    continue;
                }
                if (protectedVolume.contains(pos) || isInUpperProtectedVolume(pos, protectedVolume, floorY)) {
                    leaks++;
                }
            }
        }
        return leaks;
    }

    private static boolean isInUpperProtectedVolume(
            BlockPos pos,
            Set<BlockPos> protectedBlocks,
            int floorY) {
        if (pos.getY() <= floorY) {
            return false;
        }
        for (BlockPos protectedPos : protectedBlocks) {
            if (protectedPos.getX() == pos.getX()
                    && protectedPos.getZ() == pos.getZ()
                    && pos.getY() >= protectedPos.getY() - 1
                    && pos.getY() <= protectedPos.getY() + 3) {
                return true;
            }
        }
        return false;
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
