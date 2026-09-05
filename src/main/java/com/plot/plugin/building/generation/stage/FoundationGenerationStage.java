package com.plot.plugin.building.generation.stage;

import com.plot.api.world.IBlockProjectionService;
import com.plot.plugin.building.generation.BuildingBlockWriter;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 地基找平：高于基面切削，低于基面回填。
 */
public final class FoundationGenerationStage implements BuildingGenerationStage {
    @Override
    public String name() {
        return "foundation";
    }

    @Override
    public void generate(BuildingGenerationContext context) {
        BuildingGenerationResult result = context.getResult();
        World world = context.getWorld();
        int baseElevation = context.getBaseElevation();
        String fillBlockId = context.getFoundationFillBlockId();
        IBlockProjectionService projectionHandler = context.getProjectionService();

        for (BuildingGenerationContext.GridCell cell : context.getFootprintCells()) {
            BlockPos column = context.canvasToColumn(cell.center());
            int groundY = BuildingGenerationContext.sampleTopHeight(world, column);
            if (groundY > baseElevation) {
                for (int y = baseElevation + 1; y <= groundY; y++) {
                    BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                    BuildingBlockWriter.recordBlock(result, pos, "minecraft:air", projectionHandler);
                }
                result.cutVolume += groundY - baseElevation;
            } else if (groundY < baseElevation) {
                for (int y = groundY + 1; y <= baseElevation; y++) {
                    BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                    BuildingBlockWriter.recordBlock(result, pos, fillBlockId, projectionHandler);
                }
                result.fillVolume += baseElevation - groundY;
            }
        }
    }
}
