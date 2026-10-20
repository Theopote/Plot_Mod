package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.solid.*;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RoadSurfacePlacementPassTest {
    @Test
    void pavementWinsOverTunnelAliasingAndRetainsOriginalUndoState() {
        var plan = plan();
        for (String obstructingMaterial : List.of("minecraft:air", "minecraft:stone_bricks")) {
            var result = new RoadGenerationResult(0);
            var surface = new BlockPos(3, 69, -3);
            result.recordPlacementByLayerPriority(surface, "minecraft:grass_block", obstructingMaterial,
                RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.STRUCTURAL);
            RoadSurfacePlacementPass.apply(result, plan, 5);
            assertEquals("minecraft:white_concrete", result.placementRecords.get(surface).newBlockId);
            assertEquals("minecraft:grass_block", result.placementRecords.get(surface).previousBlockId);
            assertEquals(RoadSolidLayer.ROAD, result.placementLayers.get(surface));
        }
    }

    @Test
    void roadMarkingsSurviveAndMissingRecordsDoNotGetInvented() {
        var result = new RoadGenerationResult(0);
        RoadSurfacePlacementPass.apply(result, plan(), 5);
        assertTrue(result.placementRecords.isEmpty());
        var surface = new BlockPos(3, 69, -3);
        result.recordPlacementByLayerPriority(surface, "minecraft:stone", "minecraft:yellow_concrete",
            RoadSolidLayer.MARKING, RoadSolidMaterialPriority.DEFAULT);
        RoadSurfacePlacementPass.apply(result, plan(), 5);
        assertEquals("minecraft:yellow_concrete", result.placementRecords.get(surface).newBlockId);
    }

    @Test
    void mergedApproachsFillCannotOccupyReservedRoadHeadroom() {
        for (boolean roadFirst : new boolean[]{true, false}) {
            var road = new RoadGenerationResult(0);
            var surface = new BlockPos(3, 69, -3);
            road.recordPlacementByLayerPriority(surface, "minecraft:air", "minecraft:white_concrete",
                RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);
            RoadSurfacePlacementPass.apply(road, plan(), 5);
            var other = new RoadGenerationResult(0);
            other.recordPlacementByLayerPriority(surface.up(2), "minecraft:air", "minecraft:coarse_dirt",
                RoadSolidLayer.SUBGRADE, RoadSolidMaterialPriority.DEFAULT);
            other.recordPlacementByLayerPriority(surface.up(5), "minecraft:air", "minecraft:stone",
                RoadSolidLayer.BRIDGE, RoadSolidMaterialPriority.STRUCTURAL);
            var merged = new RoadGenerationResult(0);
            merged.mergeFrom(roadFirst ? road : other);
            merged.mergeFrom(roadFirst ? other : road);
            RoadSurfacePlacementPass.clearIntrudingFill(merged);
            assertEquals("minecraft:air", merged.placementRecords.get(surface.up(2)).newBlockId);
            assertEquals("minecraft:stone", merged.placementRecords.get(surface.up(5)).newBlockId);
        }
    }

    private static RoadCorridorPlan plan() {
        var plan = new RoadCorridorPlan();
        var key = new ColumnKey(3, -3);
        plan.surfaceMask().put(key, new SurfaceCell(new Vec2d(3, -3), 69,
            RoadSolidLayer.ROAD, "minecraft:white_concrete"));
        plan.putClearance(key, new ClearanceCell(new Vec2d(3, -3), 69, 70,
            RoadCorridorConstructionMode.OPEN, com.plot.plugin.road.RoadConstructionType.FILL));
        return plan;
    }
}
