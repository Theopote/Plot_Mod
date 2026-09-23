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

import java.util.List;

/**
 * 楼板生成，并在同一阶段内完成顶层屋面材质替换（Phase 0 约定）。
 * 每层楼板使用对应 FloorPlate 的内轮廓。
 * <p>
 * inner offset 失败时跳过室内楼板，见 {@link InnerOffsetDegradation}。
 */
public final class FloorGenerationStage implements BuildingGenerationStage {
    @Override
    public String name() {
        return "floor";
    }

    @Override
    public void generate(BuildingGenerationContext context) {
        generateFloors(context);
        replaceTopFloorMaterial(context);
    }

    private void generateFloors(BuildingGenerationContext context) {
        BuildingGenerationResult result = context.getResult();
        BuildingDefinition definition = context.getDefinition();
        MassingSpec massing = definition.massing();
        EnvelopeSpec envelope = definition.envelope();
        int baseElevation = context.getBaseElevation();
        IBlockProjectionService projectionHandler = context.getProjectionService();

        for (int floor = 0; floor <= massing.floors(); floor++) {
            int plateFloor = Math.min(floor, Math.max(0, massing.floors() - 1));
            ResolvedFloorPlate plate = context.resolvedFloorPlate(plateFloor);
            Polygon innerPolygon = plate.innerPolygon();
            if (!InnerOffsetDegradation.hasInteriorSpace(innerPolygon)) {
                continue;
            }
            int floorY = baseElevation + floor * massing.floorHeight();
            double blockCellSize = context.getCanvasScale().uniformBlocksToCanvas(
                1.0, plate.outerPoints());
            List<BuildingGenerationContext.GridCell> innerCells = BuildingGenerationContext.collectFootprintCells(
                plate.innerPoints(), innerPolygon, blockCellSize);
            for (BuildingGenerationContext.GridCell cell : innerCells) {
                if (!InnerOffsetDegradation.isInteriorCell(innerPolygon, cell.center())) {
                    continue;
                }
                BlockPos column = context.canvasToColumn(cell.center());
                BlockPos pos = new BlockPos(column.getX(), floorY, column.getZ());
                String floorBlockId = MaterialMixResolver.resolve(
                    envelope.floorMaterial(), pos, definition.id(),
                    BuildingGeometryUtils::resolveBlockId);
                BuildingBlockWriter.recordBlock(result, pos, floorBlockId, projectionHandler);
            }
        }
    }

    private void replaceTopFloorMaterial(BuildingGenerationContext context) {
        BuildingGenerationResult result = context.getResult();
        BuildingDefinition definition = context.getDefinition();
        MassingSpec massing = definition.massing();
        int topPlateFloor = Math.max(0, massing.floors() - 1);
        ResolvedFloorPlate topPlate = context.resolvedFloorPlate(topPlateFloor);
        if (topPlate.outerCells().isEmpty()) {
            return;
        }
        int topFloorY = context.getTopFloorY();
        String roofBlockId = context.getRoofBlockId();
        IBlockProjectionService projectionHandler = context.getProjectionService();

        // 平顶覆盖整个外轮廓（含外墙环带），与墙体顶面齐平
        for (BuildingGenerationContext.GridCell cell : topPlate.outerCells()) {
            BlockPos column = context.canvasToColumn(cell.center());
            BlockPos pos = new BlockPos(column.getX(), topFloorY, column.getZ());
            BuildingBlockWriter.recordBlock(result, pos, roofBlockId, projectionHandler);
        }
    }
}
