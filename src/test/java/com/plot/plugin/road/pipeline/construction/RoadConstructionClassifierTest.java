package com.plot.plugin.road.pipeline.construction;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadConstructionClassifierTest {

    @Test
    void sustainedCliffUsesOnlyMountainSideShelterAndOrdinaryBanksRemainOpen() {
        var segment = new PathSegment(new Vec2d(0, 0), new Vec2d(30, 0));
        var info = new SegmentHeightInfo(segment, 90, 90, 64, 64, 0);
        var columns = new com.plot.plugin.road.RoadTerrainClearanceUtils.BlockColumnResolver() {
            public int worldX(Vec2d p) { return (int) Math.round(p.x); }
            public int worldZ(Vec2d p) { return (int) Math.round(p.y); }
        };
        for (boolean left : List.of(true, false)) {
            TerrainSampler cliff = new TerrainSampler() {
                public int sampleSurfaceY(Vec2d p) { return (left ? p.y > 0 : p.y < 0) ? 120 : 60; }
                public boolean isSolidBlock(int x, int y, int z) { return y <= sampleSurfaceY(new Vec2d(x, z)); }
            };
            assertEquals(left ? RoadConstructionType.ROCK_SHELTER_LEFT : RoadConstructionType.ROCK_SHELTER_RIGHT,
                RoadConstructionClassifier.cliffShelterType(info, cliff, 7,
                    com.plot.plugin.road.tunnel.ResolvedTunnelStyle.defaults(), columns, 1));
        }
        assertEquals(null, RoadConstructionClassifier.cliffShelterType(info, new FlatTerrainSampler(70), 7,
            com.plot.plugin.road.tunnel.ResolvedTunnelStyle.defaults(), columns, 1));
        assertEquals(null, RoadConstructionClassifier.cliffShelterType(info, new FlatTerrainSampler(120), 7,
            com.plot.plugin.road.tunnel.ResolvedTunnelStyle.defaults(), columns, 1));
    }

    @Test
    void halfCliffWithCavityStillClassifiesAsRockShelter() {
        var segment = new PathSegment(new Vec2d(0, 0), new Vec2d(30, 0));
        var info = new SegmentHeightInfo(segment, 90, 90, 64, 64, 0);
        var columns = new com.plot.plugin.road.RoadTerrainClearanceUtils.BlockColumnResolver() {
            public int worldX(Vec2d p) { return (int) Math.round(p.x); }
            public int worldZ(Vec2d p) { return (int) Math.round(p.y); }
        };
        TerrainSampler cliffCavity = new TerrainSampler() {
            public int sampleSurfaceY(Vec2d p) { return p.y > 0 ? 120 : 60; }
            public boolean isSolidBlock(int x, int y, int z) { return false; }
        };
        assertEquals(RoadConstructionType.ROCK_SHELTER_LEFT,
            RoadConstructionClassifier.cliffShelterType(info, cliffCavity, 7,
                com.plot.plugin.road.tunnel.ResolvedTunnelStyle.defaults(), columns, 1));
    }

    @Test
    void openTrenchCenterlineHasNoRoofOverburden() {
        var segment = new PathSegment(new Vec2d(0, 0), new Vec2d(20, 0));
        var info = new SegmentHeightInfo(segment, 80, 80, 64, 64, 0);
        TerrainSampler openTrench = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d p) {
                // Daylight at centerline; high banks only off to the sides
                return Math.abs(p.y) <= 2 ? 64 : 80;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= sampleSurfaceY(new Vec2d(x, z));
            }
        };
        assertFalse(
            RoadConstructionClassifier.hasRoofOverburden(
                info, openTrench, com.plot.plugin.road.tunnel.ResolvedTunnelStyle.defaults()),
            "open-cut centerline with sky above must not count as tunnel roof cover");
    }

    @Test
    void roofOverburdenRejectsDiscontinuousFloatingLedge() {
        var segment = new PathSegment(new Vec2d(0, 0), new Vec2d(20, 0));
        var info = new SegmentHeightInfo(segment, 90, 90, 64, 64, 0);
        var style = com.plot.plugin.road.tunnel.ResolvedTunnelStyle.defaults();
        int roofY = 64 + style.roofTopOffset();
        TerrainSampler caveWithFloatingLedge = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d p) {
                return roofY + 8; // high surface reading alone must not prove cover
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                if (y <= 64) {
                    return true;
                }
                // Gap above roof, then an independent floating ledge
                return y >= roofY + 4 && y <= roofY + 8;
            }
        };
        assertFalse(
            RoadConstructionClassifier.hasRoofOverburden(info, caveWithFloatingLedge, style),
            "discontinuous rock above a void must not count as tunnel overburden");
    }

    @Test
    void emergingSlopeSegmentDoesNotStayTunnelFromMidpointAlone() {
        RoadSystemConfig config = new RoadSystemConfig("emerge");
        config.setTerrainStyle(com.plot.plugin.road.terrain.RoadTerrainStyle.BALANCED);
        // Long deep cut so evaluator initially picks TUNNEL
        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(40, 0));
        SegmentHeightInfo info = new SegmentHeightInfo(segment, 90, 90, 64, 64, 0.0);
        var columns = new com.plot.plugin.road.RoadTerrainClearanceUtils.BlockColumnResolver() {
            public int worldX(Vec2d p) { return (int) Math.round(p.x); }
            public int worldZ(Vec2d p) { return (int) Math.round(p.y); }
        };
        // Buried for x < 20, daylight for x >= 20 — midpoint (x=20) is transitional
        TerrainSampler emerging = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d p) {
                return p.x < 20 ? 90 : 64;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= sampleSurfaceY(new Vec2d(x, z));
            }
        };
        ConstructionDetection detection = RoadConstructionClassifier.classify(
            List.of(segment),
            List.of(info),
            emerging,
            config,
            null,
            p -> new BlockPos((int) Math.round(p.x), 64, (int) Math.round(p.y)),
            7,
            com.plot.plugin.road.tunnel.ResolvedTunnelStyle.defaults(),
            columns);
        assertFalse(detection.constructionTypes().getFirst().isTunnelFamily(),
            "segment that exits the mountain must not remain TUNNEL from midpoint cover alone");
    }

    @Test
    void midpointCoverWithOpenEndsDemotesTunnel() {
        RoadSystemConfig config = new RoadSystemConfig("open-ends");
        config.setTerrainStyle(com.plot.plugin.road.terrain.RoadTerrainStyle.BALANCED);
        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(40, 0));
        SegmentHeightInfo info = new SegmentHeightInfo(segment, 90, 90, 64, 64, 0.0);
        var columns = new com.plot.plugin.road.RoadTerrainClearanceUtils.BlockColumnResolver() {
            public int worldX(Vec2d p) { return (int) Math.round(p.x); }
            public int worldZ(Vec2d p) { return (int) Math.round(p.y); }
        };
        TerrainSampler midOnly = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d p) {
                return Math.abs(p.x - 20) <= 4 ? 90 : 64;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= sampleSurfaceY(new Vec2d(x, z));
            }
        };
        ConstructionDetection detection = RoadConstructionClassifier.classify(
            List.of(segment),
            List.of(info),
            midOnly,
            config,
            null,
            p -> new BlockPos((int) Math.round(p.x), 64, (int) Math.round(p.y)),
            7,
            com.plot.plugin.road.tunnel.ResolvedTunnelStyle.defaults(),
            columns);
        assertFalse(detection.constructionTypes().getFirst().isTunnelFamily(),
            "cover only near midpoint must not keep the whole segment as TUNNEL");
    }

    @Test
    void classifyMarksBridgeWhenTargetIsHighAboveGround() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setTerrainStyle(com.plot.plugin.road.terrain.RoadTerrainStyle.SMOOTH);

        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(10, 0));
        SegmentHeightInfo heightInfo = new SegmentHeightInfo(segment, 64, 64, 70, 70, 0.0);
        TerrainSampler terrain = new FlatTerrainSampler(64);

        ConstructionDetection detection = RoadConstructionClassifier.classify(
            List.of(segment),
            List.of(heightInfo),
            terrain,
            config,
            canvas -> BlockPos.ORIGIN);

        assertEquals(RoadConstructionType.BRIDGE_ABUTMENT, detection.constructionTypes().getFirst());
        assertEquals(1, detection.bridges().size());
        assertTrue(detection.tunnels().isEmpty());
        assertEquals(1, detection.runCount(RoadConstructionType.BRIDGE));
        assertEquals(0.0, detection.runs().getFirst().startStation(), 1e-6);
        assertEquals(10.0, detection.runs().getFirst().endStation(), 1e-6);
    }

    @Test
    void structureRunFlagsMarkOnlyPortalSegments() {
        List<RoadConstructionType> types = List.of(
            RoadConstructionType.CUT,
            RoadConstructionType.TUNNEL,
            RoadConstructionType.TUNNEL,
            RoadConstructionType.TUNNEL,
            RoadConstructionType.FILL);

        assertFalse(RoadConstructionClassifier.isStructureRunStart(types, 0));
        assertTrue(RoadConstructionClassifier.isStructureRunStart(types, 1));
        assertFalse(RoadConstructionClassifier.isStructureRunStart(types, 2));
        assertFalse(RoadConstructionClassifier.isStructureRunEnd(types, 2));
        assertTrue(RoadConstructionClassifier.isStructureRunEnd(types, 3));
        assertFalse(RoadConstructionClassifier.isStructureRunEnd(types, 4));
    }

    @Test
    void shortOpenGapBetweenTunnelsIsMergedIntoTunnel() {
        List<RoadConstructionType> types = new java.util.ArrayList<>(List.of(
            RoadConstructionType.TUNNEL,
            RoadConstructionType.TUNNEL,
            RoadConstructionType.CUT,
            RoadConstructionType.ROAD,
            RoadConstructionType.TUNNEL,
            RoadConstructionType.TUNNEL));
        // 两段隧道之间 2+2=4 格开敞，应并入隧道
        List<Double> distances = List.of(8.0, 8.0, 2.0, 2.0, 8.0, 8.0);
        RoadConstructionClassifier.mergeShortTunnelGaps(types, distances, 1.0);
        assertEquals(RoadConstructionType.TUNNEL, types.get(2));
        assertEquals(RoadConstructionType.TUNNEL, types.get(3));
    }

    @Test
    void longOpenGapBetweenTunnelsIsNotMerged() {
        List<RoadConstructionType> types = new java.util.ArrayList<>(List.of(
            RoadConstructionType.TUNNEL,
            RoadConstructionType.CUT,
            RoadConstructionType.CUT,
            RoadConstructionType.CUT,
            RoadConstructionType.TUNNEL));
        List<Double> distances = List.of(10.0, 3.0, 3.0, 3.0, 10.0); // gap=9 > 5
        RoadConstructionClassifier.mergeShortTunnelGaps(types, distances, 1.0);
        assertEquals(RoadConstructionType.CUT, types.get(1));
        assertEquals(RoadConstructionType.CUT, types.get(2));
        assertEquals(RoadConstructionType.CUT, types.get(3));
    }

    @Test
    void bridgeInsideTunnelGapIsNotConvertedToTunnel() {
        List<RoadConstructionType> types = new java.util.ArrayList<>(List.of(
            RoadConstructionType.TUNNEL,
            RoadConstructionType.BRIDGE,
            RoadConstructionType.TUNNEL));
        List<Double> distances = List.of(10.0, 4.0, 10.0);
        RoadConstructionClassifier.mergeShortTunnelGaps(types, distances, 1.0);
        assertEquals(RoadConstructionType.BRIDGE, types.get(1));
    }

    @Test
    void markPortalAndAbutmentRewritesStructureRunEnds() {
        List<RoadConstructionType> types = new java.util.ArrayList<>(List.of(
            RoadConstructionType.CUT,
            RoadConstructionType.TUNNEL,
            RoadConstructionType.TUNNEL,
            RoadConstructionType.TUNNEL,
            RoadConstructionType.BRIDGE,
            RoadConstructionType.BRIDGE,
            RoadConstructionType.FILL));

        RoadConstructionClassifier.markPortalAndAbutmentSegments(types);

        assertEquals(RoadConstructionType.CUT, types.get(0));
        assertEquals(RoadConstructionType.TUNNEL_PORTAL, types.get(1));
        assertEquals(RoadConstructionType.TUNNEL, types.get(2));
        assertEquals(RoadConstructionType.TUNNEL_PORTAL, types.get(3));
        assertEquals(RoadConstructionType.BRIDGE_ABUTMENT, types.get(4));
        assertEquals(RoadConstructionType.BRIDGE_ABUTMENT, types.get(5));
        assertEquals(RoadConstructionType.FILL, types.get(6));
        assertTrue(RoadConstructionClassifier.isStructureRunStart(types, 1));
        assertFalse(RoadConstructionClassifier.isStructureRunStart(types, 2));
        assertTrue(RoadConstructionClassifier.isStructureRunEnd(types, 3));
    }
}
