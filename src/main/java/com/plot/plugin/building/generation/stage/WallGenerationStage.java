package com.plot.plugin.building.generation.stage;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.BuildingBlockWriter;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.massing.FloorPlateGeometryResolver;
import com.plot.plugin.building.generation.massing.FloorPlateGeometryResolver.ResolvedFloorPlate;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.EnvelopeSpec;
import com.plot.plugin.building.model.spec.MassingSpec;
import net.minecraft.util.math.BlockPos;

/**
 * 墙体生成：按 FloorPlate 逐层挤出外轮廓内、内轮廓外的柱列。
 * <p>
 * inner offset 失败时仍生成实心墙体量（与 Floor 阶段跳过室内楼板区分）。
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
            ResolvedFloorPlate plate = FloorPlateGeometryResolver.resolve(
                massing.plateForFloor(floor), envelope.wallThickness());
            Polygon outerPolygon = plate.outerPolygon();
            Polygon innerPolygon = plate.innerPolygon();
            int yStart = baseElevation + floor * floorHeight;
            int yEnd = yStart + floorHeight;

            for (BuildingGenerationContext.GridCell cell : plate.outerCells()) {
                Vec2d center = cell.center();
                if (!outerPolygon.contains(center)) {
                    continue;
                }
                if (innerPolygon != null && innerPolygon.contains(center)) {
                    continue;
                }
                BlockPos column = BuildingGeometryUtils.canvasToBlockXZ(
                    center, context.getCoordinateService());
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
