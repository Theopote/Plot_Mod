package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.core.block.BlockSpec;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.powerline.VoxelLineRasterizer;
import com.plot.plugin.powerline.design.CrossarmSupport;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.design.structure.TowerArm;
import com.plot.plugin.powerline.design.structure.TowerArmPlacement;
import com.plot.plugin.powerline.TowerLocalPoint;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Legacy 横担斜撑放置：V/K/单斜撑为轻量支撑骨架（仅顶弦 + 斜线），桁架才使用完整上下弦。
 */
public final class BracedCrossarmVoxelPlacer {
    /** 斜撑锚点内缩比例，避免从最外缘起撑。 */
    private static final double BRACE_ANCHOR_RATIO = 0.72;

    private BracedCrossarmVoxelPlacer() {
    }

    public static void place(
            Vec2d planPoint,
            int topY,
            PoleLayer layer,
            Vec2d crossarmNormal,
            VoxelSink sink,
            String materialSeedKey,
            PlanToBlockMapper blockMapper,
            PlanToWorldMapper worldMapper) {
        if (layer == null
                || !layer.getCrossarmSupport().isActive()
                || sink == null
                || blockMapper == null
                || worldMapper == null) {
            return;
        }
        Vec2d normal = normalize(crossarmNormal);
        Vec2d forward = perpendicular(normal);
        double reach = (layer.getCrossarmLength() - 1) / 2.0;
        int supportDepth = layer.getCrossarmSupportDepth();
        double topHeight = topY;
        double bottomHeight = topY - supportDepth;
        double anchorReach = reach * BRACE_ANCHOR_RATIO;

        MaterialMix chordMaterial = layer.getMaterial();
        MaterialMix braceMaterial = layer.resolveCrossarmBraceMaterial();

        if (layer.getCrossarmSupport() == CrossarmSupport.TRUSS) {
            placeTruss(
                planPoint, normal, forward, topY, layer, crossarmNormal,
                reach, supportDepth, chordMaterial, braceMaterial,
                sink, materialSeedKey, worldMapper);
            return;
        }

        placeTopChord(
            planPoint, normal, forward, topHeight, -reach, reach,
            chordMaterial, layer, crossarmNormal, sink, materialSeedKey, worldMapper);

        switch (layer.getCrossarmSupport()) {
            case V_BRACE -> placeVBrace(
                planPoint, normal, forward, anchorReach, topHeight, bottomHeight,
                braceMaterial, sink, materialSeedKey, worldMapper);
            case K_BRACE -> placeKBrace(
                planPoint, normal, forward, anchorReach, topHeight, bottomHeight,
                braceMaterial, sink, materialSeedKey, worldMapper);
            case DIAGONAL -> placeDiagonalBrace(
                planPoint, normal, forward, reach, topHeight, bottomHeight,
                braceMaterial, sink, materialSeedKey, worldMapper);
            default -> { }
        }
    }

    private static void placeTopChord(
            Vec2d planPoint,
            Vec2d normal,
            Vec2d forward,
            double topHeight,
            double lateralStart,
            double lateralEnd,
            MaterialMix material,
            PoleLayer layer,
            Vec2d crossarmNormal,
            VoxelSink sink,
            String materialSeedKey,
            PlanToWorldMapper worldMapper) {
        int y = (int) Math.round(topHeight);
        placeChordRail(
            planPoint, normal, forward, y, lateralStart, lateralEnd, 0.0,
            material, layer, crossarmNormal, sink, materialSeedKey, worldMapper);
    }

    /** 左右各一条斜线汇于下弦中点，不生成底弦。 */
    private static void placeVBrace(
            Vec2d planPoint,
            Vec2d normal,
            Vec2d forward,
            double anchorReach,
            double topHeight,
            double bottomHeight,
            MaterialMix braceMaterial,
            VoxelSink sink,
            String materialSeedKey,
            PlanToWorldMapper worldMapper) {
        placeSupportLine(
            planPoint, normal, forward,
            -anchorReach, topHeight, 0.0, bottomHeight,
            braceMaterial, sink, materialSeedKey, worldMapper);
        placeSupportLine(
            planPoint, normal, forward,
            anchorReach, topHeight, 0.0, bottomHeight,
            braceMaterial, sink, materialSeedKey, worldMapper);
    }

    /** 轻量 K：底角汇于顶弦中点，无底弦。 */
    private static void placeKBrace(
            Vec2d planPoint,
            Vec2d normal,
            Vec2d forward,
            double anchorReach,
            double topHeight,
            double bottomHeight,
            MaterialMix braceMaterial,
            VoxelSink sink,
            String materialSeedKey,
            PlanToWorldMapper worldMapper) {
        placeSupportLine(
            planPoint, normal, forward,
            -anchorReach, bottomHeight, 0.0, topHeight,
            braceMaterial, sink, materialSeedKey, worldMapper);
        placeSupportLine(
            planPoint, normal, forward,
            anchorReach, bottomHeight, 0.0, topHeight,
            braceMaterial, sink, materialSeedKey, worldMapper);
    }

