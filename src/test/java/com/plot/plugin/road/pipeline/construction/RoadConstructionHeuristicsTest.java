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
        assertEquals(1.1f, RoadConstructionHeuristics.cutToFillBalanceRatio(config), 0.01f);
    }

    @Test
    void flattenPresetIsMoreAggressive() {
        assertEquals(2, RoadConstructionHeuristics.bridgeThreshold(
            RoadConstructionHeuristics.TerrainAdaptationPreset.FLATTEN));
        assertEquals(3, RoadConstructionHeuristics.tunnelThreshold(
            RoadConstructionHeuristics.TerrainAdaptationPreset.FLATTEN));
        assertEquals(1.35f, RoadConstructionHeuristics.cutToFillBalanceRatio(
            RoadConstructionHeuristics.TerrainAdaptationPreset.FLATTEN), 0.01f);
    }
}
