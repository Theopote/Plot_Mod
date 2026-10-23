package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelPortalBackfillPassTest {

    @Test
    void sealsHillsideAndSkipsOpenApproachInFrontOfPortal() {
        Vec2d center = new Vec2d(0, 0);
        Vec2d forward = new Vec2d(1, 0);
        Vec2d leftNormal = new Vec2d(0, 1);
        RoadCorridorPlan plan = new RoadCorridorPlan();
        plan.registerPortalBackfillSite(new PortalBackfillSite(center, leftNormal, forward, 64, 5, 1.0));
        plan.putClearance(
            new ColumnKey(-3, 0),
            new ClearanceCell(new Vec2d(-3, 0), 64, 72,
                RoadCorridorConstructionMode.OPEN, RoadConstructionType.ROAD));
        plan.putClearance(
            new ColumnKey(3, 0),
            new ClearanceCell(new Vec2d(3, 0), 64, 72,
                RoadCorridorConstructionMode.TUNNEL, RoadConstructionType.TUNNEL));

        var host = CorridorTestFixtures.planHost(new RoadSystemConfig("portal-backfill"));
        assertEquals(1, TunnelPortalBackfillPass.resolveIntoHillSign(
            plan, host, center, forward, 8, 1.0));
        assertTrue(TunnelPortalBackfillPass.isOpenApproachColumn(plan, host, new Vec2d(-3, 0)));

        TerrainSampler hill = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 80;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= 80;
            }
        };
        RoadSolidModel solids = new RoadSolidModel();
        TunnelPortalBackfillPass.apply(solids, plan, hill, host);

        boolean approachSealed = solids.primitives().stream().anyMatch(p ->
            Math.abs(p.planPoint().x + 3) < 0.6 && Math.abs(p.planPoint().y) < 0.6);
        boolean hillsideSealed = solids.primitives().stream().anyMatch(p ->
            Math.abs(p.planPoint().x - 3) < 0.6 && Math.abs(p.planPoint().y) < 0.6);
        assertTrue(!approachSealed, "open approach in front of the mouth must not receive a roof lid");
        assertTrue(hillsideSealed, "hillside / tunnel interior should restore a roof shell");
    }
}