    /** 单侧斜撑（故意不对称）。 */
    private static void placeDiagonalBrace(
            Vec2d planPoint,
            Vec2d normal,
            Vec2d forward,
            double reach,
            double topHeight,
            double bottomHeight,
            MaterialMix braceMaterial,
            VoxelSink sink,
            String materialSeedKey,
            PlanToWorldMapper worldMapper) {
        placeSupportLine(
            planPoint, normal, forward,
            -reach, bottomHeight, reach, topHeight,
            braceMaterial, sink, materialSeedKey, worldMapper);
    }

    /** 完整桁架：上下弦 + X 撑（复用塔横担语义）。 */
    private static void placeTruss(
            Vec2d planPoint,
            Vec2d normal,
            Vec2d forward,
            int topY,
            PoleLayer layer,
            Vec2d crossarmNormal,
            double reach,
            int supportDepth,
            MaterialMix chordMaterial,
            MaterialMix braceMaterial,
            VoxelSink sink,
            String materialSeedKey,
            PlanToWorldMapper worldMapper) {
        TowerArm arm = new TowerArm("layer_crossarm", topY, reach);
        arm.setShape(layer.getCrossarmSupport().armShape());
        arm.setBracing(layer.getCrossarmSupport().bracingPattern());
        arm.setVerticalDrop(supportDepth);
        arm.setLongitudinalHalfWidth(0);

        TowerArmPlacement.placeArm(
            arm,
            chordMaterial,
            braceMaterial,
            (lateralStart, lateralEnd, height, longHalf, material) -> placeChordRail(
                planPoint, normal, forward, (int) Math.round(height),
                lateralStart, lateralEnd, longHalf,
                material, layer, crossarmNormal, sink, materialSeedKey, worldMapper),
            (start, end, material) -> placeSupportLine(
                planPoint, normal, forward,
                start.lateral(), start.vertical(), end.lateral(), end.vertical(),
                material, sink, materialSeedKey, worldMapper));
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
            PlanToWorldMapper worldMapper) {
        Vec2d startPoint = planPoint
            .add(normal.multiply(lateralStart))
            .add(forward.multiply(longitudinal));
        Vec2d endPoint = planPoint
            .add(normal.multiply(lateralEnd))
            .add(forward.multiply(longitudinal));
        BlockPos center = worldCenterBlock(planPoint.add(forward.multiply(longitudinal)), y, worldMapper);
        for (BlockPos pos : rasterizeSymmetricWorldLine(startPoint, y, endPoint, y, worldMapper)) {
            String blockId = MaterialMixResolver.resolve(material, pos, materialSeedKey);
            int lateralOffset = crossarmLateralOffset(pos, center, crossarmNormal);
            BlockSpec spec = crossarmBlockSpec(blockId, crossarmNormal, lateralOffset);
            sink.put(pos.getX(), pos.getY(), pos.getZ(), spec);
        }
    }

    private static void placeSupportLine(
            Vec2d planPoint,
            Vec2d normal,
            Vec2d forward,
            double startLateral,
            double startVertical,
            double endLateral,
            double endVertical,
            MaterialMix material,
            VoxelSink sink,
            String materialSeedKey,
            PlanToWorldMapper worldMapper) {
        Vec2d startPoint = toPlanPoint(planPoint, normal, forward, startLateral, 0.0);
        Vec2d endPoint = toPlanPoint(planPoint, normal, forward, endLateral, 0.0);
        for (BlockPos pos : rasterizeSymmetricWorldLine(
                startPoint, startVertical, endPoint, endVertical, worldMapper)) {
            String blockId = MaterialMixResolver.resolve(material, pos, materialSeedKey);
            sink.put(pos.getX(), pos.getY(), pos.getZ(), blockId);
        }
    }

    private static Vec2d toPlanPoint(
            Vec2d planPoint,
            Vec2d normal,
            Vec2d forward,
            double lateral,
            double longitudinal) {
        return planPoint
            .add(normal.multiply(lateral))
            .add(forward.multiply(longitudinal));
    }

    private static List<BlockPos> rasterizeSymmetricWorldLine(
            Vec2d startPlan,
            double startY,
            Vec2d endPlan,
            double endY,
            PlanToWorldMapper worldMapper) {
        Vec2d worldStart = worldMapper.toWorldXZ(startPlan);
        Vec2d worldEnd = worldMapper.toWorldXZ(endPlan);
        return VoxelLineRasterizer.rasterizeSymmetricLine3D(
            worldStart.x,
            startY,
            worldStart.y,
            worldEnd.x,
            endY,
            worldEnd.y);
    }

    private static BlockPos worldCenterBlock(Vec2d planCenter, int y, PlanToWorldMapper worldMapper) {
        Vec2d worldCenter = worldMapper.toWorldXZ(planCenter);
        return new BlockPos(
            VoxelLineRasterizer.symmetricBlock(worldCenter.x),
            y,
            VoxelLineRasterizer.symmetricBlock(worldCenter.y));
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
