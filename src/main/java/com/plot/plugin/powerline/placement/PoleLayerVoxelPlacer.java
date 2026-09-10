package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.WorldCoordinateUtils;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.api.world.ICoordinateService;
import net.minecraft.util.math.BlockPos;

/**
 * 杆塔 {@link PoleDesign} 分层放置（COLUMN / CROSSARM / CAP）。
 * <p>
 * 世界生成与 UI 预览均通过 {@link VoxelSink} + {@link PlanToBlockMapper} 调用本类。
 */
public final class PoleLayerVoxelPlacer {
    private static final Vec2d PREVIEW_ORIGIN = new Vec2d(0, 0);
    private static final Vec2d PREVIEW_CROSSARM_NORMAL = new Vec2d(0, 1);

    private PoleLayerVoxelPlacer() {
    }

    /** 预览局部坐标系：X/Z 为 plan 坐标取整，Y 为层栈高度。 */
    public static PlanToBlockMapper previewMapper() {
        return (planPoint, worldY) -> new BlockPos(planX(planPoint), worldY, planZ(planPoint));
    }

    /** 世界坐标系：plan → canvas 方块 XZ，Y 为绝对高度。 */
    public static PlanToBlockMapper worldMapper(ICoordinateService coordinateTransformer) {
        return (planPoint, worldY) -> {
            BlockPos column = WorldCoordinateUtils.canvasToBlockXZ(planPoint, coordinateTransformer);
            return new BlockPos(column.getX(), worldY, column.getZ());
        };
    }

    public static void placeDesignPreview(PoleDesign design, VoxelSink sink, String materialSeedKey) {
        placeDesign(
            design,
            PREVIEW_ORIGIN,
            0,
            PREVIEW_CROSSARM_NORMAL,
            sink,
            materialSeedKey,
            previewMapper());
    }

    public static void placeDesign(
            PoleDesign design,
            Vec2d planPoint,
            int layerBaseY,
            Vec2d crossarmNormal,
            VoxelSink sink,
            String materialSeedKey,
            PlanToBlockMapper mapper) {
        if (design == null || sink == null || mapper == null) {
            return;
        }
        Vec2d normal = normalizeCrossarmNormal(crossarmNormal);
        int currentY = layerBaseY;
        for (PoleLayer layer : design.getLayers()) {
            switch (layer.getShape()) {
                case COLUMN -> placeColumnLayer(planPoint, currentY, layer, sink, materialSeedKey, mapper);
                case CROSSARM -> placeCrossarmLayer(planPoint, currentY, layer, normal, sink, materialSeedKey, mapper);
                case CAP -> placeCapLayer(planPoint, currentY, layer, sink, materialSeedKey, mapper);
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
            String materialSeedKey,
            PlanToBlockMapper mapper) {
        for (int y = baseY; y < baseY + layer.getHeight(); y++) {
            putLayerBlock(planPoint, y, layer.getMaterial(), sink, materialSeedKey, mapper);
        }
    }

    private static void placeCapLayer(
            Vec2d planPoint,
            int baseY,
            PoleLayer layer,
            VoxelSink sink,
            String materialSeedKey,
            PlanToBlockMapper mapper) {
        putLayerBlock(planPoint, baseY, layer.getMaterial(), sink, materialSeedKey, mapper);
    }

    private static void placeCrossarmLayer(
            Vec2d planPoint,
            int baseY,
            PoleLayer layer,
            Vec2d normal,
            VoxelSink sink,
            String materialSeedKey,
            PlanToBlockMapper mapper) {
        int half = layer.getCrossarmLength() / 2;
        for (int y = baseY; y < baseY + layer.getHeight(); y++) {
            for (int offset = -half; offset <= half; offset++) {
                Vec2d armPoint = planPoint.add(normal.multiply(offset));
                putLayerBlock(armPoint, y, layer.getMaterial(), sink, materialSeedKey, mapper);
            }
        }
    }

    private static void putLayerBlock(
            Vec2d planPoint,
            int worldY,
            MaterialMix material,
            VoxelSink sink,
            String materialSeedKey,
            PlanToBlockMapper mapper) {
        BlockPos pos = mapper.toBlockPos(planPoint, worldY);
        String blockId = MaterialMixResolver.resolve(material, pos, materialSeedKey);
        sink.put(pos.getX(), pos.getY(), pos.getZ(), blockId);
    }

    private static Vec2d normalizeCrossarmNormal(Vec2d crossarmNormal) {
        if (crossarmNormal == null || crossarmNormal.lengthSquared() <= 1e-12) {
            return new Vec2d(0, 1);
        }
        return crossarmNormal.normalize();
    }

    private static int planX(Vec2d planPoint) {
        return (int) Math.round(planPoint.x);
    }

    private static int planZ(Vec2d planPoint) {
        return (int) Math.round(planPoint.y);
    }
}
