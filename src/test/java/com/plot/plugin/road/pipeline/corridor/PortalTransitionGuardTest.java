package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalTransitionGuardTest {

    private static final int ROAD_Y = 64;

    @Test
    void exposedSlopesOnBothSidesClearAboveRoofWhileCrownRemainsProtected() {
        for (Vec2d forward : java.util.List.of(new Vec2d(1, 0), new Vec2d(0, 1), new Vec2d(1, 1).normalize())) {
            var normal = new Vec2d(-forward.y, forward.x);
            var center = new Vec2d(100, 200);
            var plan = new RoadCorridorPlan();
            plan.putClearance(new ColumnKey(100, 200), new ClearanceCell(center, ROAD_Y, ROAD_Y + 8,
                RoadCorridorConstructionMode.PORTAL, RoadConstructionType.TUNNEL_PORTAL));
            plan.registerPortalBackfillSite(new PortalBackfillSite(center, normal, forward, ROAD_Y, 7, 1));
            var guard = PortalTransitionGuard.from(plan);
            var host = CorridorTestFixtures.planHost(new RoadSystemConfig("portal-batter"));
            int roof = RoadCorridorPlanBuilder.portalRoofTopY(host, ROAD_Y);
            assertEquals(roof, guard.capCutTopY(center, roof + 4, host, 4));
            for (int side : new int[]{-1, 1}) {
                Vec2d exposed = center.add(normal.multiply(side * 10));
                assertEquals(roof + 4, guard.capCutTopY(exposed, roof + 4, host, 4));
                assertEquals(roof + 4, guard.capClearTopY(exposed, roof + 4, host, 4));
                // Keep the designed slope face; only its overlying terrain is removed.
                assertEquals(roof - 1, guard.capSlopeSurfaceY(exposed, roof + 4, host, 8));
                var key = new ColumnKey(host.worldX(exposed), host.worldZ(exposed));
                plan.slopeCellOrCreate(key, exposed).mergeCut(roof + 2, "minecraft:stone");
                var solids = new com.plot.plugin.road.solid.RoadSolidModel();
                RoadCorridorSolidEmitter.emit(solids, plan, new com.plot.core.terrain.FlatTerrainSampler(roof + 4),
                    host, null, "minecraft:stone");
                assertTrue(solids.primitives().stream().anyMatch(p ->
                    p.planPoint().subtract(exposed).lengthSquared() < 1e-12 && p.elevation() == roof
                        && "minecraft:air".equals(p.materialId())), "terrain lid at slope crown must be cleared");
                assertTrue(solids.primitives().stream().anyMatch(p ->
                    p.planPoint().subtract(exposed).lengthSquared() < 1e-12 && p.elevation() == roof + 4
                        && "minecraft:air".equals(p.materialId())), "clear the whole exposed terrain column");
            }
        }
    }

    @Test
    void findsPortalByWorldZForNorthSouthAlignment() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        // N–S portal mouth around x=50, z=10..12 — old worldX-only index would miss z-neighbors.
        plan.putClearance(
            new ColumnKey(50, 10),
            new ClearanceCell(
                new Vec2d(50, 10),
                ROAD_Y,
                ROAD_Y + 8,
                RoadCorridorConstructionMode.PORTAL,
                RoadConstructionType.TUNNEL_PORTAL));
        plan.putClearance(
            new ColumnKey(50, 11),
            new ClearanceCell(
                new Vec2d(50, 11),
                ROAD_Y,
                ROAD_Y + 8,
                RoadCorridorConstructionMode.PORTAL,
                RoadConstructionType.TUNNEL_PORTAL));

        PortalTransitionGuard guard = PortalTransitionGuard.from(plan);
        Integer designY = guard.designSurfaceYNear(
            50, 14, RoadCorridorWidth.PORTAL_TRANSITION_CLEAR_BAND_BLOCKS);

        assertEquals(ROAD_Y, designY);
    }

    @Test
    void findsPortalOnOffsetEastWestRoadNotNearCanvasOrigin() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        // E–W portal far from z=0; old lateral check used planPoint.y and skipped capping.
        plan.putClearance(
            new ColumnKey(100, 200),
            new ClearanceCell(
                new Vec2d(100, 200),
                ROAD_Y,
                ROAD_Y + 8,
                RoadCorridorConstructionMode.PORTAL,
                RoadConstructionType.TUNNEL_PORTAL));

        PortalTransitionGuard guard = PortalTransitionGuard.from(plan);
        RoadCorridorPlanBuilder.PlanHost host = CorridorTestFixtures.planHost(new RoadSystemConfig("portal"));
        int roofTop = RoadCorridorPlanBuilder.portalRoofTopY(host, ROAD_Y);
        int capped = guard.capSlopeSurfaceY(new Vec2d(102, 200), roofTop + 5, host, 4);

        assertEquals(roofTop - 1, capped,
            "slope face must stay strictly below portal roof crown");
    }

    @Test
    void slopeCapNeverReachesRoofTop() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        plan.putClearance(
            new ColumnKey(0, 0),
            new ClearanceCell(
                new Vec2d(0, 0),
                ROAD_Y,
                ROAD_Y + 8,
                RoadCorridorConstructionMode.PORTAL,
                RoadConstructionType.TUNNEL_PORTAL));

        PortalTransitionGuard guard = PortalTransitionGuard.from(plan);
        RoadCorridorPlanBuilder.PlanHost host = CorridorTestFixtures.planHost(new RoadSystemConfig("portal"));
        int roofTop = RoadCorridorPlanBuilder.portalRoofTopY(host, ROAD_Y);

        assertEquals(roofTop - 1, guard.capSlopeSurfaceY(new Vec2d(0, 0), roofTop, host, 4));
        assertEquals(roofTop - 1, guard.capSlopeSurfaceY(new Vec2d(0, 0), roofTop + 10, host, 4));
        assertTrue(guard.capSlopeSurfaceY(new Vec2d(0, 0), roofTop - 3, host, 4) <= roofTop - 1);
    }

    @Test
    void clearTopMayReachRoofButNotAbove() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        plan.putClearance(
            new ColumnKey(0, 0),
            new ClearanceCell(
                new Vec2d(0, 0),
                ROAD_Y,
                ROAD_Y + 8,
                RoadCorridorConstructionMode.PORTAL,
                RoadConstructionType.TUNNEL_PORTAL));

        PortalTransitionGuard guard = PortalTransitionGuard.from(plan);
        RoadCorridorPlanBuilder.PlanHost host = CorridorTestFixtures.planHost(new RoadSystemConfig("portal"));
        int roofTop = RoadCorridorPlanBuilder.portalRoofTopY(host, ROAD_Y);

        assertEquals(roofTop, guard.capClearTopY(new Vec2d(0, 0), roofTop + 20, host, 4));
        assertEquals(roofTop, guard.capCutTopY(new Vec2d(0, 0), roofTop + 20, host, 4));
    }
}
