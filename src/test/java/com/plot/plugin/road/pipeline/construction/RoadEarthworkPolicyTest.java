package com.plot.plugin.road.pipeline.construction;

import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
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
    void deepDaylightCoverUsesStructureHeightPlusTunnelThreshold() {
        // 默认净高5+衬砌1 + BALANCED阈值4 = 10
        assertEquals(10, RoadEarthworkPolicy.deepDaylightCoverBlocks(4));
        assertEquals(14, RoadEarthworkPolicy.deepDaylightCoverBlocks(8));
        // tunnelThreshold<=0 时仍保留最小盖层 1：structure 6 + 1 = 7
        assertEquals(7, RoadEarthworkPolicy.deepDaylightCoverBlocks(0));
        assertEquals(
            6 + 4,
            RoadEarthworkPolicy.deepDaylightCoverBlocks(ResolvedTunnelStyle.defaults(), 4));
    }

    @Test
    void suppressOpenDaylightBatterForNearTunnelDeepCoverAndTunnelTypes() {
        assertTrue(RoadEarthworkPolicy.suppressOpenDaylightBatter(
            RoadConstructionType.ROAD, true, 0, 4));
        assertTrue(RoadEarthworkPolicy.suppressOpenDaylightBatter(
            RoadConstructionType.TUNNEL, false, 0, 4));
        // 普通切方：低于 净高+衬砌+阈值，仍应放坡
        assertFalse(RoadEarthworkPolicy.suppressOpenDaylightBatter(
            RoadConstructionType.CUT, false, 4, 4));
        assertFalse(RoadEarthworkPolicy.suppressOpenDaylightBatter(
            RoadConstructionType.ROAD, false, 8, 4));
        assertFalse(RoadEarthworkPolicy.suppressOpenDaylightBatter(
            RoadConstructionType.ROAD, false, 9, 4));
        // 达到结构高度+盖层才禁开敞削山
        assertTrue(RoadEarthworkPolicy.suppressOpenDaylightBatter(
            RoadConstructionType.ROAD, false, 10, 4));
        assertTrue(RoadEarthworkPolicy.suppressOpenDaylightBatter(
            RoadConstructionType.CUT, false, 32, 4));
    }

    @Test
    void suppressShoulderCutBatterOnlyForStructureDepthMountainEdges() {
        assertFalse(RoadEarthworkPolicy.suppressShoulderCutBatter(false, 4, 4));
        assertFalse(RoadEarthworkPolicy.suppressShoulderCutBatter(false, 9, 4));
        assertTrue(RoadEarthworkPolicy.suppressShoulderCutBatter(false, 10, 4));
        assertTrue(RoadEarthworkPolicy.suppressShoulderCutBatter(false, 14, 8));
        assertFalse(RoadEarthworkPolicy.suppressShoulderCutBatter(true, 24, 4));
        // threshold 0 → 门槛 = structure(6)+1 = 7
        assertFalse(RoadEarthworkPolicy.suppressShoulderCutBatter(false, 6, 0));
        assertTrue(RoadEarthworkPolicy.suppressShoulderCutBatter(false, 7, 0));
    }

    @Test
    void suppressFillBatterWhenClearanceExceedsBridgeThreshold() {
        assertFalse(RoadEarthworkPolicy.suppressFillBatter(true, 3, 3));
        assertTrue(RoadEarthworkPolicy.suppressFillBatter(true, 4, 3));
        assertFalse(RoadEarthworkPolicy.suppressFillBatter(false, 10, 3));
        assertFalse(RoadEarthworkPolicy.suppressFillBatter(true, 10, 0));
        assertTrue(RoadEarthworkPolicy.allowsOpenFillHeight(67, 64, 3));
        assertFalse(RoadEarthworkPolicy.allowsOpenFillHeight(68, 64, 3));
    }
}
