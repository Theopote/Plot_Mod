package com.plot.plugin.building.generation.stage;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.BuildingBlockWriter;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.EnvelopeSpec;
import net.minecraft.util.math.BlockPos;

/**
 * 墙体生成：外轮廓内、内轮廓外的柱列挤出。
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
        EnvelopeSpec envelope = definition.envelope();
        Polygon outerPolygon = context.getOuterPolygon();
        Polygon innerPolygon = context.getInnerPolygon();
        int baseElevation = context.getBaseElevation();
        IBlockProjectionService projectionHandler = context.getProjectionService();
        int topY = context.getTopFloorY();

        for (BuildingGenerationContext.GridCell cell : context.getFootprintCells()) {
            Vec2d center = cell.center();
            if (!outerPolygon.contains(center)) {
                continue;
            }
            if (innerPolygon != null && innerPolygon.contains(center)) {
                continue;
            }
            BlockPos column = BuildingGeometryUtils.canvasToBlockXZ(
                center, context.getCoordinateService());
            for (int y = baseElevation; y < topY; y++) {
                BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                String wallBlockId = MaterialMixResolver.resolve(
                    envelope.wallMaterial(), pos, definition.id(),
                    BuildingGeometryUtils::resolveBlockId);
                BuildingBlockWriter.recordBlock(result, pos, wallBlockId, projectionHandler);
            }
        }
    }
}
