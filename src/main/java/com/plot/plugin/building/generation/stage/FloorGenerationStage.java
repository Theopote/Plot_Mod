package com.plot.plugin.building.generation.stage;

import com.plot.api.world.IBlockProjectionService;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.BuildingBlockWriter;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.EnvelopeSpec;
import com.plot.plugin.building.model.spec.MassingSpec;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * 楼板生成，并在同一阶段内完成顶层屋面材质替换（Phase 0 约定）。
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
        Polygon innerPolygon = context.getInnerPolygon();
        if (innerPolygon == null) {
            return;
        }

        BuildingGenerationResult result = context.getResult();
        BuildingDefinition definition = context.getDefinition();
        MassingSpec massing = definition.massing();
        EnvelopeSpec envelope = definition.envelope();
        int baseElevation = context.getBaseElevation();
        IBlockProjectionService projectionHandler = context.getProjectionService();

        List<BuildingGenerationContext.GridCell> innerCells = BuildingGenerationContext.collectFootprintCells(
            BuildingGeometryUtils.copyPoints(innerPolygon.getPoints()), innerPolygon);

        for (int floor = 0; floor <= massing.floors(); floor++) {
            int floorY = baseElevation + floor * massing.floorHeight();
            for (BuildingGenerationContext.GridCell cell : innerCells) {
                if (!innerPolygon.contains(cell.center())) {
                    continue;
                }
                BlockPos column = BuildingGeometryUtils.canvasToBlockXZ(
                    cell.center(), context.getCoordinateService());
                BlockPos pos = new BlockPos(column.getX(), floorY, column.getZ());
                String floorBlockId = MaterialMixResolver.resolve(
                    envelope.floorMaterial(), pos, definition.id(),
                    BuildingGeometryUtils::resolveBlockId);
                BuildingBlockWriter.recordBlock(result, pos, floorBlockId, projectionHandler);
            }
        }
    }

    private void replaceTopFloorMaterial(BuildingGenerationContext context) {
        Polygon innerPolygon = context.getInnerPolygon();
        if (innerPolygon == null) {
            return;
        }
        BuildingGenerationResult result = context.getResult();
        int topFloorY = context.getTopFloorY();
        String roofBlockId = context.getRoofBlockId();
        IBlockProjectionService projectionHandler = context.getProjectionService();

        List<BuildingGenerationContext.GridCell> innerCells = BuildingGenerationContext.collectFootprintCells(
            BuildingGeometryUtils.copyPoints(innerPolygon.getPoints()), innerPolygon);
        for (BuildingGenerationContext.GridCell cell : innerCells) {
            if (!innerPolygon.contains(cell.center())) {
                continue;
            }
            BlockPos column = BuildingGeometryUtils.canvasToBlockXZ(
                cell.center(), context.getCoordinateService());
            BlockPos pos = new BlockPos(column.getX(), topFloorY, column.getZ());
            BuildingBlockWriter.recordBlock(result, pos, roofBlockId, projectionHandler);
        }
    }
}
