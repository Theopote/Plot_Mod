package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.core.terrain.FlatTerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionCorridorTrimTest {

    @Test
    void trimsCorridorNearTJunction() {
        RoadNetwork network = tJunctionNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        RoadEdge approach = network.getEdges().values().stream()
            .filter(edge -> edge.getStartNodeId().equals(junction.getId()) || edge.getEndNodeId().equals(junction.getId()))
            .filter(edge -> edge.getLength() > 50.0)
            .findFirst()
            .orElseThrow();

        RoadSystemConfig config = new RoadSystemConfig("test");
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        JunctionCorridorBuildContext context = JunctionCorridorTrim.forEdge(
            network, approach, crossSection, config, 1.0);

        assertTrue(context.endpointTrim().skipStart() > 0.0 || context.endpointTrim().skipEnd() > 0.0);
        assertFalse(context.exclusionPolygons().isEmpty());
    }

    @Test
    void skipDistanceMatchesFillPolygonRadius() {
        RoadNetwork network = tJunctionNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        RoadEdge approach = network.getEdges().values().stream()
            .filter(edge -> edge.getStartNodeId().equals(junction.getId())
                || edge.getEndNodeId().equals(junction.getId()))
            .filter(edge -> edge.getLength() > 50.0)
            .findFirst()
            .orElseThrow();

        RoadSystemConfig config = new RoadSystemConfig("test");
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        JunctionCorridorBuildContext context = JunctionCorridorTrim.forEdge(
            network, approach, crossSection, config, 1.0);

        double skip = Math.max(context.endpointTrim().skipStart(), context.endpointTrim().skipEnd());
        double expected = com.plot.plugin.road.RoadJunctionGeometry.resolveEffectiveJunctionRadius(
            network.getEdges().values().stream()
                .filter(edge -> edge.getStartNodeId().equals(junction.getId())
                    || edge.getEndNodeId().equals(junction.getId()))
                .toList(),
            edge -> com.plot.plugin.road.RoadDimensionUtils.halfExtentFromCenter(
                com.plot.plugin.road.model.RoadModelUtils.resolveCrossSection(network, edge, config)
                    .totalHardSurfaceWidthBlocks()),
            com.plot.plugin.road.RoadJunctionGeometry.DEFAULT_JUNCTION_RADIUS);
        assertEquals(expected, skip, 1e-6);
        assertTrue(skip < expected + 0.5);
    }

    @Test
    void doesNotTrimElevatedRoadAtGradeSeparatedJunction() {
        RoadNetwork network = crossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        Road roadB = network.getRoad("roadB");
        network.setNodeGradeSeparation(junction.getId(), true, roadB.getId(), 3.0);

        RoadEdge elevated = network.getEdges().values().stream()
            .filter(edge -> roadB.getId().equals(edge.getRoadId()))
            .findFirst()
            .orElseThrow();

        RoadSystemConfig config = new RoadSystemConfig("test");
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        JunctionCorridorBuildContext elevatedContext = JunctionCorridorTrim.forEdge(
            network, elevated, crossSection, config, 1.0);

        assertFalse(JunctionCorridorTrim.shouldTrimAtEndpoint(network, elevated, junction));
        assertTrue(elevatedContext.exclusionPolygons().isEmpty());
    }

    @Test
    void junctionTrimReducesOverlappingSurfaceColumnsInsidePolygon() {
        RoadNetwork network = tJunctionNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        RoadEdge approach = network.getEdges().values().stream()
            .filter(edge -> edge.getStartNodeId().equals(junction.getId()) || edge.getEndNodeId().equals(junction.getId()))
            .filter(edge -> edge.getLength() > 50.0)
            .findFirst()
            .orElseThrow();
        List<Vec2d> centerline = List.of(new Vec2d(0, 0), new Vec2d(80, 0));

        RoadSystemConfig config = new RoadSystemConfig("test");
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        JunctionCorridorBuildContext trim = JunctionCorridorTrim.forEdge(
            network, approach, crossSection, config, 1.0);

        PathSegment segment = new PathSegment(centerline.get(0), centerline.get(1));
        RoadCorridorPlan withoutTrim = RoadCorridorPlanBuilder.build(
            List.of(segment),
            List.of(new SegmentHeightInfo(segment, 64, 64, 64, 64, 0.0)),
            CrossSectionBuildContext.fixed(crossSection),
            List.of(com.plot.plugin.road.RoadConstructionType.ROAD),
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            new FlatTerrainSampler(64),
            1.0,
            "junction-trim",
            JunctionCorridorBuildContext.NONE,
            CorridorTestFixtures.planHost(config));

        RoadCorridorPlan withTrim = RoadCorridorPlanBuilder.build(
            List.of(segment),
            List.of(new SegmentHeightInfo(segment, 64, 64, 64, 64, 0.0)),
            CrossSectionBuildContext.fixed(crossSection),
            List.of(com.plot.plugin.road.RoadConstructionType.ROAD),
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            new FlatTerrainSampler(64),
            1.0,
            "junction-trim",
            trim,
            CorridorTestFixtures.planHost(config));

        assertTrue(withTrim.surfaceMask().size() < withoutTrim.surfaceMask().size());
    }

    private static RoadNetwork tJunctionNetwork() {
        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 40));
        RoadNode east = network.createNode(new Vec2d(80, 0));
        RoadNode south = network.createNode(new Vec2d(0, -40));
        network.createEdge(junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 40)), "roadA");
        network.createEdge(junction.getId(), east.getId(), List.of(new Vec2d(0, 0), new Vec2d(80, 0)), "roadB");
        network.createEdge(junction.getId(), south.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, -40)), "roadC");
        return network;
    }

    private static RoadNetwork crossNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("roadA");
        Road roadB = network.createRoad("roadB");
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 40));
        RoadNode south = network.createNode(new Vec2d(0, -40));
        RoadNode east = network.createNode(new Vec2d(80, 0));
        RoadNode west = network.createNode(new Vec2d(-80, 0));
        network.createEdge(junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 40)), roadA.getId());
        network.createEdge(junction.getId(), south.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, -40)), roadA.getId());
        network.createEdge(junction.getId(), east.getId(), List.of(new Vec2d(0, 0), new Vec2d(80, 0)), roadB.getId());
        network.createEdge(junction.getId(), west.getId(), List.of(new Vec2d(0, 0), new Vec2d(-80, 0)), roadB.getId());
        return network;
    }
}
