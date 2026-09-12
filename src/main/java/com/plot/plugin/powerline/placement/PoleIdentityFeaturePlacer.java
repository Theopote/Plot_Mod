package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.core.block.BlockSpec;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.plugin.powerline.design.PoleLayer;
import net.minecraft.util.math.BlockPos;

/**
 * 杆塔身份特征体素：将 Style 卡片承诺的轮廓元素实体化到 Minecraft 几何中。
 * <p>
 * 由 {@link PoleLayerVoxelPlacer} 在分层放置之后调用，预览与世界生成共用。
 */
public final class PoleIdentityFeaturePlacer {
    private static final int ROTOR_BLADE_LENGTH = 4;

    private PoleIdentityFeaturePlacer() {
    }

    public static void placeFeatures(
            PoleDesign design,
            Vec2d planPoint,
            int layerBaseY,
            Vec2d crossarmNormal,
            VoxelSink sink,
            PlanToBlockMapper mapper) {
        if (design == null || sink == null || mapper == null) {
            return;
        }
        Vec2d lateral = normalize(crossarmNormal);
        Vec2d forward = perpendicular(lateral);
        String id = design.getId();
        if (PoleDesignCatalog.WASTELAND_WIND_TURBINE_ID.equals(id)) {
            placeWindRotor(planPoint, layerBaseY, design, lateral, forward, sink, mapper);
        } else if (PoleDesignCatalog.MODERN_UTILITY_POLE_ID.equals(id)) {
            placeTransformerBox(planPoint, layerBaseY, design, lateral, sink, mapper);
        } else if (PoleDesignCatalog.SUBURBAN_LAMP_POLE_ID.equals(id)) {
            placeSuburbanLampHead(planPoint, layerBaseY, design, lateral, sink, mapper);
        } else if (PoleDesignCatalog.FANTASY_COPPER_POLE_ID.equals(id)) {
            placeFantasyCopperCross(planPoint, layerBaseY, design, lateral, forward, sink, mapper);
        } else if (PoleDesignCatalog.STEAMPUNK_BRASS_TOWER_ID.equals(id)) {
            placeSteampunkGearCap(planPoint, layerBaseY, design, lateral, forward, sink, mapper);
        }
    }

    /** 四向桨叶 + 轮毂（CAP 层已有 hub 方块）。 */
    private static void placeWindRotor(
            Vec2d planPoint,
            int layerBaseY,
            PoleDesign design,
            Vec2d lateral,
            Vec2d forward,
            VoxelSink sink,
            PlanToBlockMapper mapper) {
        int hubY = layerTopY(layerBaseY, design, PoleLayer.Shape.CAP);
        if (hubY < layerBaseY) {
            hubY = layerBaseY + design.totalHeight() - 1;
        }
        String blade = "minecraft:orange_terracotta";
        placeBlade(planPoint, hubY, lateral, ROTOR_BLADE_LENGTH, blade, sink, mapper);
        placeBlade(planPoint, hubY, lateral.multiply(-1), ROTOR_BLADE_LENGTH, blade, sink, mapper);
        placeBlade(planPoint, hubY, forward, ROTOR_BLADE_LENGTH, blade, sink, mapper);
        placeBlade(planPoint, hubY, forward.multiply(-1), ROTOR_BLADE_LENGTH, blade, sink, mapper);
    }

    /** 侧挂变压器箱：2×2 iron_block，附在杆身中段。 */
    private static void placeTransformerBox(
            Vec2d planPoint,
            int layerBaseY,
            PoleDesign design,
            Vec2d lateral,
            VoxelSink sink,
            PlanToBlockMapper mapper) {
        int boxBaseY = layerBaseY + Math.max(4, design.totalHeight() / 2 - 1);
        int sideOffset = 2;
        String block = "minecraft:iron_block";
        for (int dy = 0; dy < 2; dy++) {
            for (int dSide = 0; dSide < 2; dSide++) {
                Vec2d point = planPoint.add(lateral.multiply(sideOffset + dSide));
                putBlock(point, boxBaseY + dy, block, sink, mapper);
            }
        }
    }

