package com.plot.plugin.powerline.preview;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.SnapshotCoordinateService;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.powerline.PoleFrame;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.TowerStructureGenerator;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.placement.VoxelSink;
import net.minecraft.util.math.BlockPos;


/**
 * 塔体 UI 体素预览：委托 {@link TowerStructureGenerator}，与落地建造共用放置规则。
 */
public final class TowerStructurePreviewVoxelPlacer {
    private static final Vec2d PREVIEW_ORIGIN = new Vec2d(0, 0);
    private static final Vec2d PREVIEW_FORWARD = new Vec2d(1, 0);
    private static final int PREVIEW_GROUND_Y = 0;
    /** 与测试/均匀画布投影一致：1 canvas unit = 1 block。 */
    private static final ICoordinateService PREVIEW_COORDINATES = SnapshotCoordinateService.uniformScale(1.0);

    private TowerStructurePreviewVoxelPlacer() {
    }

    public static void placePreview(
            TowerStructureDesign structure,
            VoxelSink sink,
            String materialSeedKey) {
        if (structure == null || sink == null) {
            return;
        }
        String seed = materialSeedKey != null ? materialSeedKey : "powerline_preview";
        PowerLineFootprint footprint = PowerLineFootprint.forPreviewSeed(seed);
        PowerLineGenerationResult result = new PowerLineGenerationResult(footprint);
        TowerStructureGenerator.generate(
            structure,
            PoleFrame.fromPole(PREVIEW_ORIGIN, PREVIEW_FORWARD, PREVIEW_GROUND_Y),
            footprint,
            result,
            PREVIEW_COORDINATES,
            noopProjection(),
            null);
        for (BlockRecord record : result.placementRecords.values()) {
            BlockPos previewPos = worldToPreviewBlock(record.pos);
            sink.put(previewPos.getX(), previewPos.getY(), previewPos.getZ(), record.newBlockId);
        }
    }

    /** identity 系：preview (lateral, vertical, longitudinal) ↔ world (x, y, z)。 */
    private static BlockPos worldToPreviewBlock(BlockPos worldPos) {
        return new BlockPos(worldPos.getZ(), worldPos.getY() - PREVIEW_GROUND_Y, worldPos.getX());
    }

    private static IBlockProjectionService noopProjection() {
        return new IBlockProjectionService() {
            @Override
            public String getBlockIdAt(BlockPos pos) {
                return "minecraft:air";
            }

            @Override
            public boolean setBlockAt(BlockPos pos, String blockId) {
                return true;
            }

            @Override
            public PlacementReadiness checkWorldModificationReadiness() {
                return PlacementReadiness.ok();
            }
        };
    }
}
