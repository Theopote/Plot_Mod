package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路面掩膜 / 井筒清障覆盖 TUNNEL 后必须同步隧道期望。
 */
class RoadSurfacePlacementPassExpectationTest {

    @Test
    void clearShaftColumnAdoptsGradeSeparationCavity() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        BlockPos surface = new BlockPos(0, 64, 0);
        BlockPos subgrade = new BlockPos(0, 66, 0);
        BlockPos elevated = new BlockPos(0, 72, 0);

        result.recordPlacementOverride(surface, "minecraft:air", "minecraft:stone");
        result.placementLayers.put(surface, RoadSolidLayer.ROAD);
        result.openRoadClearanceTops.put(surface, 70);

        result.recordPlacementOverride(subgrade, "minecraft:air", "minecraft:dirt");
        result.placementLayers.put(subgrade, RoadSolidLayer.SUBGRADE);
        result.recordExpectedTunnelLining(subgrade);

        result.recordPlacementOverride(elevated, "minecraft:air", "minecraft:stone");
        result.placementLayers.put(elevated, RoadSolidLayer.ROAD);

        RoadSurfacePlacementPass.clearIntrudingFill(result);

        assertTrue("minecraft:air".equals(result.placementRecords.get(subgrade).newBlockId));
        assertFalse(result.expectedTunnelLining.contains(subgrade));
        assertTrue(result.expectedTunnelCavity.contains(subgrade));
        assertTrue(result.placementClearancePurposes.isEmpty()
                || result.placementClearancePurposes.get(subgrade) == null
                || result.placementClearancePurposes.get(subgrade) == ClearancePurpose.GRADE_SEPARATION
                || result.expectedTunnelCavity.contains(subgrade));
    }
}
