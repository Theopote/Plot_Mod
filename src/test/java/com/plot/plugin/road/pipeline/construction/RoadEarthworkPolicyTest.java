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
        assertTrue(RoadEarthworkPolicy.allowsCorridorBatter(RoadConstructionType.ROAD));
        assertTrue(RoadEarthworkPolicy.of(RoadConstructionType.CUT).allowsOpenSubgradeFill());
    }

    @Test
    void mapsBridgeAndTunnelInteriorToStructureOnly() {
        assertEquals(RoadEarthworkPolicy.STRUCTURE_ONLY, RoadEarthworkPolicy.of(RoadConstructionType.BRIDGE));
        assertEquals(RoadEarthworkPolicy.STRUCTURE_ONLY, RoadEarthworkPolicy.of(RoadConstructionType.TUNNEL));
        assertFalse(RoadEarthworkPolicy.allowsCorridorBatter(RoadConstructionType.BRIDGE));
        assertFalse(RoadEarthworkPolicy.of(RoadConstructionType.TUNNEL).allowsOpenSubgradeFill());
        assertFalse(RoadEarthworkPolicy.of(RoadConstructionType.BRIDGE).allowsOpenCorridorClearance());
    }

    @Test
    void tunnelFamilyNeverEmitsCorridorBatterButPortalKeepsClearance() {
        assertEquals(RoadEarthworkPolicy.TRANSITION_BATTER, RoadEarthworkPolicy.of(RoadConstructionType.TUNNEL_PORTAL));
        assertTrue(RoadEarthworkPolicy.of(RoadConstructionType.TUNNEL_PORTAL).allowsOpenCorridorClearance());
        assertFalse(RoadEarthworkPolicy.allowsCorridorBatter(RoadConstructionType.TUNNEL));
        assertFalse(RoadEarthworkPolicy.allowsCorridorBatter(RoadConstructionType.TUNNEL_PORTAL));
        assertFalse(RoadEarthworkPolicy.allowsCorridorBatter(RoadConstructionType.ROCK_SHELTER_LEFT));
        assertFalse(RoadEarthworkPolicy.allowsCorridorBatter(RoadConstructionType.ROCK_SHELTER_RIGHT));
    }

    @Test
    void mapsBridgeAbutmentToTransitionBatter() {
        assertEquals(RoadEarthworkPolicy.TRANSITION_BATTER, RoadEarthworkPolicy.of(RoadConstructionType.BRIDGE_ABUTMENT));
        assertTrue(RoadEarthworkPolicy.allowsCorridorBatter(RoadConstructionType.BRIDGE_ABUTMENT));
    }

    @Test
    void nullTypeDefaultsToNormalBatter() {
        assertEquals(RoadEarthworkPolicy.NORMAL_BATTER, RoadEarthworkPolicy.of(null));
        assertTrue(RoadEarthworkPolicy.allowsCorridorBatter(null));
    }

    @Test
    void suppressOpenDaylightBatterForNearTunnelDeepCoverAndTunnelTypes() {
        assertTrue(RoadEarthworkPolicy.suppressOpenDaylightBatter(
            RoadConstructionType.ROAD, true, 0, 4));
        assertTrue(RoadEarthworkPolicy.suppressOpenDaylightBatter(
            RoadConstructionType.TUNNEL, false, 0, 4));
        assertTrue(RoadEarthworkPolicy.suppressOpenDaylightBatter(
            RoadConstructionType.CUT, false, 4, 4));
        assertTrue(RoadEarthworkPolicy.suppressOpenDaylightBatter(
            RoadConstructionType.ROAD, false, 8, 4));
        assertFalse(RoadEarthworkPolicy.suppressOpenDaylightBatter(
            RoadConstructionType.ROAD, false, 2, 4));
    }

    @Test
    void suppressShoulderCutBatterOnlyForDeepCutEdges() {
        assertTrue(RoadEarthworkPolicy.suppressShoulderCutBatter(false, 4, 4));
        assertTrue(RoadEarthworkPolicy.suppressShoulderCutBatter(false, 10, 4));
        assertFalse(RoadEarthworkPolicy.suppressShoulderCutBatter(true, 10, 4));
        assertFalse(RoadEarthworkPolicy.suppressShoulderCutBatter(false, 2, 4));
        assertFalse(RoadEarthworkPolicy.suppressShoulderCutBatter(false, 10, 0));
    }
}
