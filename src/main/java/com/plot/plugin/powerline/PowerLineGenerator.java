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
import com.plot.plugin.powerline.design.structure.TowerStructureValidator;
import com.plot.plugin.powerline.design.structure.TowerValidationIssue;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 电力线路生成器：立杆 + 多挂点导线。
 */
public class PowerLineGenerator {
    private final ICoordinateService coordinateTransformer;
    private final IBlockProjectionService projectionHandler;
    private final PowerLineAttachmentResolver attachmentResolver;

    public PowerLineGenerator(
            ICoordinateService coordinateTransformer,
            IBlockProjectionService projectionHandler) {
        this.coordinateTransformer = java.util.Objects.requireNonNull(
            coordinateTransformer, "coordinateTransformer");
        this.projectionHandler = java.util.Objects.requireNonNull(
            projectionHandler, "projectionHandler");
        this.attachmentResolver = new PowerLineAttachmentResolver(coordinateTransformer);
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

        List<PolePlacement> placements = new ArrayList<>(polePositions.size());
        for (int i = 0; i < polePositions.size(); i++) {
            placements.add(buildPolePlacement(
                polePositions,
                i,
                footprint,
                terrain,
                designResolver,
                result));
        }

        for (int span = 0; span < placements.size() - 1; span++) {
            ConductorSpanGenerator.generateBetween(
                placements.get(span),
                placements.get(span + 1),
                footprint,
                terrain,
                result,
                coordinateTransformer,
                projectionHandler);
        }
        return result;
    }

    private PolePlacement buildPolePlacement(
            List<Vec2d> polePositions,
            int index,
            PowerLineFootprint footprint,
            TerrainSampler terrain,
            PoleDesignResolver designResolver,
            PowerLineGenerationResult result) {
        Vec2d planPoint = polePositions.get(index);
        int groundY = terrain.sampleSurfaceY(planPoint);
        Vec2d tangent = computePoleTangent(polePositions, index);
        PoleFrame frame = PoleFrame.fromPole(planPoint, tangent, groundY);
        PoleDesign design = resolveDesign(footprint, designResolver);

        int legacyWireHangY;
        List<ResolvedAttachment> attachments = List.of();
        boolean usesAttachmentConductors = false;

        if (design != null) {
            if (design.hasTowerStructure()) {
                legacyWireHangY = TowerStructureGenerator.generate(
                    design.getTowerStructure(),
                    frame,
                    footprint,
                    result,
                    coordinateTransformer,
                    projectionHandler,
                    terrain);
            } else {
                legacyWireHangY = applyPoleDesign(design, planPoint, groundY, tangent, footprint, result);
            }
            attachments = attachmentResolver.resolve(design, frame);
            usesAttachmentConductors = design.hasEnabledAttachments();
            for (TowerValidationIssue issue : TowerStructureValidator.validate(design)) {
                if (issue.severity() != com.plot.plugin.powerline.design.structure.TowerValidationSeverity.INFO) {
                    result.warnings.add(issue.message());
                }
            }
            for (ResolvedAttachment attachment : attachments) {
                ConductorSpanGenerator.placeInsulator(attachment, footprint, result, projectionHandler);
            }
        } else {
            legacyWireHangY = groundY + (int) Math.round(footprint.getPoleHeight());
            generateDefaultPole(planPoint, groundY, legacyWireHangY, footprint, result);
        }

        return new PolePlacement(
            planPoint,
            frame,
            design,
            attachments,
            legacyWireHangY,
            usesAttachmentConductors);
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
        int wireHangY = design.wireHangHeightFromGround(groundY);
        Vec2d direction = tangent.lengthSquared() > 1e-12 ? tangent.normalize() : new Vec2d(1, 0);
        Vec2d normal = RoadGeometryUtils.leftNormal(direction);

        for (PoleLayer layer : design.getLayers()) {
            switch (layer.getShape()) {
                case COLUMN -> placeColumnLayer(planPoint, currentY, layer, footprint, result);
                case CROSSARM -> placeCrossarmLayer(planPoint, currentY, layer, normal, footprint, result);
                case CAP -> placeCapLayer(planPoint, currentY, layer, footprint, result);
                default -> { }
            }
            currentY += layer.getHeight();
        }
        return wireHangY;
    }

    static int computeWireHangHeight(int groundY, PoleDesign design) {
        return design.wireHangHeightFromGround(groundY);
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
