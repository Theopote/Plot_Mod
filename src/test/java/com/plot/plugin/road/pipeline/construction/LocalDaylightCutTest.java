package com.plot.plugin.road.pipeline.construction;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDaylightCutTest {

    @Test
    void shortShelterIslandBetweenOpenRoadBecomesCut() {
        List<RoadConstructionType> types = new ArrayList<>(List.of(
            RoadConstructionType.ROAD,
            RoadConstructionType.ROCK_SHELTER_LEFT,
            RoadConstructionType.ROAD));
        List<Double> distances = List.of(10.0, 3.0, 10.0);
        List<SegmentHeightInfo> heights = List.of(
            height(0, 10, 64, 80),
            height(10, 13, 64, 80),
            height(13, 23, 64, 80));
        LocalDaylightCut.apply(
            types, distances, heights, hill(80), 7,
            ResolvedTunnelStyle.defaults(), resolver(), 1.0);
        assertEquals(RoadConstructionType.CUT, types.get(1),
            "3-block rock-shelter island flanked by open road must daylight-cut");
    }

    @Test
    void longCliffShelterIsKept() {
        List<RoadConstructionType> types = new ArrayList<>(List.of(
            RoadConstructionType.ROAD,
            RoadConstructionType.ROCK_SHELTER_LEFT,
            RoadConstructionType.ROAD));
        List<Double> distances = List.of(10.0, 8.0, 10.0);
        List<SegmentHeightInfo> heights = List.of(
            height(0, 10, 64, 90),
            height(10, 18, 64, 90),
            height(18, 28, 64, 90));
        LocalDaylightCut.apply(
            types, distances, heights, hill(90), 7,
            ResolvedTunnelStyle.defaults(), resolver(), 1.0);
        assertEquals(RoadConstructionType.ROCK_SHELTER_LEFT, types.get(1),
            "8-block continuous cliff must keep shelter rules");
    }

    @Test
    void shortGapBetweenRealTunnelsIsNotDaylightedHere() {
        // mergeShortTunnelGaps already absorbs such gaps; if a short TUNNEL island
        // remains between TUNNEL flanks, daylight must refuse (not open-flanked).
        List<RoadConstructionType> types = new ArrayList<>(List.of(
            RoadConstructionType.TUNNEL,
            RoadConstructionType.ROCK_SHELTER_LEFT,
            RoadConstructionType.TUNNEL));
        List<Double> distances = List.of(20.0, 2.0, 20.0);
        List<SegmentHeightInfo> heights = List.of(
            height(0, 20, 64, 90),
            height(20, 22, 64, 90),
            height(22, 42, 64, 90));
        LocalDaylightCut.apply(
            types, distances, heights, hill(90), 7,
            ResolvedTunnelStyle.defaults(), resolver(), 1.0);
        assertEquals(RoadConstructionType.ROCK_SHELTER_LEFT, types.get(1));
    }

    @Test
    void excavationOverBudgetRefusesDaylight() {
        List<RoadConstructionType> types = new ArrayList<>(List.of(
            RoadConstructionType.CUT,
            RoadConstructionType.ROCK_SHELTER_RIGHT,
            RoadConstructionType.CUT));
        List<Double> distances = List.of(8.0, 3.0, 8.0);
        List<SegmentHeightInfo> heights = List.of(
            height(0, 8, 64, 120),
            height(8, 11, 64, 120),
            height(11, 19, 64, 120));
        // Huge solid mountain → over MAX_EXCAVATION_BLOCKS
        LocalDaylightCut.apply(
            types, distances, heights, hill(200), 15,
            ResolvedTunnelStyle.defaults(), resolver(), 1.0);
        assertEquals(RoadConstructionType.ROCK_SHELTER_RIGHT, types.get(1),
            "oversized cut volume must keep shelter");
    }

    @Test
    void trueTunnelIslandsAreNeverDaylightCandidates() {
        assertFalse(LocalDaylightCut.isDaylightCandidate(RoadConstructionType.TUNNEL));
        assertFalse(LocalDaylightCut.isDaylightCandidate(RoadConstructionType.TUNNEL_PORTAL));
    }

    @Test
    void flankedByOpenHelpers() {
        assertTrue(LocalDaylightCut.flankedByOpenDaylight(
            List.of(RoadConstructionType.ROAD, RoadConstructionType.TUNNEL, RoadConstructionType.CUT),
            1, 2));
        assertFalse(LocalDaylightCut.flankedByOpenDaylight(
            List.of(RoadConstructionType.TUNNEL, RoadConstructionType.TUNNEL, RoadConstructionType.TUNNEL),
            1, 2));
    }

    private static SegmentHeightInfo height(double x0, double x1, int targetY, int groundY) {
        PathSegment segment = new PathSegment(new Vec2d(x0, 0), new Vec2d(x1, 0));
        return new SegmentHeightInfo(segment, groundY, groundY, targetY, targetY, 0.0);
    }

    private static TerrainSampler hill(int topY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return topY;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return topY;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y <= topY;
            }

            @Override
            public boolean isConstructionVolumeObstruction(int worldX, int y, int worldZ) {
                return y <= topY;
            }
        };
    }

    private static RoadTerrainClearanceUtils.BlockColumnResolver resolver() {
        return new RoadTerrainClearanceUtils.BlockColumnResolver() {
            @Override
            public int worldX(Vec2d planPoint) {
                return (int) Math.round(planPoint.x);
            }

            @Override
            public int worldZ(Vec2d planPoint) {
                return (int) Math.round(planPoint.y);
            }
        };
    }
}