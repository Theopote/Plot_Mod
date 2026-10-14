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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlopeShoulderStartContinuityTest {

    @Test
    void straightCutPlanHasNoShoulderToSlopeStartGaps() {
        assertZeroStartGaps(buildPlan(straightSegment(new Vec2d(0, 0), new Vec2d(30, 0)), 64, 100));
    }

    @Test
    void deepCutPlanHasNoShoulderToSlopeStartGaps() {
        assertZeroStartGaps(buildPlan(straightSegment(new Vec2d(0, 0), new Vec2d(40, 0)), 64, 120));
    }

    @Test
    void cutPlanEmitsSlopeCellsFromShoulderEdge() {
        RoadCorridorPlan plan = buildPlan(straightSegment(new Vec2d(0, 0), new Vec2d(10, 0)), 64, 100);
        assertTrue(plan.slopeEntries().size() > 0,
            "cut plan should emit slope cells anchored at shoulder outer edge");
        assertEquals(0, RoadGenerationIntegrityValidator.countShoulderToSlopeStartGaps(plan));
    }

    private static void assertZeroStartGaps(RoadCorridorPlan plan) {
        int gaps = RoadGenerationIntegrityValidator.countShoulderToSlopeStartGaps(plan);
        assertEquals(0, gaps, "outer SHOULDER must connect to slope start column");
    }

    private static RoadCorridorPlan buildPlan(List<Vec2d> path, int roadY, int terrainY) {
        List<PathSegment> segments = new ArrayList<>();
        List<SegmentHeightInfo> heightInfos = new ArrayList<>();
        for (int i = 1; i < path.size(); i++) {
            PathSegment segment = new PathSegment(path.get(i - 1), path.get(i));
            segments.add(segment);
            heightInfos.add(new SegmentHeightInfo(segment, roadY, roadY, roadY, roadY, 0.0));
        }
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        return RoadCorridorPlanBuilder.build(
            segments,
            heightInfos,
            CrossSectionBuildContext.fixed(crossSection),
            List.of(RoadConstructionType.ROAD),
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            new FlatTerrainSampler(terrainY),
            1.0,
            "start-continuity",
            JunctionCorridorBuildContext.NONE,
            CorridorTestFixtures.planHost(config));
    }

    private static List<Vec2d> straightSegment(Vec2d from, Vec2d to) {
        return com.plot.plugin.road.solid.RoadVoxelRasterizer.sampleSpanPoints(from, to);
    }
}
