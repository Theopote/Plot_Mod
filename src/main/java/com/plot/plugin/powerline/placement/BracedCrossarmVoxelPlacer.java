package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.core.block.BlockSpec;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.powerline.VoxelLineRasterizer;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerArmPlacement;
import com.plot.plugin.powerline.TowerLocalPoint;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 Legacy 横担层的斜撑参数委托给 {@link TowerArmPlacement}，在体素预览与世界生成中共用。
 */
public final class BracedCrossarmVoxelPlacer {
    private BracedCrossarmVoxelPlacer() {
    }

    public static void place(
            Vec2d planPoint,
            int topY,
            PoleLayer layer,
            Vec2d crossarmNormal,
            VoxelSink sink,
            String materialSeedKey,
            PlanToBlockMapper mapper) {
        if (layer == null || !layer.getCrossarmSupport().isActive() || sink == null || mapper == null) {
            return;
        }
        Vec2d normal = normalize(crossarmNormal);
        Vec2d forward = perpendicular(normal);
        double reach = (layer.getCrossarmLength() - 1) / 2.0;
        int supportDepth = layer.getCrossarmSupportDepth();

        TowerArm arm = new TowerArm("layer_crossarm", topY, reach);
        arm.setShape(layer.getCrossarmSupport().armShape());
        arm.setBracing(layer.getCrossarmSupport().bracingPattern());
        arm.setVerticalDrop(supportDepth);
        arm.setLongitudinalHalfWidth(0);

        MaterialMix chordMaterial = layer.getMaterial();
        MaterialMix braceMaterial = layer.resolveCrossarmBraceMaterial();

        TowerArmPlacement.placeArm(
            arm,
            chordMaterial,
            braceMaterial,
            (lateralStart, lateralEnd, height, longHalf, material) -> placeChord(
                planPoint,
                normal,
                forward,
                height,
                lateralStart,
                lateralEnd,
                longHalf,
                material,
                layer,
                crossarmNormal,
                sink,
                materialSeedKey,
                mapper),
            (start, end, material) -> placeBrace(
                planPoint,
                normal,
                forward,
                start,
                end,
                material,
                sink,
                materialSeedKey,
                mapper));
    }

    private static void placeChord(
            Vec2d planPoint,
            Vec2d normal,
            Vec2d forward,
            double height,
            double lateralStart,
            double lateralEnd,
            double longHalf,
            MaterialMix material,
            PoleLayer layer,
            Vec2d crossarmNormal,
            VoxelSink sink,
            String materialSeedKey,
            PlanToBlockMapper mapper) {
        int y = (int) Math.round(height);
        placeChordRail(planPoint, normal, forward, y, lateralStart, lateralEnd, -longHalf, material, layer, crossarmNormal, sink, materialSeedKey, mapper);
        if (longHalf > 0) {
            placeChordRail(planPoint, normal, forward, y, lateralStart, lateralEnd, longHalf, material, layer, crossarmNormal, sink, materialSeedKey, mapper);
        }
    }

    private static void placeChordRail(
            Vec2d planPoint,
            Vec2d normal,
            Vec2d forward,
            int y,
            double lateralStart,
            double lateralEnd,
            double longitudinal,
            MaterialMix material,
            PoleLayer layer,
            Vec2d crossarmNormal,
            VoxelSink sink,
            String materialSeedKey,
            PlanToBlockMapper mapper) {
        Vec2d startPoint = planPoint
            .add(normal.multiply(lateralStart))
            .add(forward.multiply(longitudinal));
        Vec2d endPoint = planPoint
            .add(normal.multiply(lateralEnd))
            .add(forward.multiply(longitudinal));
        BlockPos center = mapper.toBlockPos(planPoint.add(forward.multiply(longitudinal)), y);
        for (BlockPos pos : rasterizeSymmetricPlanLine(startPoint, y, endPoint, y, mapper)) {
            String blockId = MaterialMixResolver.resolve(material, pos, materialSeedKey);
            int lateralOffset = crossarmLateralOffset(pos, center, crossarmNormal);
            BlockSpec spec = crossarmBlockSpec(blockId, crossarmNormal, lateralOffset);
            sink.put(pos.getX(), pos.getY(), pos.getZ(), spec);
        }
    }

    private static void placeBrace(
            Vec2d planPoint,
            Vec2d normal,
            Vec2d forward,
            TowerLocalPoint start,
            TowerLocalPoint end,
            MaterialMix material,
            VoxelSink sink,
            String materialSeedKey,
            PlanToBlockMapper mapper) {
        Vec2d startPoint = toPlanPoint(planPoint, normal, forward, start);
        Vec2d endPoint = toPlanPoint(planPoint, normal, forward, end);
        for (BlockPos pos : rasterizeSymmetricPlanLine(
                startPoint,
                start.vertical(),
                endPoint,
                end.vertical(),
                mapper)) {
            String blockId = MaterialMixResolver.resolve(material, pos, materialSeedKey);
            sink.put(pos.getX(), pos.getY(), pos.getZ(), blockId);
        }
    }

    private static Vec2d toPlanPoint(
            Vec2d planPoint,
            Vec2d normal,
            Vec2d forward,
            TowerLocalPoint local) {
        return planPoint
            .add(normal.multiply(local.lateral()))
            .add(forward.multiply(local.longitudinal()));
    }

    /**
     * 在 plan 坐标系对称光栅化，再映射到方块坐标，避免 floor 取整导致左右斜撑差一格。
     */
    private static List<BlockPos> rasterizeSymmetricPlanLine(
            Vec2d startPlan,
            double startY,
            Vec2d endPlan,
            double endY,
            PlanToBlockMapper mapper) {
        List<BlockPos> localLine = VoxelLineRasterizer.rasterizeSymmetricLine3D(
            startPlan.x,
            startY,
            startPlan.y,
            endPlan.x,
            endY,
            endPlan.y);
        List<BlockPos> mapped = new ArrayList<>(localLine.size());
        for (BlockPos cell : localLine) {
            mapped.add(mapper.toBlockPos(new Vec2d(cell.getX(), cell.getZ()), cell.getY()));
        }
        return mapped;
    }

    private static int crossarmLateralOffset(BlockPos position, BlockPos center, Vec2d normal) {
        double deltaX = position.getX() - center.getX();
        double deltaZ = position.getZ() - center.getZ();
        return (int) Math.round(deltaX * normal.x + deltaZ * normal.y);
    }

    private static BlockSpec crossarmBlockSpec(String blockId, Vec2d normal, int lateralOffset) {
        if ("minecraft:lightning_rod".equals(blockId)) {
            Vec2d direction = lateralOffset >= 0 ? normal : normal.multiply(-1);
            return DirectionalBlockSpecs.lightningRodAlong(direction);
        }
        if (blockId != null && blockId.endsWith("_slab")) {
            return DirectionalBlockSpecs.crossarmSlab(blockId);
        }
        return BlockSpec.of(blockId);
    }

    private static Vec2d normalize(Vec2d crossarmNormal) {
        if (crossarmNormal == null || crossarmNormal.lengthSquared() <= 1e-12) {
            return new Vec2d(0, 1);
        }
        return crossarmNormal.normalize();
    }

    private static Vec2d perpendicular(Vec2d normal) {
        return new Vec2d(-normal.y, normal.x);
    }
}