    /** 横担末端下垂灯头：chain + soul_lantern。 */
    private static void placeSuburbanLampHead(
            Vec2d planPoint,
            int layerBaseY,
            PoleDesign design,
            Vec2d lateral,
            VoxelSink sink,
            PlanToBlockMapper mapper) {
        int armY = layerTopY(layerBaseY, design, PoleLayer.Shape.CROSSARM);
        int halfReach = crossarmHalfReach(design);
        Vec2d tip = planPoint.add(lateral.multiply(halfReach));
        putBlock(tip, armY, DirectionalBlockSpecs.verticalChain(), sink, mapper);
        putBlock(tip, armY - 1, BlockSpec.of("minecraft:soul_lantern"), sink, mapper);
    }

    /** 十字避雷针横担（第二根垂直于既有横担）。 */
    private static void placeFantasyCopperCross(
            Vec2d planPoint,
            int layerBaseY,
            PoleDesign design,
            Vec2d lateral,
            Vec2d forward,
            VoxelSink sink,
            PlanToBlockMapper mapper) {
        int armY = layerTopY(layerBaseY, design, PoleLayer.Shape.CROSSARM);
        int halfReach = crossarmHalfReach(design);
        for (int step = 1; step <= halfReach; step++) {
            putBlock(
                planPoint.add(forward.multiply(step)),
                armY,
                DirectionalBlockSpecs.lightningRodAlong(forward),
                sink,
                mapper);
            putBlock(
                planPoint.add(forward.multiply(-step)),
                armY,
                DirectionalBlockSpecs.lightningRodAlong(forward.multiply(-1)),
                sink,
                mapper);
        }
    }

    /** 顶部齿轮环：中心 CAP + 四向金块齿。 */
    private static void placeSteampunkGearCap(
            Vec2d planPoint,
            int layerBaseY,
            PoleDesign design,
            Vec2d lateral,
            Vec2d forward,
            VoxelSink sink,
            PlanToBlockMapper mapper) {
        int capY = layerTopY(layerBaseY, design, PoleLayer.Shape.CAP);
        String tooth = "minecraft:gold_block";
        putBlock(planPoint.add(lateral), capY, tooth, sink, mapper);
        putBlock(planPoint.add(lateral.multiply(-1)), capY, tooth, sink, mapper);
        putBlock(planPoint.add(forward), capY, tooth, sink, mapper);
        putBlock(planPoint.add(forward.multiply(-1)), capY, tooth, sink, mapper);
    }

    private static void placeBlade(
            Vec2d hub,
            int y,
            Vec2d direction,
            int length,
            String blockId,
            VoxelSink sink,
            PlanToBlockMapper mapper) {
        for (int step = 1; step <= length; step++) {
            putBlock(hub.add(direction.multiply(step)), y, blockId, sink, mapper);
        }
    }

    private static int layerTopY(int layerBaseY, PoleDesign design, PoleLayer.Shape shape) {
        int y = layerBaseY;
        for (PoleLayer layer : design.getLayers()) {
            if (layer.getShape() == shape) {
                return y + layer.getHeight() - 1;
            }
            y += layer.getHeight();
        }
        return layerBaseY + design.totalHeight() - 1;
    }

    private static int crossarmHalfReach(PoleDesign design) {
        for (PoleLayer layer : design.getLayers()) {
            if (layer.getShape() == PoleLayer.Shape.CROSSARM) {
                return layer.getCrossarmLength() / 2;
            }
        }
        return 2;
    }

    private static void putBlock(
            Vec2d planPoint,
            int worldY,
            String blockId,
            VoxelSink sink,
            PlanToBlockMapper mapper) {
        putBlock(planPoint, worldY, BlockSpec.of(blockId), sink, mapper);
    }

    private static void putBlock(
            Vec2d planPoint,
            int worldY,
            BlockSpec block,
            VoxelSink sink,
            PlanToBlockMapper mapper) {
        BlockPos pos = mapper.toBlockPos(planPoint, worldY);
        sink.put(pos.getX(), pos.getY(), pos.getZ(), block);
    }

    private static Vec2d normalize(Vec2d vector) {
        if (vector == null || vector.lengthSquared() <= 1e-12) {
            return new Vec2d(1, 0);
        }
        return vector.normalize();
    }

    private static Vec2d perpendicular(Vec2d lateral) {
        return new Vec2d(-lateral.y, lateral.x);
    }
}
