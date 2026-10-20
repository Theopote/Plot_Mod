package com.plot.plugin.road.solid;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadVoxelRasterizerTest {

    @Test
    void junctionExcavationProtectsAnActualSlopingDeckBelowNominalClearancePlane() {
        var deck = new net.minecraft.util.math.BlockPos(3, 69, 0);
        var aggregate = new RoadGenerationResult(0);
        aggregate.recordPlacementByLayerPriority(deck, "minecraft:stone", "minecraft:white_concrete",
            RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);
        var solids = new RoadSolidModel();
        solids.add(new Vec2d(3, 0), 69, RoadSolidLayer.TUNNEL, "minecraft:air", RoadSolidMaterialPriority.CLEARANCE);
        solids.add(new Vec2d(3, 0), 68, RoadSolidLayer.TUNNEL, "minecraft:air", RoadSolidMaterialPriority.CLEARANCE);
        RoadVoxelRasterizer.flushJunctionSolids(aggregate, solids,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance(), null, null, null, java.util.Set.of(deck));
        assertEquals("minecraft:white_concrete", aggregate.placementRecords.get(deck).newBlockId);
        assertEquals("minecraft:air", aggregate.placementRecords.get(deck.down()).newBlockId);
    }

    @Test
    void connectedPlanPointsFillFourConnectedDiagonal() {
        List<Vec2d> points = RoadVoxelRasterizer.sampleConnectedPlanPoints(
            new Vec2d(0, 0), new Vec2d(2, 2));

        assertEquals(5, points.size());
        assertEquals(0, points.getFirst().x, 1e-9);
        assertEquals(0, points.getFirst().y, 1e-9);
        assertEquals(2, points.getLast().x, 1e-9);
        assertEquals(2, points.getLast().y, 1e-9);
        for (int i = 1; i < points.size(); i++) {
            int manhattan = (int) Math.round(Math.abs(points.get(i).x - points.get(i - 1).x)
                + Math.abs(points.get(i).y - points.get(i - 1).y));
            assertEquals(1, manhattan);
        }
    }

    @Test
    void connectedPlanPointsKeepAxisAlignedStrip() {
        List<Vec2d> points = RoadVoxelRasterizer.sampleConnectedPlanPoints(
            new Vec2d(2, 5), new Vec2d(6, 5));
        assertEquals(5, points.size());
        assertTrue(points.stream().allMatch(p -> Math.abs(p.y - 5) < 1e-9));
    }
}
