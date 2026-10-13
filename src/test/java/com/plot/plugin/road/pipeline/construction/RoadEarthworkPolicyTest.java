package com.plot.plugin.road.pipeline.construction;

import com.plot.plugin.road.RoadConstructionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadEarthworkPolicyTest {

    @Test
    void mapsRoadCutFillToNormalBatter() {
        assertEquals(RoadEarthworkPolicy.NORMAL_BATTER, RoadEarthworkPolicy.of(RoadConstructionType.ROAD));
        assertEquals(RoadEarthworkPolicy.NORMAL_BATTER, RoadEarthworkPolicy.of(RoadConstructionType.CUT));
        assertEquals(RoadEarthworkPolicy.NORMAL_BATTER, RoadEarthworkPolicy.of(RoadConstructionType.FILL));
        assertTrue(RoadEarthworkPolicy.of(RoadConstructionType.ROAD).allowsCorridorBatter());
        assertTrue(RoadEarthworkPolicy.of(RoadConstructionType.CUT).allowsOpenSubgradeFill());
    }

    @Test
    void mapsBridgeAndTunnelInteriorToStructureOnly() {
        assertEquals(RoadEarthworkPolicy.STRUCTURE_ONLY, RoadEarthworkPolicy.of(RoadConstructionType.BRIDGE));
        assertEquals(RoadEarthworkPolicy.STRUCTURE_ONLY, RoadEarthworkPolicy.of(RoadConstructionType.TUNNEL));
        assertFalse(RoadEarthworkPolicy.of(RoadConstructionType.BRIDGE).allowsCorridorBatter());
        assertFalse(RoadEarthworkPolicy.of(RoadConstructionType.TUNNEL).allowsOpenSubgradeFill());
    }

    @Test
    void mapsPortalAndAbutmentToTransitionBatter() {
        assertEquals(RoadEarthworkPolicy.TRANSITION_BATTER, RoadEarthworkPolicy.of(RoadConstructionType.BRIDGE_ABUTMENT));
        assertEquals(RoadEarthworkPolicy.TRANSITION_BATTER, RoadEarthworkPolicy.of(RoadConstructionType.TUNNEL_PORTAL));
        assertTrue(RoadEarthworkPolicy.of(RoadConstructionType.TUNNEL_PORTAL).allowsCorridorBatter());
    }

    @Test
    void nullTypeDefaultsToNormalBatter() {
        assertEquals(RoadEarthworkPolicy.NORMAL_BATTER, RoadEarthworkPolicy.of(null));
    }
}
