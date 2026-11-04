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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 隧道洞身外侧不得残留开放边坡切方柱（会掏穿山体）。
 */
class TunnelMountainProtectionTest {

    @Test
    void tunnelBodyPlanHasNoOpenBatterInsideMountainProtection() {
        List<Vec2d> path = new ArrayList<>();
        path.addAll(RoadVoxelRasterizer.sampleSpanPoints(new Vec2d(0, 0), new Vec2d(8, 0)));
        path.addAll(RoadVoxelRasterizer.sampleSpanPoints(new Vec2d(8, 0), new Vec2d(12, 0)));
        path.addAll(RoadVoxelRasterizer.sampleSpanPoints(new Vec2d(12, 0), new Vec2d(28, 0)));
        path.addAll(RoadVoxelRasterizer.sampleSpanPoints(new Vec2d(28, 0), new Vec2d(32, 0)));
        path.addAll(RoadVoxelRasterizer.sampleSpanPoints(new Vec2d(32, 0), new Vec2d(40, 0)));

        List<PathSegment> segments = new ArrayList<>();
        List<SegmentHeightInfo> heightInfos = new ArrayList<>();
        for (int i = 1; i < path.size(); i++) {
            PathSegment segment = new PathSegment(path.get(i - 1), path.get(i));
            segments.add(segment);
            heightInfos.add(new SegmentHeightInfo(segment, 64, 64, 64, 64, 0.0));
        }
        // 段类型：OPEN → PORTAL → TUNNEL → PORTAL → OPEN（按段索引近似）
        List<RoadConstructionType> types = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            double midX = (segments.get(i).start.x + segments.get(i).end.x) * 0.5;
            if (midX < 8) {
                types.add(RoadConstructionType.ROAD);
            } else if (midX < 12) {
                types.add(RoadConstructionType.TUNNEL_PORTAL);
            } else if (midX < 28) {
                types.add(RoadConstructionType.TUNNEL);
            } else if (midX < 32) {
                types.add(RoadConstructionType.TUNNEL_PORTAL);
            } else {
                types.add(RoadConstructionType.ROAD);
            }
        }

        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        config.setIncludeSlopeBatter(true);
        config.setCutSlopeRatio(1.0f);
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        RoadCorridorPlan plan = RoadCorridorPlanBuilder.build(
            segments,
            heightInfos,
            CrossSectionBuildContext.fixed(crossSection),
            types,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            new FlatTerrainSampler(96),
            1.0,
            "tunnel-protect",
            JunctionCorridorBuildContext.NONE,
            CorridorTestFixtures.planHost(config));

        TunnelMountainProtection protection = TunnelMountainProtection.from(plan, crossSection);
        assertTrue(protection.protectedColumnCount() > 0, "tunnel plan should seed mountain protection");

        int batterInside = 0;
        for (var entry : plan.slopeEntries()) {
            if (protection.protects(entry.getKey())) {
                batterInside++;
            }
        }
        assertEquals(0, batterInside,
            "no open batter columns may remain inside tunnel mountain protection");
        assertEquals(0, RoadGenerationIntegrityValidator.countStructureInteriorSlopeCells(plan));
    }

    @Test
    void protectionIsColumnarAndIndependentOfElevation() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        plan.putClearance(
            new ColumnKey(10, 4),
            new ClearanceCell(
                new Vec2d(10, 4), 40, 46,
                RoadCorridorConstructionMode.TUNNEL,
                RoadConstructionType.TUNNEL));
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(CorridorTestFixtures.baseConfig());
        TunnelMountainProtection protection = TunnelMountainProtection.from(plan, section);
        assertTrue(protection.protects(10, 4));
        // 2D column mask: same XZ is protected regardless of design Y (documented conservative behavior)
        assertTrue(protection.protects(new ColumnKey(10, 4)));
        assertTrue(protection.protects(11, 4) || protection.protects(10, 5),
            "body radius must expand at least one neighbor column");
    }

    @Test
    void diagonalTunnelPortalScrubsBatterInsideProtectionEnvelope() {
        List<Vec2d> path = new ArrayList<>();
        // 45° diagonal through mountain
        path.addAll(RoadVoxelRasterizer.sampleSpanPoints(new Vec2d(0, 0), new Vec2d(10, 10)));
        path.addAll(RoadVoxelRasterizer.sampleSpanPoints(new Vec2d(10, 10), new Vec2d(14, 14)));
        path.addAll(RoadVoxelRasterizer.sampleSpanPoints(new Vec2d(14, 14), new Vec2d(28, 28)));
        path.addAll(RoadVoxelRasterizer.sampleSpanPoints(new Vec2d(28, 28), new Vec2d(32, 32)));
        path.addAll(RoadVoxelRasterizer.sampleSpanPoints(new Vec2d(32, 32), new Vec2d(40, 40)));
        List<PathSegment> segments = new ArrayList<>();
        List<SegmentHeightInfo> heightInfos = new ArrayList<>();
        List<RoadConstructionType> types = new ArrayList<>();
        for (int i = 1; i < path.size(); i++) {
            PathSegment segment = new PathSegment(path.get(i - 1), path.get(i));
            segments.add(segment);
            heightInfos.add(new SegmentHeightInfo(segment, 90, 90, 64, 64, 0.0));
            double mid = (path.get(i - 1).x + path.get(i).x) * 0.5;
            if (mid < 10) {
                types.add(RoadConstructionType.ROAD);
            } else if (mid < 14) {
                types.add(RoadConstructionType.TUNNEL_PORTAL);
            } else if (mid < 28) {
                types.add(RoadConstructionType.TUNNEL);
            } else if (mid < 32) {
                types.add(RoadConstructionType.TUNNEL_PORTAL);
            } else {
                types.add(RoadConstructionType.ROAD);
            }
        }
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        config.setIncludeSlopeBatter(true);
        config.setCutSlopeRatio(1.0f);
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        RoadCorridorPlan plan = RoadCorridorPlanBuilder.build(
            segments,
            heightInfos,
            CrossSectionBuildContext.fixed(crossSection),
            types,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            new FlatTerrainSampler(96),
            1.0,
            "diag-tunnel-protect",
            JunctionCorridorBuildContext.NONE,
            CorridorTestFixtures.planHost(config));
        TunnelMountainProtection protection = TunnelMountainProtection.from(plan, crossSection);
        assertTrue(protection.protectedColumnCount() > 0);
        long batterInside = plan.slopeEntries().stream()
            .filter(e -> protection.protects(e.getKey()))
            .count();
        assertEquals(0, batterInside,
            "diagonal tunnel mountain protection must scrub open batter inside its envelope");
    }
}
