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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCorridorPlanBuilderTest {

    @Test
    void spanRasterizationCoversStraightSegmentLength() {
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        CrossSectionBuildContext crossSections = CrossSectionBuildContext.fixed(crossSection);

        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(20, 0));
        List<PathSegment> segments = List.of(segment);
        List<SegmentHeightInfo> heightInfos = List.of(
            new SegmentHeightInfo(segment, 64, 64, 64, 64, 0.0));
        List<RoadConstructionType> constructionTypes = List.of(RoadConstructionType.ROAD);

        RoadCorridorPlan plan = RoadCorridorPlanBuilder.build(
            segments,
            heightInfos,
            crossSections,
            constructionTypes,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            new FlatTerrainSampler(64),
            1.0,
            "test-seed",
            CorridorTestFixtures.planHost(config));

        assertTrue(plan.surfaceMask().size() >= 20,
            "surface mask must cover at least one cell per block along a 20-block segment");
        assertTrue(plan.clearanceEntries().size() >= plan.surfaceMask().size(),
            "clearance envelope must cover at least the road surface columns");
    }
}
