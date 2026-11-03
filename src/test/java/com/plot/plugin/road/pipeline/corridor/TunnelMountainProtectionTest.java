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
}
