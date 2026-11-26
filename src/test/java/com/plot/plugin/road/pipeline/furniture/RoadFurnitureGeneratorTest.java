package com.plot.plugin.road.pipeline.furniture;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionTransitionMode;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadFurnitureGeneratorTest {

    @Test
    void streetlightsUseSharedRoadVoxelElevationInsteadOfTerrain() {
        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(20, 0));
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(20, 72)));
        DesignElevationSource elevations = new DesignElevationSource(alignment, 0, 20, 20);
        RoadSolidModel solids = new RoadSolidModel();
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(
            new RoadSystemConfig("furniture-profile"));

        RoadFurnitureGenerator.generateStreetlights(
            solids,
            List.of(segment),
            List.of(new SegmentHeightInfo(segment, 20, 20, 20, 20, 0)),
            CrossSectionBuildContext.fixed(section),
            4,
            1.0,
            elevations,
            BuildHeightProfile.inactive(),
            null);

        var lights = solids.byLayer(RoadSolidLayer.STREETLIGHT);
        assertFalse(lights.isEmpty());
        assertTrue(lights.stream().allMatch(light -> light.elevation() >= 71));
        assertTrue(lights.stream().allMatch(light ->
            light.elevation() == elevations.elevationAtLocalDistance(light.planPoint().x).orElseThrow() + 1));
    }

    @Test
    void hasAnyLegacyStreetlightSpacingSeesLaterStationWhenFallbackOff() {
        RoadSystemConfig config = new RoadSystemConfig("furniture-var-light");
        config.setStreetlightSpacing(0);
        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        Road road = network.createRoad("r");
        road.getCrossSection().getStreetFurniture().setStreetlightSpacing(null);
        RoadCrossSection lit = new RoadCrossSection();
        lit.getStreetFurniture().setStreetlightSpacing(12);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(40.0, lit)
        ), VariableCrossSectionTransitionMode.STEP));
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        CrossSectionBuildContext ctx = CrossSectionBuildContext.forEdge(network, edge, config);
        assertTrue(ctx.hasAnyLegacyStreetlightSpacing());
        assertTrue(
            ctx.fallback().streetlightSpacing == null || ctx.fallback().streetlightSpacing <= 0);
    }

    @Test
    void streetlightsSkippedOnTunnelAndBridgeSegments() {
        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(20, 0));
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(20, 70)));
        DesignElevationSource elevations = new DesignElevationSource(alignment, 0, 20, 20);
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(
            new RoadSystemConfig("furniture-mute"));

        for (RoadConstructionType type : List.of(RoadConstructionType.TUNNEL, RoadConstructionType.BRIDGE)) {
            RoadSolidModel solids = new RoadSolidModel();
            RoadFurnitureGenerator.generateStreetlights(
                solids,
                List.of(segment),
                List.of(new SegmentHeightInfo(segment, 20, 20, 20, 20, 0)),
                CrossSectionBuildContext.fixed(section),
                4,
                1.0,
                elevations,
                BuildHeightProfile.inactive(),
                List.of(type));
            assertTrue(solids.byLayer(RoadSolidLayer.STREETLIGHT).isEmpty(), type.name());
        }
    }
}
