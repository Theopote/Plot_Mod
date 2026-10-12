package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.test.scale.ScaleInvarianceProjections;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void surfaceMaskUsesWorldColumnKeysUnderScaledProjection() {
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        CrossSectionBuildContext crossSections = CrossSectionBuildContext.fixed(crossSection);
        ICoordinateService coordinates = ScaleInvarianceProjections.FAR;
        RoadCorridorPlanBuilder.PlanHost host = CorridorTestFixtures.planHost(config, coordinates);

        Vec2d canvasStart = new Vec2d(1, 0);
        Vec2d canvasEnd = new Vec2d(6, 0);
        PathSegment segment = new PathSegment(canvasStart, canvasEnd);
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
            ScaleInvarianceProjections.worldBlocksPerCanvasUnit(coordinates),
            "scaled-seed",
            host);

        int expectedWorldX = host.canvasToBlockPos(new Vec2d(2, 0)).getX();
        assertTrue(plan.surfaceMask().get(new ColumnKey(expectedWorldX, 0)) != null,
            "surface mask must key columns by projected world XZ, not canvas coordinates");
        assertFalse(plan.surfaceMask().get(new ColumnKey(2, 0)) != null,
            "canvas coordinate 2 must not be used as a world column key under 4:1 projection");
    }

    @Test
    void bridgeSpanProducesContinuousSurfaceMask() {
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        CrossSectionBuildContext crossSections = CrossSectionBuildContext.fixed(crossSection);

        PathSegment approach = new PathSegment(new Vec2d(0, 0), new Vec2d(8, 0));
        PathSegment bridge = new PathSegment(new Vec2d(8, 0), new Vec2d(22, 0));
        PathSegment exit = new PathSegment(new Vec2d(22, 0), new Vec2d(30, 0));
        List<PathSegment> segments = List.of(approach, bridge, exit);
        List<SegmentHeightInfo> heightInfos = List.of(
            new SegmentHeightInfo(approach, 64, 64, 64, 64, 0.0),
            new SegmentHeightInfo(bridge, 64, 64, 64, 64, 0.0),
            new SegmentHeightInfo(exit, 64, 64, 64, 64, 0.0));
        List<RoadConstructionType> constructionTypes = List.of(
            RoadConstructionType.ROAD,
            RoadConstructionType.BRIDGE,
            RoadConstructionType.ROAD);

        RoadCorridorPlan plan = RoadCorridorPlanBuilder.build(
            segments,
            heightInfos,
            crossSections,
            constructionTypes,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            new FlatTerrainSampler(50),
            1.0,
            "bridge-seed",
            CorridorTestFixtures.planHost(config));

        int bridgeSurfaceColumns = 0;
        for (int x = 8; x <= 22; x++) {
            SurfaceCell cell = plan.surfaceMask().get(new ColumnKey(x, 0));
            if (cell != null && cell.layer() == RoadSolidLayer.ROAD) {
                bridgeSurfaceColumns++;
            }
        }
        assertTrue(bridgeSurfaceColumns >= 14,
            "bridge span must keep continuous ROAD surface mask, found " + bridgeSurfaceColumns);
        ClearanceCell midBridge = plan.clearanceCell(new ColumnKey(15, 0));
        assertTrue(midBridge != null, "bridge columns must still record clearance metadata");
        assertTrue(midBridge.clearTopY() > midBridge.designSurfaceY(),
            "bridge clearance envelope must have positive overhead height");
    }
}
