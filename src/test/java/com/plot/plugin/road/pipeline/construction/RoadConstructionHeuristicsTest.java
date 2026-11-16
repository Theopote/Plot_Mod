package com.plot.plugin.road.pipeline.construction;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoadConstructionHeuristicsTest {

    @Test
    void balancedStyleMatchesDefaultThresholds() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setTerrainStyle(RoadTerrainStyle.BALANCED);

        assertEquals(3, RoadConstructionHeuristics.bridgeThreshold(config));
        assertEquals(4, RoadConstructionHeuristics.tunnelThreshold(config));
        assertEquals(1.0, RoadConstructionHeuristics.MIN_STRUCTURE_RUN, 1e-9);
    }

    @Test
    void smoothStyleIsMoreAggressive() {
        assertEquals(2, RoadConstructionHeuristics.bridgeThreshold(RoadTerrainStyle.SMOOTH));
        assertEquals(3, RoadConstructionHeuristics.tunnelThreshold(RoadTerrainStyle.SMOOTH));
    }

    @Test
    void configOverrideBeatsTerrainStyleTable() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setTerrainStyle(RoadTerrainStyle.SMOOTH);
        assertEquals(2, RoadConstructionHeuristics.bridgeThreshold(config));

        config.setBridgeThreshold(8);
        assertEquals(8, RoadConstructionHeuristics.bridgeThreshold(config));
        assertEquals(8, RoadConstructionHeuristics.constructionConfig(config).bridgeThreshold());

        config.setBridgeThreshold(null);
        assertEquals(2, RoadConstructionHeuristics.bridgeThreshold(config));
    }

    @Test
    void roadOverrideBeatsConfigAndStyle() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setTerrainStyle(RoadTerrainStyle.FOLLOW);
        config.setBridgeThreshold(5);

        Road road = new Road("r1");
        assertEquals(5, RoadConstructionHeuristics.bridgeThreshold(road, config));

        road.setBridgeThreshold(12);
        assertEquals(12, RoadConstructionHeuristics.bridgeThreshold(road, config));
        assertEquals(12, road.getEffectiveBridgeThreshold(config));

        road.setBridgeThreshold(null);
        assertEquals(5, road.getEffectiveBridgeThreshold(config));

        config.setBridgeThreshold(null);
        assertEquals(6, road.getEffectiveBridgeThreshold(config));
    }

    @Test
    void stylePlusConfigResolutionHonorsExplicitOverrideArgument() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setTerrainStyle(RoadTerrainStyle.BALANCED);
        config.setBridgeThreshold(4);

        assertEquals(9, RoadConstructionHeuristics.bridgeThreshold(
            RoadTerrainStyle.FOLLOW, config, 9));
        assertEquals(4, RoadConstructionHeuristics.bridgeThreshold(
            RoadTerrainStyle.FOLLOW, config, null));
        assertNull(new Road().getBridgeThreshold());
    }
}
