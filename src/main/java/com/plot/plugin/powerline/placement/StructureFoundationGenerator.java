package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.WorldCoordinateUtils;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.powerline.PoleFrame;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.TowerLocalPoint;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureGeometry;
import net.minecraft.util.math.BlockPos;

/** 单柱与铁塔多脚基础填充（水面/坡地柱脚延伸）。 */
public final class StructureFoundationGenerator {
    private StructureFoundationGenerator() {
    }

    public static void fillCenterColumn(
            Vec2d planPoint,
            PolePlacementBase placementBase,
            MaterialMix poleMaterial,
            String footprintId,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            ICoordinateService coordinates) {
        if (placementBase == null || !placementBase.requiresUnderwaterFill()) {
            return;
        }
        BlockPos column = WorldCoordinateUtils.canvasToBlockXZ(planPoint, coordinates);
        fillColumn(
            column,
            placementBase.engineeringGroundY() + 1,
            placementBase.buildBaseY(),
            poleMaterial,
            footprintId,
            result,
            projection);
    }

    public static void fillTowerLegFoundations(
            TowerFoundationPlan plan,
            TowerStation baseStation,
            PoleFrame frame,
            MaterialMix poleMaterial,
            String footprintId,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            ICoordinateService coordinates) {
        if (plan == null || baseStation == null || frame == null || result == null) {
            return;
        }
        for (int corner = 0; corner < TowerFoundationPlan.CORNER_COUNT; corner++) {
            if (!plan.requiresLegFill(corner)) {
                continue;
            }
            TowerLocalPoint cornerPoint = TowerStructureGeometry.cornerPoint(baseStation, corner);
            Vec2d planPoint = frame.toPlanPoint(cornerPoint.lateral(), cornerPoint.longitudinal());
            BlockPos column = WorldCoordinateUtils.canvasToBlockXZ(planPoint, coordinates);
            fillColumn(
                column,
                plan.fillBottomY(corner),
                plan.fillTopY(corner),
                poleMaterial,
                footprintId,
                result,
                projection);
        }
    }

    private static void fillColumn(
            BlockPos column,
            int fromYInclusive,
            int toYInclusive,
            MaterialMix poleMaterial,
            String footprintId,
            PowerLineGenerationResult result,
            IBlockProjectionService projection) {
        if (fromYInclusive > toYInclusive) {
            return;
        }
        for (int y = fromYInclusive; y <= toYInclusive; y++) {
            BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
            String blockId = MaterialMixResolver.resolve(poleMaterial, pos, footprintId);
            PlacementWriter.put(result, projection, pos, blockId, PlacementCategory.FOUNDATION);
        }
    }
}
