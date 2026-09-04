package com.plot.plugin.building.generation.component;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.BuildingBlockWriter;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.massing.FloorPlateGeometryResolver;
import com.plot.plugin.building.generation.massing.FloorPlateGeometryResolver.ResolvedFloorPlate;
import com.plot.plugin.building.model.spec.AccessorySpec;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.EnvelopeSpec;
import com.plot.plugin.building.model.spec.MassingSpec;
import com.plot.plugin.building.model.spec.ParapetSpec;
import net.minecraft.util.math.BlockPos;

/**
 * 女儿墙：沿顶层外墙环带向上延伸。
 */
public final class ParapetGenerator {
    private ParapetGenerator() {
    }

    public static void generate(BuildingGenerationContext context, ParapetSpec spec) {
        if (spec == null || !spec.enabled()) {
            return;
        }
        BuildingGenerationResult result = context.getResult();
        BuildingDefinition definition = context.getDefinition();
        MassingSpec massing = definition.massing();
        EnvelopeSpec envelope = definition.envelope();
        IBlockProjectionService projectionService = context.getProjectionService();

        int topFloorIndex = Math.max(0, massing.floors() - 1);
        ResolvedFloorPlate plate = FloorPlateGeometryResolver.resolve(
            massing.plateForFloor(topFloorIndex), envelope.wallThickness());
        Polygon outerPolygon = plate.outerPolygon();
        Polygon innerPolygon = plate.innerPolygon();
        if (innerPolygon == null) {
            return;
        }

        int topWallY = context.getTopFloorY();
        String blockId = BuildingGeometryUtils.resolveBlockId(spec.resolvedMaterial());

        for (BuildingGenerationContext.GridCell cell : plate.outerCells()) {
            Vec2d center = cell.center();
            if (!outerPolygon.contains(center) || innerPolygon.contains(center)) {
                continue;
            }
            BlockPos column = BuildingGeometryUtils.canvasToBlockXZ(
                center, context.getCoordinateService());
            for (int layer = 0; layer < spec.height(); layer++) {
                BlockPos pos = new BlockPos(column.getX(), topWallY + layer, column.getZ());
                BuildingBlockWriter.recordBlock(result, pos, blockId, projectionService);
            }
        }
    }

    public static void generate(BuildingGenerationContext context) {
        AccessorySpec accessory = context.getDefinition().accessory();
        generate(context, accessory.parapet());
    }
}
