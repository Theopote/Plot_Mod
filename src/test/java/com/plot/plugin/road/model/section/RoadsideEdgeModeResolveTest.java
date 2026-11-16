package com.plot.plugin.road.model.section;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.style.RoadStyleCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadsideEdgeModeResolveTest {

    @Test
    void legacyEnabledMigratesToSlopeOrNone() {
        SlopeBatter batter = new SlopeBatter();
        batter.setEnabled(true);
        assertEquals(RoadsideEdgeMode.SLOPE_BATTER, batter.getMode());
        batter.setEnabled(false);
        assertEquals(RoadsideEdgeMode.NONE, batter.getMode());
    }

    @Test
    void retainingWallModeResolvesWithoutSlopeRatios() {
        RoadCrossSection section = RoadStyleCatalog.cityStreet().toCrossSection();
        RoadSystemConfig defaults = new RoadSystemConfig("road_system");
        defaults.setRoadsideEdgeMode(RoadsideEdgeMode.NONE);
        ResolvedCrossSection resolved = ResolvedCrossSection.resolve(section, defaults);
        assertTrue(resolved.includeRetainingWall);
        assertFalse(resolved.includeSlopeBatter);
        assertTrue(resolved.engagesRoadsideEarthwork());
    }

    @Test
    void networkRoundTripPreservesRetainingWallMode() {
        SlopeBatter batter = new SlopeBatter();
        batter.setMode(RoadsideEdgeMode.RETAINING_WALL);
        batter.setWallMaterial("minecraft:cobblestone");
        assertEquals(RoadsideEdgeMode.RETAINING_WALL, batter.getMode());
        assertEquals("minecraft:cobblestone", batter.getWallMaterial());
    }
}
