package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadVoxelRasterizer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 急折外侧：按段垂向伸出的边坡射线会在远端张开成三角空隙；角向加密后应覆盖该楔形。
 */
class SlopeOuterCornerContinuityTest {

    @Test
    void sharpLTurnOuterWedgeHasSlopeAndClearance() {
        // 东行再北折：左转，外侧楔形在折点东南方
        List<Vec2d> path = new ArrayList<>();
        path.addAll(RoadVoxelRasterizer.sampleSpanPoints(new Vec2d(0, 0), new Vec2d(40, 0)));
        path.addAll(RoadVoxelRasterizer.sampleSpanPoints(new Vec2d(40, 0), new Vec2d(40, 40)));

        // 覆土 8 < 默认深埋门槛(净高+衬砌+隧道阈值≈10)，仍属普通路堑，应有连续外侧边坡
        RoadCorridorPlan plan = buildCutPlan(path, 64, 72);
        assertTrue(plan.slopeEntries().size() > 20, "cut L-turn should emit many slope columns");

        // 折点 (40,0) 外侧 SE：角向加密应盖住楔形（覆土 8 → 边坡水平约 10 格内）
        List<Vec2d> probes = List.of(
            new Vec2d(45, -4),
            new Vec2d(47, -6),
            new Vec2d(44, -7),
            new Vec2d(48, -5),
            new Vec2d(46, -8));
        for (Vec2d probe : probes) {
            assertTrue(hasNearbySlope(plan, probe, 2),
                "outer corner wedge missing slope near " + probe);
            assertTrue(hasNearbyClearance(plan, probe, 2),
                "outer corner wedge missing clearance near " + probe);
        }
    }

    private static boolean hasNearbySlope(RoadCorridorPlan plan, Vec2d probe, int radius) {
        int px = (int) Math.round(probe.x);
        int pz = (int) Math.round(probe.y);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (plan.slopeCell(new ColumnKey(px + dx, pz + dz)) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasNearbyClearance(RoadCorridorPlan plan, Vec2d probe, int radius) {
        int px = (int) Math.round(probe.x);
        int pz = (int) Math.round(probe.y);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (plan.clearanceCell(new ColumnKey(px + dx, pz + dz)) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static RoadCorridorPlan buildCutPlan(List<Vec2d> path, int roadY, int terrainY) {
        List<PathSegment> segments = new ArrayList<>();
        List<SegmentHeightInfo> heightInfos = new ArrayList<>();
        List<RoadConstructionType> types = new ArrayList<>();
        for (int i = 1; i < path.size(); i++) {
            PathSegment segment = new PathSegment(path.get(i - 1), path.get(i));
            segments.add(segment);
            heightInfos.add(new SegmentHeightInfo(segment, roadY, roadY, roadY, roadY, 0.0));
            types.add(RoadConstructionType.ROAD);
        }
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        config.setCutSlopeRatio(1.0f);
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        return RoadCorridorPlanBuilder.build(
            segments,
            heightInfos,
            CrossSectionBuildContext.fixed(crossSection),
            types,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            new FlatTerrainSampler(terrainY),
            1.0,
            "outer-corner",
            JunctionCorridorBuildContext.NONE,
            CorridorTestFixtures.planHost(config));
    }
}
