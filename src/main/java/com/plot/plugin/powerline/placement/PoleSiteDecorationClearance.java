package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.core.command.BlockRecord;
import com.plot.core.geometry.WorldCoordinateUtils;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import net.minecraft.util.math.BlockPos;

/**
 * 电杆/铁塔落点处清除可清理的自然附着物（草、花、树叶、自然树），语义与道路
 * {@link com.plot.plugin.road.RoadRoadbedGradingUtils#clearRoadDecorations} 一致。
 */
public final class PoleSiteDecorationClearance {
    private static final String AIR = "minecraft:air";

    private PoleSiteDecorationClearance() {
    }

    public static void clearAroundPole(
            Vec2d planPoint,
            PolePlacementBase placementBase,
            PoleDesign design,
            double defaultPoleHeight,
            Vec2d tangent,
            TerrainSampler terrain,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            ICoordinateService coordinates) {
        if (planPoint == null
                || placementBase == null
                || terrain == null
                || result == null
                || projection == null
                || coordinates == null) {
            return;
        }

        SiteFootprint footprint = computeFootprint(design, defaultPoleHeight);
        Vec2d forward = normalizeTangent(tangent);
        Vec2d normal = WorldCoordinateUtils.leftNormal(forward);
        int groundY = placementBase.engineeringGroundY();
        int structureTopY = placementBase.buildBaseY() + footprint.structureHeightBlocks;

        for (int lateral = -footprint.lateralRadiusBlocks; lateral <= footprint.lateralRadiusBlocks; lateral++) {
            for (int longitudinal = -footprint.longitudinalRadiusBlocks;
                    longitudinal <= footprint.longitudinalRadiusBlocks;
                    longitudinal++) {
                Vec2d sample = planPoint
                    .add(normal.multiply(lateral))
                    .add(forward.multiply(longitudinal));
                BlockPos column = WorldCoordinateUtils.canvasToBlockXZ(sample, coordinates);
                int columnTopY = terrain.sampleColumnTopY(sample);
                int clearTopY = Math.max(columnTopY, structureTopY);
                for (int y = groundY + 1; y <= clearTopY; y++) {
                    if (terrain.isRoadClearableDecoration(column.getX(), y, column.getZ())) {
                        recordAir(result, projection, new BlockPos(column.getX(), y, column.getZ()));
                    }
                }
            }
        }
    }

    static SiteFootprint computeFootprint(PoleDesign design, double defaultPoleHeight) {
        if (design != null && design.hasTowerStructure()) {
            TowerStructureDesign structure = design.getTowerStructure();
            double lateral = structure.maxHalfWidth();
            double longitudinal = structure.maxHalfDepth();
            for (TowerArm arm : structure.getArms()) {
                if (arm == null) {
                    continue;
                }
                lateral = Math.max(lateral, arm.getLateralReach());
                longitudinal = Math.max(longitudinal, arm.getLongitudinalHalfWidth());
            }
            return new SiteFootprint(
                (int) Math.ceil(lateral),
                (int) Math.ceil(longitudinal),
                (int) Math.ceil(structure.maxHeight()));
        }
        if (design != null) {
            int crossarmHalf = 0;
            for (PoleLayer layer : design.getLayers()) {
                if (layer != null && layer.getShape() == PoleLayer.Shape.CROSSARM) {
                    crossarmHalf = Math.max(crossarmHalf, layer.getCrossarmLength() / 2);
                }
            }
            return new SiteFootprint(crossarmHalf, 0, design.totalHeight());
        }
        return new SiteFootprint(0, 0, Math.max(1, (int) Math.round(defaultPoleHeight)));
    }

    private static Vec2d normalizeTangent(Vec2d tangent) {
        if (tangent == null || tangent.lengthSquared() <= 1e-12) {
            return new Vec2d(1, 0);
        }
        return tangent.normalize();
    }

    private static void recordAir(
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            BlockPos pos) {
        BlockRecord existing = result.placementRecords.get(pos);
        if (existing != null) {
            if (AIR.equals(existing.newBlockId)) {
                return;
            }
            result.placementRecords.put(pos, new BlockRecord(pos, existing.previousBlockId, AIR));
            return;
        }
        String previous = projection.getBlockIdAt(pos);
        if (AIR.equals(previous)) {
            return;
        }
        result.placementRecords.put(pos, new BlockRecord(pos, previous, AIR));
    }

    record SiteFootprint(int lateralRadiusBlocks, int longitudinalRadiusBlocks, int structureHeightBlocks) {
    }
}
