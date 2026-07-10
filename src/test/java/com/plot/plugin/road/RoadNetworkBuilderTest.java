package com.plot.plugin.road;

import com.plot.plugin.road.model.RoadEdge;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadNetworkBuilderTest {

    @Test
    void splitSlopeOverridesRemapsMileage() {
        List<RoadEdge.SlopeOverride> overrides = List.of(
            new RoadEdge.SlopeOverride(10, 20, 3.0f)
        );

        List<RoadEdge.SlopeOverride> first = RoadNetworkBuilder.splitSlopeOverrides(overrides, 15, 30, true);
        assertEquals(1, first.size());
        assertEquals(10, first.getFirst().startDistance, 1e-6);
        assertEquals(15, first.getFirst().endDistance, 1e-6);
        assertEquals(3.0f, first.getFirst().maxSlope);

        List<RoadEdge.SlopeOverride> second = RoadNetworkBuilder.splitSlopeOverrides(overrides, 15, 30, false);
        assertEquals(1, second.size());
        assertEquals(0, second.getFirst().startDistance, 1e-6);
        assertEquals(5, second.getFirst().endDistance, 1e-6);
        assertEquals(3.0f, second.getFirst().maxSlope);
    }

    @Test
    void splitSlopeOverridesDropsOutOfRangeSegments() {
        List<RoadEdge.SlopeOverride> overrides = List.of(
            new RoadEdge.SlopeOverride(0, 5, 2.0f),
            new RoadEdge.SlopeOverride(25, 30, 4.0f)
        );

        List<RoadEdge.SlopeOverride> first = RoadNetworkBuilder.splitSlopeOverrides(overrides, 15, 30, true);
        assertEquals(1, first.size());
        assertEquals(0, first.getFirst().startDistance, 1e-6);
        assertEquals(5, first.getFirst().endDistance, 1e-6);

        List<RoadEdge.SlopeOverride> second = RoadNetworkBuilder.splitSlopeOverrides(overrides, 15, 30, false);
        assertEquals(1, second.size());
        assertEquals(10, second.getFirst().startDistance, 1e-6);
        assertEquals(15, second.getFirst().endDistance, 1e-6);
    }
}
