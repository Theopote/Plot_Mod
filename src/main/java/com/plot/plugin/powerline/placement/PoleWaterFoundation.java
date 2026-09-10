package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.core.command.BlockRecord;
import com.plot.core.geometry.WorldCoordinateUtils;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import net.minecraft.util.math.BlockPos;

/** 水面电杆：自湖底向上填实至水面，杆体从水面之上起建。 */
public final class PoleWaterFoundation {
    private PoleWaterFoundation() {
    }

    public static void fillBelowBuildBase(
            Vec2d planPoint,
            PolePlacementBase base,
            MaterialMix poleMaterial,
            String footprintId,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            ICoordinateService coordinates) {
        if (base == null || !base.requiresUnderwaterFill() || result == null || projection == null) {
            return;
        }
        BlockPos column = WorldCoordinateUtils.canvasToBlockXZ(planPoint, coordinates);
        for (int y = base.engineeringGroundY() + 1; y <= base.buildBaseY(); y++) {
            BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
            String blockId = MaterialMixResolver.resolve(poleMaterial, pos, footprintId);
            recordBlock(result, projection, pos, blockId);
        }
    }

    private static void recordBlock(
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            BlockPos pos,
            String newBlockId) {
        BlockRecord existing = result.placementRecords.get(pos);
        if (existing != null) {
            result.placementRecords.put(pos, new BlockRecord(pos, existing.previousBlockId, newBlockId));
            return;
        }
        String previous = projection.getBlockIdAt(pos);
        result.placementRecords.put(pos, new BlockRecord(pos, previous, newBlockId));
    }
}
