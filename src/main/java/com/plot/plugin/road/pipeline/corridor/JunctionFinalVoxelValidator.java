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
            int protectedLosses,
            int disconnectedTunnelComponents,
            int crossLayerLeaks) {
        public static final PostMergeReport CLEAN = new PostMergeReport(0, 0, 0, 0);
    }

    public static PostMergeReport validateNode(
            JunctionValidationManifest manifest,
            RoadGenerationResult aggregate,
            PlacementRecordColumnIndex index,
            ICoordinateService coordinateTransformer) {
        if (manifest == null || aggregate == null || index == null) {
            return PostMergeReport.CLEAN;
        }
        int missingSurface = countMissingSurfaceExpectations(manifest, aggregate);
        int protectedLosses = countProtectedLosses(manifest, aggregate);
        int crossLayerLeaks = countCrossLayerLeaks(manifest, aggregate, index, coordinateTransformer);
        int disconnected = countDisconnectedTunnelComponents(manifest, aggregate, index, coordinateTransformer);
        return new PostMergeReport(missingSurface, protectedLosses, disconnected, crossLayerLeaks);
    }

    private static int countMissingSurfaceExpectations(
            JunctionValidationManifest manifest,
            RoadGenerationResult aggregate) {
        Set<ColumnKey> missingColumns = new HashSet<>();
        for (JunctionSurfaceExpectation expectation : manifest.surfaceExpectations()) {
            if (expectation == null || expectation.position() == null) {
                continue;
            }
            if (!hasMatchingSurface(aggregate, expectation.position(), expectation.expectedLayer())) {
                missingColumns.add(new ColumnKey(
                    expectation.position().getX(),
                    expectation.position().getZ()));
            }
        }
        return missingColumns.size();
    }

    private static boolean hasMatchingSurface(
            RoadGenerationResult aggregate,
            BlockPos pos,
            RoadSolidLayer expectedLayer) {
        BlockRecord record = aggregate.placementRecords.get(pos);
        if (record == null || "minecraft:air".equals(record.newBlockId)) {
            return false;
        }
        RoadSolidLayer layer = aggregate.placementLayers.get(pos);
        if (layer == null) {
            return expectedLayer == RoadSolidLayer.ROAD;
        }
        if (layer == expectedLayer) {
            return true;
        }
        return isSurfaceFamily(layer) && isSurfaceFamily(expectedLayer);
    }

    private static boolean isSurfaceFamily(RoadSolidLayer layer) {
        return layer == RoadSolidLayer.ROAD
            || layer == RoadSolidLayer.SIDEWALK
            || layer == RoadSolidLayer.SHOULDER
            || layer == RoadSolidLayer.BIKE_LANE
            || layer == RoadSolidLayer.MEDIAN
            // Stop lines / markings overwrite plaza ROAD at the same BlockPos after stamp.
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
            ICoordinateService coordinateTransformer) {
        if (!manifest.hasGradeSeparation() || manifest.footprintPolygon().size() < 3) {
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
        for (BlockPos pos : aggregate.placementRecords.keySet()) {
            if (pos.getY() <= floorY || pos.getY() > upperBandEnd) {
                continue;
            }
            Vec2d planPoint = blockToPlan(pos, coordinateTransformer);
            if (!RoadGeometryUtils.pointInPolygon(planPoint, manifest.footprintPolygon())) {
                continue;
            }
            if (!index.isTunnelAir(pos, aggregate)) {
                continue;
            }
            if (protectedVolume.contains(pos) || isInUpperProtectedVolume(pos, protectedVolume, floorY)) {
                leaks++;
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
            ICoordinateService coordinateTransformer) {
        if (!manifest.hasTunnelValidation() || manifest.footprintPolygon().size() < 3) {
            return 0;
        }
        Set<BlockPos> openVolume = collectPostMergeOpenVolume(manifest, aggregate, index, coordinateTransformer);
        if (openVolume.isEmpty()) {
            return 0;
        }
        List<BlockPos> seeds = new ArrayList<>();
        for (TunnelPortalSeed seed : manifest.tunnelPortalSeeds()) {
            seeds.add(seed.walkableAirPosition());
        }
        if (seeds.isEmpty()) {
            return 0;
        }
        TunnelJunctionAirConnectivityValidator.Result flood =
            TunnelJunctionAirConnectivityValidator.validate(openVolume, seeds);
        return flood.disconnectedComponentCount();
    }

    private static Set<BlockPos> collectPostMergeOpenVolume(
            JunctionValidationManifest manifest,
            RoadGenerationResult aggregate,
            PlacementRecordColumnIndex index,
            ICoordinateService coordinateTransformer) {
        Set<BlockPos> openVolume = new HashSet<>();
        if (manifest.footprintPolygon().size() < 3) {
            return openVolume;
        }
        for (var entry : aggregate.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getY() < manifest.underpassYMin() || pos.getY() > manifest.underpassYMax()) {
                continue;
            }
            Vec2d planPoint = blockToPlan(pos, coordinateTransformer);
            if (!RoadGeometryUtils.pointInPolygon(planPoint, manifest.footprintPolygon())) {
                continue;
            }
            if (index.isTunnelAir(pos, aggregate)) {
                openVolume.add(pos);
            }
        }
        return openVolume;
    }

    private static Vec2d blockToPlan(BlockPos pos, ICoordinateService coordinateTransformer) {
        if (coordinateTransformer != null) {
            Vec2d world = new Vec2d(pos.getX(), pos.getZ());
            var snapshot = coordinateTransformer.captureProjection();
            if (snapshot != null && snapshot.isValid()) {
                return snapshot.toCanvas(world);
            }
        }
        return new Vec2d(pos.getX(), pos.getZ());
    }
}
