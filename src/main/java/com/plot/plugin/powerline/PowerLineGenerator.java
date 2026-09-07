package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.core.command.BlockRecord;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.PoleLayer;
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

    public PowerLineGenerationResult generate(
            PowerLineFootprint footprint,
            TerrainSampler terrain,
            PoleDesignResolver designResolver) {
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

        int[] wireHangHeights = new int[polePositions.size()];
        for (int i = 0; i < polePositions.size(); i++) {
            Vec2d planPoint = polePositions.get(i);
            int groundY = terrain.sampleSurfaceY(planPoint);
            Vec2d tangent = computePoleTangent(polePositions, i);
            PoleDesign design = resolveDesign(footprint, designResolver);

            if (design != null) {
                wireHangHeights[i] = applyPoleDesign(
                    design, planPoint, groundY, tangent, footprint, result);
            } else {
                int poleTopY = groundY + (int) Math.round(footprint.getPoleHeight());
                generateDefaultPole(planPoint, groundY, poleTopY, footprint, result);
                wireHangHeights[i] = poleTopY;
            }
        }

        for (int span = 0; span < polePositions.size() - 1; span++) {
            generateWireSpan(
                polePositions.get(span),
                polePositions.get(span + 1),
                wireHangHeights[span],
                wireHangHeights[span + 1],
                footprint,
                terrain,
                result);
        }
        return result;
    }

    private PoleDesign resolveDesign(PowerLineFootprint footprint, PoleDesignResolver designResolver) {
        if (footprint.getPoleDesignId() == null || footprint.getPoleDesignId().isBlank()) {
            return null;
        }
        if (designResolver == null) {
            return null;
        }
        return designResolver.find(footprint.getPoleDesignId());
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

    int applyPoleDesign(
            PoleDesign design,
            Vec2d planPoint,
            int groundY,
            Vec2d tangent,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result) {
        int currentY = groundY + 1;
        int wireHangY = groundY + design.totalHeight();
        Vec2d direction = tangent.lengthSquared() > 1e-12 ? tangent.normalize() : new Vec2d(1, 0);
        Vec2d normal = RoadGeometryUtils.leftNormal(direction);

        for (PoleLayer layer : design.getLayers()) {
            switch (layer.getShape()) {
                case COLUMN -> placeColumnLayer(planPoint, currentY, layer, footprint, result);
                case CROSSARM -> {
                    placeCrossarmLayer(planPoint, currentY, layer, normal, footprint, result);
                    wireHangY = currentY + layer.getHeight() - 1;
                }
                case CAP -> placeCapLayer(planPoint, currentY, layer, footprint, result);
                default -> { }
            }
            currentY += layer.getHeight();
        }
        return wireHangY;
    }

    static int computeWireHangHeight(int groundY, PoleDesign design) {
        int currentY = groundY + 1;
        int wireHangY = groundY + design.totalHeight();
        for (PoleLayer layer : design.getLayers()) {
            if (layer.getShape() == PoleLayer.Shape.CROSSARM) {
                wireHangY = currentY + layer.getHeight() - 1;
            }
            currentY += layer.getHeight();
        }
        return wireHangY;
    }

    private void placeColumnLayer(
            Vec2d planPoint,
            int baseY,
            PoleLayer layer,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result) {
        BlockPos column = RoadGeometryUtils.canvasToBlockXZ(planPoint, coordinateTransformer);
        for (int y = baseY; y < baseY + layer.getHeight(); y++) {
            BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
            String blockId = MaterialMixResolver.resolve(layer.getMaterial(), pos, footprint.getId());
            recordBlock(result, pos, blockId);
        }
    }

    private void placeCapLayer(
            Vec2d planPoint,
            int baseY,
            PoleLayer layer,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result) {
        BlockPos column = RoadGeometryUtils.canvasToBlockXZ(planPoint, coordinateTransformer);
        BlockPos pos = new BlockPos(column.getX(), baseY, column.getZ());
        String blockId = MaterialMixResolver.resolve(layer.getMaterial(), pos, footprint.getId());
        recordBlock(result, pos, blockId);
    }

    private void placeCrossarmLayer(
            Vec2d planPoint,
            int baseY,
            PoleLayer layer,
            Vec2d normal,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result) {
        int half = layer.getCrossarmLength() / 2;
        for (int y = baseY; y < baseY + layer.getHeight(); y++) {
            for (int offset = -half; offset <= half; offset++) {
                Vec2d armPoint = planPoint.add(normal.multiply(offset));
                BlockPos column = RoadGeometryUtils.canvasToBlockXZ(armPoint, coordinateTransformer);
                BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                String blockId = MaterialMixResolver.resolve(layer.getMaterial(), pos, footprint.getId());
                recordBlock(result, pos, blockId);
            }
        }
    }

    static Vec2d computePoleTangent(List<Vec2d> poles, int index) {
        if (poles == null || poles.size() < 2) {
            return new Vec2d(1, 0);
        }
        if (index <= 0) {
            return poles.get(1).subtract(poles.get(0));
        }
        if (index >= poles.size() - 1) {
            return poles.get(index).subtract(poles.get(index - 1));
        }
        Vec2d incoming = poles.get(index).subtract(poles.get(index - 1));
        Vec2d outgoing = poles.get(index + 1).subtract(poles.get(index));
        return incoming.add(outgoing);
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
