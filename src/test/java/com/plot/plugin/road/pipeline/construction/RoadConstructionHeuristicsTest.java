package com.plot.plugin.road.pipeline.construction;

import com.plot.plugin.config.RoadSystemConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadConstructionHeuristicsTest {

    @Test
    void balancedPresetMatchesLegacyDefaults() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setTerrainAdaptation(RoadConstructionHeuristics.TerrainAdaptationPreset.BALANCED);

        assertEquals(3, RoadConstructionHeuristics.bridgeThreshold(config));
        assertEquals(4, RoadConstructionHeuristics.tunnelThreshold(config));
    }

    @Test
    void flattenPresetIsMoreAggressive() {
        assertEquals(2, RoadConstructionHeuristics.bridgeThreshold(
            RoadConstructionHeuristics.TerrainAdaptationPreset.FLATTEN));
        assertEquals(3, RoadConstructionHeuristics.tunnelThreshold(
            RoadConstructionHeuristics.TerrainAdaptationPreset.FLATTEN));
    }
}
