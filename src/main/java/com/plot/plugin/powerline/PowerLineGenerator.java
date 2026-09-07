package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.core.command.BlockRecord;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * 电力线路生成器：立杆 + 下垂导线。
 */
public class PowerLineGenerator {
    private static final int CLEARANCE_MARGIN = 1;
    private static final int WIRE_SAMPLES_PER_BLOCK = 1;

    private final ICoordinateService coordinateTransformer;
    private final IBlockProjectionService projectionHandler;

    public PowerLineGenerator(
            ICoordinateService coordinateTransformer,
            IBlockProjectionService projectionHandler) {
        this.coordinateTransformer = java.util.Objects.requireNonNull(
            coordinateTransformer, "coordinateTransformer");
        this.projectionHandler = java.util.Objects.requireNonNull(
            projectionHandler, "projectionHandler");
    }

    public PowerLineGenerationResult generate(PowerLineFootprint footprint, TerrainSampler terrain) {
        PowerLineGenerationResult result = new PowerLineGenerationResult(footprint);
        if (footprint == null || terrain == null) {
            return result;
        }

        List<Vec2d> polePositions = PowerPoleLayoutUtils.computePolePositions(
            footprint.getPathPoints(),
            footprint.getCornerAngleThreshold(),
            footprint.getMaxPoleSpacing());
        result.poleCount = polePositions.size();
        if (polePositions.isEmpty()) {
            return result;
        }

        int[] poleTopHeights = new int[polePositions.size()];
        int[] groundHeights = new int[polePositions.size()];
        for (int i = 0; i < polePositions.size(); i++) {
            Vec2d planPoint = polePositions.get(i);
            int groundY = terrain.sampleSurfaceY(planPoint);
            groundHeights[i] = groundY;
            int poleTopY = groundY + (int) Math.round(footprint.getPoleHeight());
            poleTopHeights[i] = poleTopY;
            if (footprint.getPoleDesignId() != null && !footprint.getPoleDesignId().isBlank()) {
                applyPoleDesign(
                    footprint.getPoleDesignId(),
                    planPoint,
                    groundY,
                    poleTopY,
                    footprint,
                    result);
            } else {
                generateDefaultPole(planPoint, groundY, poleTopY, footprint, result);
            }
        }

        for (int span = 0; span < polePositions.size() - 1; span++) {
            generateWireSpan(
                polePositions.get(span),
                polePositions.get(span + 1),
                poleTopHeights[span],
                poleTopHeights[span + 1],
                footprint,
                terrain,
                result);
        }
        return result;
    }

    private void generateDefaultPole(
            Vec2d planPoint,
            int groundY,
            int poleTopY,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result) {
        BlockPos column = RoadGeometryUtils.canvasToBlockXZ(planPoint, coordinateTransformer);
        MaterialMix poleMaterial = footprint.getPoleMaterial();
        for (int y = groundY + 1; y <= poleTopY; y++) {
            BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
            String blockId = MaterialMixResolver.resolve(poleMaterial, pos, footprint.getId());
            recordBlock(result, pos, blockId);
        }
    }

    /**
     * 杆塔设计器接入点（第二份任务书实现）。
     */
    @SuppressWarnings("unused")
    private void applyPoleDesign(
            String poleDesignId,
            Vec2d planPoint,
            int groundHeight,
            int poleTopY,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result) {
        // 占位：第二份任务书接入杆塔分层设计器
    }

    private void generateWireSpan(
            Vec2d startPlan,
            Vec2d endPlan,
            int startHeight,
            int endHeight,
            PowerLineFootprint footprint,
            TerrainSampler terrain,
            PowerLineGenerationResult result) {
        double spanLength = startPlan.distance(endPlan);
        if (spanLength < 1e-6) {
            return;
        }
        result.wireLength += spanLength;

        int sampleCount = Math.max(2, (int) Math.ceil(spanLength * WIRE_SAMPLES_PER_BLOCK));
        List<Double> sagProfile = PowerLineSagUtils.computeSagProfile(
            spanLength,
            startHeight,
            endHeight,
            footprint.getSagRatio(),
            sampleCount);

        MaterialMix wireMaterial = footprint.getWireMaterial();
        for (int i = 0; i < sampleCount; i++) {
            double t = (double) i / (sampleCount - 1);
            Vec2d planPoint = startPlan.lerp(endPlan, t);
            int wireY = (int) Math.round(sagProfile.get(i));
            BlockPos column = RoadGeometryUtils.canvasToBlockXZ(planPoint, coordinateTransformer);
            BlockPos pos = new BlockPos(column.getX(), wireY, column.getZ());
            String blockId = MaterialMixResolver.resolve(wireMaterial, pos, footprint.getId());
            recordBlock(result, pos, blockId);
            checkClearance(planPoint, wireY, terrain, result);
        }
    }

    private void checkClearance(
            Vec2d planPoint,
            int wireY,
            TerrainSampler terrain,
            PowerLineGenerationResult result) {
        int groundY = terrain.sampleSurfaceY(planPoint);
        if (wireY < groundY + CLEARANCE_MARGIN) {
            result.warnings.add(String.format(
                "Clearance warning at (%.1f, %.1f): wire Y=%d, ground Y=%d",
                planPoint.x,
                planPoint.y,
                wireY,
                groundY));
        }
    }

    private void recordBlock(PowerLineGenerationResult result, BlockPos pos, String newBlockId) {
        BlockRecord existing = result.placementRecords.get(pos);
        if (existing != null) {
            result.placementRecords.put(pos, new BlockRecord(pos, existing.previousBlockId, newBlockId));
            return;
        }
        String previous = projectionHandler.getBlockIdAt(pos);
        result.placementRecords.put(pos, new BlockRecord(pos, previous, newBlockId));
    }
}
