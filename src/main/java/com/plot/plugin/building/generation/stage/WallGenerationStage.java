package com.plot.plugin.building.generation.stage;

import com.plot.api.world.IBlockProjectionService;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.BuildingBlockWriter;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.massing.FloorPlateGeometryResolver.ResolvedFloorPlate;
import com.plot.plugin.building.generation.massing.InnerOffsetDegradation;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.EnvelopeSpec;
import com.plot.plugin.building.model.spec.MassingSpec;
import net.minecraft.util.math.BlockPos;

/**
 * 墙体生成：按 FloorPlate 逐层挤出外轮廓内、内轮廓外的柱列。
 * <p>
 * 降级策略见 {@link InnerOffsetDegradation}。
 */
public final class WallGenerationStage implements BuildingGenerationStage {
    @Override
    public String name() {
        return "wall";
    }

    @Override
    public void generate(BuildingGenerationContext context) {
        BuildingGenerationResult result = context.getResult();
        BuildingDefinition definition = context.getDefinition();
        MassingSpec massing = definition.massing();
        EnvelopeSpec envelope = definition.envelope();
        int baseElevation = context.getBaseElevation();
        IBlockProjectionService projectionHandler = context.getProjectionService();
        int floorHeight = massing.floorHeight();

        for (int floor = 0; floor < massing.floors(); floor++) {
            ResolvedFloorPlate plate = context.resolvedFloorPlate(floor);
            Polygon outerPolygon = plate.outerPolygon();
            Polygon innerPolygon = plate.innerPolygon();
            if (!plate.hasInteriorSpace()) {
                InnerOffsetDegradation.noteInnerOffsetFailure(result);
            }
            int yStart = baseElevation + floor * floorHeight;
            int yEnd = yStart + floorHeight;

            for (BuildingGenerationContext.GridCell cell : plate.outerCells()) {
                if (!InnerOffsetDegradation.isWallMassCell(outerPolygon, innerPolygon, cell.center())) {
                    continue;
                }
                BlockPos column = context.canvasToColumn(cell.center());
                for (int y = yStart; y < yEnd; y++) {
                    BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                    String wallBlockId = MaterialMixResolver.resolve(
                        envelope.wallMaterial(), pos, definition.id(),
                        BuildingGeometryUtils::resolveBlockId);
                    BuildingBlockWriter.recordBlock(result, pos, wallBlockId, projectionHandler);
                }
            }
        }
    }
}
