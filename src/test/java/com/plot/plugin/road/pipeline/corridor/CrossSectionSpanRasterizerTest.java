package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.solid.RoadSolidLayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossSectionSpanRasterizerTest {

    @Test
    void zeroDistanceSignificantTurnFansAngularStamps() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        var config = CorridorTestFixtures.baseConfig();
        var host = CorridorTestFixtures.planHost(config);
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        Vec2d center = new Vec2d(10, 10);
        // Shared vertex: eastbound left-normal (0,1) → northbound left-normal (-1,0)
        CrossSectionSpanRasterizer.rasterizeSpan(
            plan,
            center,
            center,
            new Vec2d(0, 1),
            new Vec2d(-1, 0),
            64,
            64,
            1.0,
            crossSection,
            "corner-fan",
            host,
            new FlatTerrainSampler(70),
            RoadConstructionType.CUT,
            8,
            null);
        assertTrue(plan.surfaceMask().entries().size() >= 8,
            "zero-distance 90° turn must fan multiple pavement columns, not a single normal");
        // Outer footprints of both end normals should appear
        assertTrue(hasSurfaceNear(plan, 10, 12, 2) || hasSurfaceNear(plan, 10, 13, 2),
            "incoming normal (+Z) shoulder/road must stamp");
        assertTrue(hasSurfaceNear(plan, 8, 10, 2) || hasSurfaceNear(plan, 7, 10, 2),
            "outgoing normal (-X) shoulder/road must stamp");
    }

    private static boolean hasSurfaceNear(RoadCorridorPlan plan, int x, int z, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (plan.surfaceMask().get(new ColumnKey(x + dx, z + dz)) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void crossesCenterlineWhenProjectedOnOppositeSide() {
        Vec2d center = new Vec2d(0, 0);
        Vec2d normal = new Vec2d(1, 0);
        assertTrue(CrossSectionSpanRasterizer.crossesCenterline(
            center, normal, new Vec2d(-1, 0), 2.0, 1.0));
        assertFalse(CrossSectionSpanRasterizer.crossesCenterline(
            center, normal, new Vec2d(2, 0), 2.0, 1.0));
        assertFalse(CrossSectionSpanRasterizer.crossesCenterline(
            center, normal, new Vec2d(-0.1, 0), 2.0, 1.0));
    }

    @Test
    void rejectsSidewalkStampInsideCarriagewayHalfWidth() {
        Vec2d center = new Vec2d(0, 0);
        Vec2d normal = new Vec2d(1, 0);
        // 人行道预期 lateral=3，但投影落在行车道半宽(2)内
        assertTrue(CrossSectionSpanRasterizer.shouldRejectIntrudingStamp(
            center, normal, new Vec2d(1.0, 0), 3.0, 1.0, 2.0, RoadSolidLayer.SIDEWALK));
        // 正常落在外侧
        assertFalse(CrossSectionSpanRasterizer.shouldRejectIntrudingStamp(
            center, normal, new Vec2d(3.0, 0), 3.0, 1.0, 2.0, RoadSolidLayer.SIDEWALK));
        // 行车道本身不因半宽裁剪被拒
        assertFalse(CrossSectionSpanRasterizer.shouldRejectIntrudingStamp(
            center, normal, new Vec2d(1.0, 0), 1.0, 1.0, 2.0, RoadSolidLayer.ROAD));
    }
}
