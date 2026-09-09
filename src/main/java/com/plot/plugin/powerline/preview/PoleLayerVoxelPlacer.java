package com.plot.plugin.powerline.preview;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleLayer;
import net.minecraft.util.math.BlockPos;

/**
 * 与 {@link com.plot.plugin.powerline.PowerLineGenerator} 相同的杆塔分层放置逻辑，
 * 写入抽象 {@link VoxelSink} 而非世界方块。
 */
public final class PoleLayerVoxelPlacer {
    private static final Vec2d PLAN_ORIGIN = new Vec2d(0, 0);
    private static final Vec2d NORMAL_Z = new Vec2d(0, 1);

    private PoleLayerVoxelPlacer() {
    }

    public static void placeDesign(PoleDesign design, VoxelSink sink, String seedKey) {
        if (design == null || sink == null) {
            return;
        }
        int currentY = 0;
        for (PoleLayer layer : design.getLayers()) {
            switch (layer.getShape()) {
                case COLUMN -> placeColumnLayer(PLAN_ORIGIN, currentY, layer, sink, seedKey);
                case CROSSARM -> placeCrossarmLayer(PLAN_ORIGIN, currentY, layer, NORMAL_Z, sink, seedKey);
                case CAP -> placeCapLayer(PLAN_ORIGIN, currentY, layer, sink, seedKey);
                default -> { }
            }
            currentY += layer.getHeight();
        }
    }

    private static void placeColumnLayer(
            Vec2d planPoint,
            int baseY,
            PoleLayer layer,
            VoxelSink sink,
            String seedKey) {
        int blockX = planX(planPoint);
        int blockZ = planZ(planPoint);
        for (int y = baseY; y < baseY + layer.getHeight(); y++) {
            String blockId = resolveMaterial(layer.getMaterial(), blockX, y, blockZ, seedKey);
            sink.put(blockX, y, blockZ, blockId);
        }
    }

    private static void placeCapLayer(
            Vec2d planPoint,
            int baseY,
            PoleLayer layer,
            VoxelSink sink,
            String seedKey) {
        int blockX = planX(planPoint);
        int blockZ = planZ(planPoint);
        String blockId = resolveMaterial(layer.getMaterial(), blockX, baseY, blockZ, seedKey);
        sink.put(blockX, baseY, blockZ, blockId);
    }

    private static void placeCrossarmLayer(
            Vec2d planPoint,
            int baseY,
            PoleLayer layer,
            Vec2d normal,
            VoxelSink sink,
            String seedKey) {
        int half = layer.getCrossarmLength() / 2;
        for (int y = baseY; y < baseY + layer.getHeight(); y++) {
            for (int offset = -half; offset <= half; offset++) {
                Vec2d armPoint = planPoint.add(normal.multiply(offset));
                int blockX = planX(armPoint);
                int blockZ = planZ(armPoint);
                String blockId = resolveMaterial(layer.getMaterial(), blockX, y, blockZ, seedKey);
                sink.put(blockX, y, blockZ, blockId);
            }
        }
    }

    private static String resolveMaterial(MaterialMix material, int x, int y, int z, String seedKey) {
        return MaterialMixResolver.resolve(material, new BlockPos(x, y, z), seedKey);
    }

    private static int planX(Vec2d planPoint) {
        return (int) Math.round(planPoint.x);
    }

    private static int planZ(Vec2d planPoint) {
        return (int) Math.round(planPoint.y);
    }
}
