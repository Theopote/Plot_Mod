package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.core.block.BlockSpec;
import com.plot.core.geometry.WorldCoordinateUtils;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.VoxelLineRasterizer;
import com.plot.api.world.ICoordinateService;
import net.minecraft.util.math.BlockPos;

/**
 * 杆塔 {@link PoleDesign} 分层放置（COLUMN / CROSSARM / CAP）。
 * <p>
 * 世界生成与 UI 预览均通过 {@link VoxelSink} + {@link PlanToBlockMapper} 调用本类。
 */
public final class PoleLayerVoxelPlacer {
    private static final Vec2d PREVIEW_ORIGIN = new Vec2d(0, 0);
    /** 预览局部系：横担沿 plan X（体素 X / 侧向），与挂点 lateral 一致。 */
    private static final Vec2d PREVIEW_CROSSARM_NORMAL = new Vec2d(1, 0);

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
                case CAP -> placeCapLayer(planPoint, currentY, layer, normal, sink, materialSeedKey, mapper);
                default -> { }
            }
            currentY += layer.getHeight();
        }
        PoleIdentityFeaturePlacer.placeFeatures(
            design,
            planPoint,
            layerBaseY,
            normal,
            sink,
            mapper);
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
            Vec2d crossarmNormal,
            VoxelSink sink,
            String materialSeedKey,
            PlanToBlockMapper mapper) {
        int topY = baseY + layer.getHeight() - 1;
        for (int y = baseY; y <= topY; y++) {
            BlockPos pos = mapper.toBlockPos(planPoint, y);
            String blockId = MaterialMixResolver.resolve(layer.getMaterial(), pos, materialSeedKey);
            BlockSpec spec = y == topY
                ? capBlockSpec(blockId, crossarmNormal)
                : BlockSpec.of(blockId);
            sink.put(pos.getX(), pos.getY(), pos.getZ(), spec);
        }
    }

    private static BlockSpec capBlockSpec(String blockId, Vec2d crossarmNormal) {
        if ("minecraft:iron_trapdoor".equals(blockId)) {
            return DirectionalBlockSpecs.ironTrapdoorHorizontalHub(crossarmNormal);
        }
        if ("minecraft:lantern".equals(blockId)) {
            return DirectionalBlockSpecs.poleTopLantern();
        }
        if ("minecraft:vine".equals(blockId)) {
            return DirectionalBlockSpecs.rusticVineCap();
        }
        return BlockSpec.of(blockId);
    }

    private static void placeCrossarmLayer(
            Vec2d planPoint,
            int baseY,
            PoleLayer layer,
            Vec2d normal,
            VoxelSink sink,
            String materialSeedKey,
            PlanToBlockMapper mapper) {
        int left = (layer.getCrossarmLength() - 1) / 2;
        int right = layer.getCrossarmLength() / 2;
        for (int y = baseY; y < baseY + layer.getHeight(); y++) {
            Vec2d startPoint = planPoint.add(normal.multiply(-left));
            Vec2d endPoint = planPoint.add(normal.multiply(right));
            BlockPos start = mapper.toBlockPos(startPoint, y);
            BlockPos end = mapper.toBlockPos(endPoint, y);
            BlockPos center = mapper.toBlockPos(planPoint, y);
            for (BlockPos pos : VoxelLineRasterizer.rasterizeLine3D(
                    start.getX(), start.getY(), start.getZ(),
                    end.getX(), end.getY(), end.getZ())) {
                String blockId = MaterialMixResolver.resolve(layer.getMaterial(), pos, materialSeedKey);
                int lateralOffset = crossarmLateralOffset(pos, center, normal);
                BlockSpec spec = crossarmBlockSpec(blockId, normal, pos, center, lateralOffset);
                sink.put(pos.getX(), pos.getY(), pos.getZ(), spec);
            }
        }
    }

    private static int crossarmLateralOffset(BlockPos position, BlockPos center, Vec2d normal) {
        double deltaX = position.getX() - center.getX();
        double deltaZ = position.getZ() - center.getZ();
        return (int) Math.round(deltaX * normal.x + deltaZ * normal.y);
    }

    private static BlockSpec crossarmBlockSpec(
            String blockId,
            Vec2d normal,
            BlockPos position,
            BlockPos center,
            int lateralOffset) {
        if ("minecraft:lightning_rod".equals(blockId)) {
            Vec2d direction = lateralOffset >= 0 ? normal : normal.multiply(-1);
            return DirectionalBlockSpecs.lightningRodAlong(direction);
        }
        if (blockId != null && blockId.endsWith("_slab")) {
            return DirectionalBlockSpecs.crossarmSlab(blockId);
        }
        return BlockSpec.of(blockId);
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
