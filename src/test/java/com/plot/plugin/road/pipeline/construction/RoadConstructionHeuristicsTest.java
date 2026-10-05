package com.plot.plugin.road.pipeline.construction;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadConstructionHeuristicsTest {

    @Test
    void balancedStyleMatchesDefaultThresholds() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setTerrainStyle(RoadTerrainStyle.BALANCED);

        assertEquals(3, RoadConstructionHeuristics.bridgeThreshold(config));
        assertEquals(4, RoadConstructionHeuristics.tunnelThreshold(config));
    }

    @Test
    void smoothStyleIsMoreAggressive() {
        assertEquals(2, RoadConstructionHeuristics.bridgeThreshold(RoadTerrainStyle.SMOOTH));
        assertEquals(3, RoadConstructionHeuristics.tunnelThreshold(RoadTerrainStyle.SMOOTH));
    }
}
