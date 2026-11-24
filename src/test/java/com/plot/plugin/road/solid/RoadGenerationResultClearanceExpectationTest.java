package com.plot.plugin.road.solid;

import com.plot.plugin.road.pipeline.corridor.ClearancePurpose;
import com.plot.plugin.road.pipeline.corridor.ExpectedRoadSurface;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 合并净空胜出后必须同步作废路面/衬砌期望，避免终态验收假阳性。
 */
class RoadGenerationResultClearanceExpectationTest {

    @Test
    void tunnelClearanceMergeOpensLiningAndRegistersCavity() {
        RoadGenerationResult base = new RoadGenerationResult(0);
        BlockPos pos = new BlockPos(5, 64, 2);
        base.recordPlacementOverride(pos, "minecraft:stone", "minecraft:stone_bricks");
        base.placementLayers.put(pos, RoadSolidLayer.TUNNEL);
        base.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.STRUCTURAL);
        base.recordExpectedTunnelLining(pos);

        RoadGenerationResult clearance = new RoadGenerationResult(0);
        clearance.recordPlacementByLayerPriority(
            pos,
            "minecraft:stone_bricks",
            "minecraft:air",
            RoadSolidLayer.TUNNEL,
            RoadSolidMaterialPriority.AIR,
            ClearancePurpose.TUNNEL_INTERIOR);

        base.mergeFrom(clearance);

        assertFalse(base.expectedTunnelLining.contains(pos),
            "clearance must drop superseded lining expectation");
        assertTrue(base.expectedTunnelCavity.contains(pos),
            "tunnel-interior clearance must register cavity");
    }

    @Test
    void layerPriorityTunnelAirRevokesStalePavementExpectation() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        BlockPos pos = new BlockPos(6, 64, 2);
        result.recordPlacementOverride(pos, "minecraft:air", "minecraft:stone");
        result.placementLayers.put(pos, RoadSolidLayer.ROAD);
        result.recordExpectedRoadSurface(new ExpectedRoadSurface(
            pos, "minecraft:stone", RoadSolidLayer.ROAD, "edge"));

        result.recordPlacementByLayerPriority(
            pos,
            "minecraft:stone",
            "minecraft:air",
            RoadSolidLayer.TUNNEL,
            RoadSolidMaterialPriority.AIR,
            ClearancePurpose.TUNNEL_INTERIOR);

        assertFalse(result.expectedRoadSurfaces.containsKey(pos),
            "TUNNEL air outranking ROAD must revoke pavement expectation");
        assertTrue(result.expectedTunnelCavity.contains(pos));
    }
}
