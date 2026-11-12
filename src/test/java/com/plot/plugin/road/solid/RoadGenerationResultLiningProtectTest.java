package com.plot.plugin.road.solid;

import com.plot.plugin.road.pipeline.corridor.ClearancePurpose;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 合法隧道衬砌不得被边坡开挖空气覆盖。
 */
class RoadGenerationResultLiningProtectTest {

    @Test
    void mergeFromDoesNotLetSlopeExcavationAirEraseExpectedTunnelLining() {
        RoadGenerationResult base = new RoadGenerationResult(0);
        BlockPos lining = new BlockPos(3, 70, 5);
        base.recordPlacementOverride(lining, "minecraft:stone", "minecraft:stone_bricks");
        base.placementLayers.put(lining, RoadSolidLayer.TUNNEL);
        base.placementMaterialPriorities.put(lining, RoadSolidMaterialPriority.STRUCTURAL);
        base.expectedTunnelLining.add(lining);

        RoadGenerationResult clearance = new RoadGenerationResult(0);
        clearance.recordPlacementByLayerPriority(
            lining,
            "minecraft:stone_bricks",
            "minecraft:air",
            RoadSolidLayer.TUNNEL,
            RoadSolidMaterialPriority.CLEARANCE,
            ClearancePurpose.SLOPE_EXCAVATION);

        base.mergeFrom(clearance);

        assertEquals("minecraft:stone_bricks", base.placementRecords.get(lining).newBlockId,
            "expectedTunnelLining must survive slope-excavation air merge");
    }
}
