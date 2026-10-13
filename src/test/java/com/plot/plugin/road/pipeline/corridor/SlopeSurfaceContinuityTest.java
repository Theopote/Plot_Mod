package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SlopeSurfaceContinuityTest {

    @Test
    void slopedCutRoadHasSlopeColumnsAlongSpanSamples() {
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        assertTrue(crossSection.includeSlopeBatter, "test requires slope batter enabled");
        CrossSectionBuildContext crossSections = CrossSectionBuildContext.fixed(crossSection);

        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(40, 0));
        List<PathSegment> segments = List.of(segment);
        List<SegmentHeightInfo> heightInfos = List.of(
            new SegmentHeightInfo(segment, 95, 95, 64, 80, 0.4));
        List<RoadConstructionType> constructionTypes = List.of(RoadConstructionType.ROAD);

        TerrainSampler highGround = new FlatTerrainSampler(95);

        RoadCorridorPlanBuilder.PlanHost host = CorridorTestFixtures.planHost(config);
        RoadCorridorPlan plan = RoadCorridorPlanBuilder.build(
            segments,
            heightInfos,
            crossSections,
            constructionTypes,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            highGround,
            1.0,
            "slope-span",
            host);

        int missingAlongPath = 0;
        for (int x = 2; x <= 38; x++) {
            final int worldX = x;
            boolean hasCutColumn = plan.slopeEntries().stream()
                .anyMatch(entry -> entry.getKey().worldX() == worldX
                    && entry.getValue().hasCut());
            if (!hasCutColumn) {
                missingAlongPath++;
            }
        }
        assertTrue(missingAlongPath <= 2,
            "cut slope should cover almost every span sample along path, missing=" + missingAlongPath);
        assertTrue(plan.slopeEntries().size() > 40,
            "continuous span raster should produce many slope columns");
    }

    @Test
    void deepCutRecordsWidthCapDiagnostic() {
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        CrossSectionBuildContext crossSections = CrossSectionBuildContext.fixed(crossSection);

        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(8, 0));
        List<PathSegment> segments = List.of(segment);
        List<SegmentHeightInfo> heightInfos = List.of(
            new SegmentHeightInfo(segment, 120, 120, 64, 64, 0.0));
        List<RoadConstructionType> constructionTypes = List.of(RoadConstructionType.ROAD);

        TerrainSampler cliff = new FlatTerrainSampler(120);

        RoadCorridorPlan plan = RoadCorridorPlanBuilder.build(
            segments,
            heightInfos,
            crossSections,
            constructionTypes,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            cliff,
            1.0,
            "deep-cut",
            CorridorTestFixtures.planHost(config));

        int requiredRun = (int) Math.ceil(56 * Math.max(0.5f, crossSection.cutSlopeRatio)) + 2;
        if (requiredRun > RoadCorridorWidth.MAX_SLOPE_BATTER_RUN_BLOCKS) {
            assertTrue(plan.slopeBatterWidthCapCount() > 0,
                "deep cut beyond max batter run must record width-cap diagnostic");
        }
    }

}
