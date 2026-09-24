package com.plot.plugin.building.generation.stage;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.BuildingBlockWriter;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationContext.GridCell;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.massing.FloorPlateGeometryResolver.ResolvedFloorPlate;
import com.plot.plugin.building.generation.massing.InnerOffsetDegradation;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.EnvelopeSpec;
import com.plot.plugin.building.model.spec.MassingSpec;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 框架生成：地基平面、转角柱、每层外圈梁、顶层屋顶轮廓线（单方块厚，无墙体/窗/屋面填充）。
 */
public final class FrameGenerationStage implements BuildingGenerationStage {
    @Override
    public String name() {
        return "frame";
    }

    @Override
    public void generate(BuildingGenerationContext context) {
        BuildingGenerationResult result = context.getResult();
        BuildingDefinition definition = context.getDefinition();
        MassingSpec massing = definition.massing();
        EnvelopeSpec envelope = definition.envelope();
        int baseElevation = context.getBaseElevation();
        int topFloorY = context.getTopFloorY();
        int floorHeight = massing.floorHeight();
        IBlockProjectionService projectionHandler = context.getProjectionService();

        placeFoundationPlane(context, result, baseElevation, projectionHandler);
        placeCornerColumns(
            context, result, definition, envelope, baseElevation, topFloorY, projectionHandler);
        for (int floor = 0; floor < massing.floors(); floor++) {
            int beamY = baseElevation + (floor + 1) * floorHeight;
            ResolvedFloorPlate plate = context.resolvedFloorPlate(floor);
            placePerimeterRing(
                context, result, definition, envelope, plate, beamY, projectionHandler);
        }
    }

    private static void placeFoundationPlane(
            BuildingGenerationContext context,
            BuildingGenerationResult result,
            int baseElevation,
            IBlockProjectionService projectionHandler) {
        String fillBlockId = context.getFoundationFillBlockId();
        for (GridCell cell : context.getFootprintCells()) {
            BlockPos column = context.canvasToColumn(cell.center());
            BlockPos pos = new BlockPos(column.getX(), baseElevation, column.getZ());
            BuildingBlockWriter.recordBlock(result, pos, fillBlockId, projectionHandler);
        }
    }

    private static void placeCornerColumns(
            BuildingGenerationContext context,
            BuildingGenerationResult result,
            BuildingDefinition definition,
            EnvelopeSpec envelope,
            int baseElevation,
            int topFloorY,
            IBlockProjectionService projectionHandler) {
        List<Vec2d> outerPoints = context.getOuterPoints();
        if (outerPoints == null || outerPoints.size() < 3) {
            return;
        }

        Set<Long> cornerColumns = new LinkedHashSet<>();
        for (Vec2d vertex : outerPoints) {
            BlockPos column = context.canvasToColumn(vertex);
            cornerColumns.add(packColumn(column.getX(), column.getZ()));
        }

        for (long packed : cornerColumns) {
            int x = (int) (packed >> 32);
            int z = (int) packed;
            for (int y = baseElevation + 1; y <= topFloorY; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                String blockId = MaterialMixResolver.resolve(
                    envelope.wallMaterial(), pos, definition.id(),
                    BuildingGeometryUtils::resolveBlockId);
                BuildingBlockWriter.recordBlock(result, pos, blockId, projectionHandler);
            }
        }
    }

    private static void placePerimeterRing(
            BuildingGenerationContext context,
            BuildingGenerationResult result,
            BuildingDefinition definition,
            EnvelopeSpec envelope,
            ResolvedFloorPlate plate,
            int y,
            IBlockProjectionService projectionHandler) {
        Polygon outerPolygon = plate.outerPolygon();
        Polygon innerPolygon = plate.innerPolygon();
        for (GridCell cell : plate.outerCells()) {
            Vec2d center = cell.center();
            if (!InnerOffsetDegradation.isWallMassCell(outerPolygon, innerPolygon, center)) {
                continue;
            }
            BlockPos column = context.canvasToColumn(center);
            BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
            String blockId = MaterialMixResolver.resolve(
                envelope.wallMaterial(), pos, definition.id(),
                BuildingGeometryUtils::resolveBlockId);
            BuildingBlockWriter.recordBlock(result, pos, blockId, projectionHandler);
        }
    }

    private static long packColumn(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }
}
