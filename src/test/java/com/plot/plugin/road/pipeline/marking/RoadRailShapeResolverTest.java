package com.plot.plugin.road.pipeline.marking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void resolvePlacementWritesShapeProperty() {
        String placement = RoadRailShapeResolver.resolvePlacement(
            "minecraft:rail",
            RoadRailShapeResolver.RailNeighbors.flat(false, false, true, true));
        assertTrue(placement.contains("shape=east_west"));
        assertTrue(placement.startsWith("minecraft:rail"));
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
