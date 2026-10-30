package com.plot.plugin.road.pipeline.marking;

import com.plot.core.block.BlockSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadRailShapeResolverTest {

    @Test
    void straightNeighborsResolveCardinalShapes() {
        assertEquals(
            RoadRailShapeResolver.SHAPE_NORTH_SOUTH,
            RoadRailShapeResolver.resolveShape(
                "minecraft:rail",
                RoadRailShapeResolver.RailNeighbors.flat(true, true, false, false)));
        assertEquals(
            RoadRailShapeResolver.SHAPE_EAST_WEST,
            RoadRailShapeResolver.resolveShape(
                "minecraft:rail",
                RoadRailShapeResolver.RailNeighbors.flat(false, false, true, true)));
    }

    @Test
    void curveNeighborsResolveCornerShapes() {
        assertEquals(
            RoadRailShapeResolver.SHAPE_NORTH_EAST,
            RoadRailShapeResolver.resolveShape(
                "minecraft:rail",
                RoadRailShapeResolver.RailNeighbors.flat(true, false, true, false)));
        assertEquals(
            RoadRailShapeResolver.SHAPE_SOUTH_WEST,
            RoadRailShapeResolver.resolveShape(
                "minecraft:rail",
                RoadRailShapeResolver.RailNeighbors.flat(false, true, false, true)));
    }

    @Test
    void poweredRailForcesStraightInsteadOfCurve() {
        assertEquals(
            RoadRailShapeResolver.SHAPE_EAST_WEST,
            RoadRailShapeResolver.resolveShape(
                "minecraft:powered_rail",
                RoadRailShapeResolver.RailNeighbors.flat(true, false, true, false)));
    }

    @Test
    void ascendingUsesHigherNeighborDirection() {
        RoadRailShapeResolver.RailNeighbors eastUp = new RoadRailShapeResolver.RailNeighbors(
            false, false, true, true, 0, 0, 1, 0);
        assertEquals(
            RoadRailShapeResolver.SHAPE_ASCENDING_EAST,
            RoadRailShapeResolver.resolveShape("minecraft:rail", eastUp));

        RoadRailShapeResolver.RailNeighbors westDownMeansAscendEast =
            new RoadRailShapeResolver.RailNeighbors(false, false, true, true, 0, 0, 0, -1);
        assertEquals(
            RoadRailShapeResolver.SHAPE_ASCENDING_EAST,
            RoadRailShapeResolver.resolveShape("minecraft:rail", westDownMeansAscendEast));
    }

    @Test
    void throughNorthSouthPrefersFlatOverSideAscending() {
        // T：南北贯通 + 东侧高一格，不得被旁支爬坡抢走直股
        RoadRailShapeResolver.RailNeighbors tee = new RoadRailShapeResolver.RailNeighbors(
            true, true, true, false, 0, 0, 1, 0);
        assertEquals(
            RoadRailShapeResolver.SHAPE_NORTH_SOUTH,
            RoadRailShapeResolver.resolveShape(
                "minecraft:rail", tee, RoadRailShapeResolver.AxisHint.NORTH_SOUTH));
        assertEquals(
            RoadRailShapeResolver.SHAPE_NORTH_SOUTH,
            RoadRailShapeResolver.resolveShape("minecraft:rail", tee));
    }

    @Test
    void resolvePlacementPreservesNonShapeProperties() {
        String placement = RoadRailShapeResolver.resolvePlacement(
            "minecraft:powered_rail[powered=true,shape=north_south]",
            RoadRailShapeResolver.RailNeighbors.flat(false, false, true, true));
        BlockSpec spec = BlockSpec.parse(placement);
        assertEquals("minecraft:powered_rail", spec.blockId());
        assertEquals("true", spec.property("powered"));
        assertEquals("east_west", spec.property("shape"));
    }

    @Test
    void mutuallyConnectedRequiresBothShapesFaceEachOther() {
        assertTrue(RoadRailShapeResolver.mutuallyConnected(
            RoadRailShapeResolver.SHAPE_EAST_WEST,
            RoadRailShapeResolver.Cardinal.EAST,
            RoadRailShapeResolver.SHAPE_EAST_WEST));
        assertFalse(RoadRailShapeResolver.mutuallyConnected(
            RoadRailShapeResolver.SHAPE_NORTH_SOUTH,
            RoadRailShapeResolver.Cardinal.EAST,
            RoadRailShapeResolver.SHAPE_EAST_WEST));
    }

    @Test
    void threeWayPrefersThroughStraight() {
        assertEquals(
            RoadRailShapeResolver.SHAPE_NORTH_SOUTH,
            RoadRailShapeResolver.resolveShape(
                "minecraft:rail",
                RoadRailShapeResolver.RailNeighbors.flat(true, true, true, false)));
    }
}
