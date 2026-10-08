package com.plot.plugin.road.tunnel;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.construction.ConstructionRun;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelPortalPlannerTest {

    private static final int ROAD_Y = 64;

    @Test
    void findsEntryPortalInsideCoveredTerrain() {
        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(40, 0));
        ResolvedTunnelStyle style = ResolvedTunnelStyle.defaults();
        int roofY = ROAD_Y + style.roofTopOffset();
        // Thin cover near entry (x<4); thick cover deeper in the mountain.
        TerrainSampler terrain = coverFromX(4, roofY + 5, roofY);

        List<TunnelPortalPlanner.PortalStation> portals = plan(segment, style, terrain);
        TunnelPortalPlanner.PortalStation entry = portals.stream()
            .filter(TunnelPortalPlanner.PortalStation::entry)
            .findFirst()
            .orElse(null);

        assertNotNull(entry);
        assertTrue(entry.worldStation() >= 4.0 - 1e-6);
        assertTrue(entry.worldStation() <= 20.0 + 1e-6);
    }

    @Test
    void findsExitPortalInsideCoveredTerrain() {
        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(40, 0));
        ResolvedTunnelStyle style = ResolvedTunnelStyle.defaults();
        int roofY = ROAD_Y + style.roofTopOffset();
        // Thin cover near exit (x>36); thick cover inward.
        TerrainSampler terrain = coverUntilX(36, roofY + 5, roofY);

        List<TunnelPortalPlanner.PortalStation> portals = plan(segment, style, terrain);
        TunnelPortalPlanner.PortalStation exit = portals.stream()
            .filter(p -> !p.entry())
            .findFirst()
            .orElse(null);

        assertNotNull(exit);
        assertTrue(exit.worldStation() <= 36.0 + 1e-6);
        assertTrue(exit.worldStation() >= 20.0 - 1e-6);
    }

    @Test
    void noFeasiblePortalReturnsEmpty() {
        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(40, 0));
        ResolvedTunnelStyle style = ResolvedTunnelStyle.defaults();
        int roofY = ROAD_Y + style.roofTopOffset();
        TerrainSampler thinEverywhere = solidUpTo(roofY); // zero cover above roof

        List<TunnelPortalPlanner.PortalStation> portals = plan(segment, style, thinEverywhere);
        assertTrue(portals.isEmpty());
    }

    @Test
    void portalSearchDoesNotLeaveTunnelRun() {
        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(40, 0));
        ResolvedTunnelStyle style = ResolvedTunnelStyle.defaults();
        int roofY = ROAD_Y + style.roofTopOffset();
        TerrainSampler thick = solidUpTo(roofY + 8);

        List<TunnelPortalPlanner.PortalStation> portals = plan(segment, style, thick);
        assertFalse(portals.isEmpty());
        for (TunnelPortalPlanner.PortalStation portal : portals) {
            assertTrue(portal.worldStation() >= -1e-6);
            assertTrue(portal.worldStation() <= 40.0 + 1e-6);
        }
    }

    @Test
    void findPortalStationReturnsNullWhenSearchBudgetExhaustedInsideThinCover() {
        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(40, 0));
        ResolvedTunnelStyle style = ResolvedTunnelStyle.defaults();
        int roofY = ROAD_Y + style.roofTopOffset();
        // Cover only past half-run search budget (maxSteps = 20 for length 40).
        TerrainSampler terrain = coverFromX(25, roofY + 5, roofY);

        TunnelPortalPlanner.PortalStation entry = TunnelPortalPlanner.findPortalStation(
            0.0, 40.0, true, 40.0,
            List.of(segment),
            List.of(new SegmentHeightInfo(segment, 80, 80, ROAD_Y, ROAD_Y, 0.0)),
            List.of(RoadConstructionType.TUNNEL),
            CrossSectionBuildContext.fixed(ResolvedCrossSection.fromConfig(new RoadSystemConfig("portal"))),
            terrain,
            1.0,
            style,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            List.of(),
            columnResolver(),
            (c, y) -> y);

        assertEquals(null, entry);
    }

    private static List<TunnelPortalPlanner.PortalStation> plan(
            PathSegment segment,
            ResolvedTunnelStyle style,
            TerrainSampler terrain) {
        ConstructionRun run = new ConstructionRun(
            RoadConstructionType.TUNNEL, 0, 1, 0.0, 40.0, 16, 16.0);
        return TunnelPortalPlanner.planPortals(
            List.of(run),
            List.of(segment),
            List.of(new SegmentHeightInfo(segment, 80, 80, ROAD_Y, ROAD_Y, 0.0)),
            List.of(RoadConstructionType.TUNNEL),
            CrossSectionBuildContext.fixed(ResolvedCrossSection.fromConfig(new RoadSystemConfig("portal"))),
            terrain,
            1.0,
            style,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            List.of(),
            columnResolver(),
            (c, y) -> y);
    }

    /** Solid up to maxSolidY for x >= fromX; elsewhere only up to thinTopY. */
    private static TerrainSampler coverFromX(double fromX, int thickTopY, int thinTopY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return top(point);
            }

            @Override
            public int sampleColumnTopY(Vec2d point) {
                return top(point);
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                int top = x >= fromX - 1e-9 ? thickTopY : thinTopY;
                return y <= top;
            }

            private int top(Vec2d point) {
                return point.x >= fromX - 1e-9 ? thickTopY : thinTopY;
            }
        };
    }

    /** Solid thick for x <= untilX; thin beyond. */
    private static TerrainSampler coverUntilX(double untilX, int thickTopY, int thinTopY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return top(point);
            }

            @Override
            public int sampleColumnTopY(Vec2d point) {
                return top(point);
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                int top = x <= untilX + 1e-9 ? thickTopY : thinTopY;
                return y <= top;
            }

            private int top(Vec2d point) {
                return point.x <= untilX + 1e-9 ? thickTopY : thinTopY;
            }
        };
    }

    private static TerrainSampler solidUpTo(int maxSolidY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return maxSolidY;
            }

            @Override
            public int sampleColumnTopY(Vec2d point) {
                return maxSolidY;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= maxSolidY;
            }
        };
    }

    private static RoadTerrainClearanceUtils.BlockColumnResolver columnResolver() {
        return new RoadTerrainClearanceUtils.BlockColumnResolver() {
            @Override
            public int worldX(Vec2d point) {
                return (int) Math.round(point.x);
            }

            @Override
            public int worldZ(Vec2d point) {
                return (int) Math.round(point.y);
            }
        };
    }
}
