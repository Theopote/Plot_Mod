package com.plot.plugin.road.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.golden.RoadGoldenScenarioFactory;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Road ↔ Earthwork 集成回归：设计标高采样与走廊几何。
 */
class RoadEarthworkIntegrationTest {

    @Test
    void sampleDesignSurfaceYUsesManualNodeElevation() {
        RoadNetwork network = RoadGoldenScenarioFactory.r01StraightFlat().network();
        RoadEdge edge = network.getEdges().values().iterator().next();
        RoadNode start = network.getNode(edge.getStartNodeId());
        RoadNode end = network.getNode(edge.getEndNodeId());
        start.setManualElevation(72.0);
        end.setManualElevation(72.0);

        Integer sampled = RoadEarthworkSurfaceSampler.sampleDesignSurfaceY(
            network, edge.getId(), start.getPosition());
        assertNotNull(sampled);
        assertEquals(72, sampled);
    }

    @Test
    void sampleDesignSurfaceYWithoutVaOrManualElevationReturnsNullNotSeaLevel() {
        RoadNetwork network = RoadGoldenScenarioFactory.r01StraightFlat().network();
        RoadEdge edge = network.getEdges().values().iterator().next();

        Integer sampled = RoadEarthworkSurfaceSampler.sampleDesignSurfaceY(
            network, edge.getId(), new Vec2d(15, 0));

        assertNull(sampled, "AUTO_SMOOTH without persisted VA must not fall back to Y=64");
        assertFalse(RoadEarthworkSurfaceSampler.hasResolvableDesignElevation(network, edge.getId()));
    }

    @Test
    void sampleDesignSurfaceYInterpolatesBetweenBothManualNodeElevations() {
        RoadNetwork network = RoadGoldenScenarioFactory.r01StraightFlat().network();
        RoadEdge edge = network.getEdges().values().iterator().next();
        RoadNode start = network.getNode(edge.getStartNodeId());
        RoadNode end = network.getNode(edge.getEndNodeId());
        start.setManualElevation(70.0);
        end.setManualElevation(80.0);

        Integer mid = RoadEarthworkSurfaceSampler.sampleDesignSurfaceY(
            network, edge.getId(), new Vec2d(15, 0));

        assertNotNull(mid);
        assertEquals(75, mid);
        assertTrue(RoadEarthworkSurfaceSampler.hasResolvableDesignElevation(network, edge.getId()));
    }

    @Test
    void sampleDesignSurfaceYReturnsNullWhenOnlyOneNodeHasManualElevation() {
        RoadNetwork network = RoadGoldenScenarioFactory.r01StraightFlat().network();
        RoadEdge edge = network.getEdges().values().iterator().next();
        network.getNode(edge.getStartNodeId()).setManualElevation(72.0);

        assertNull(RoadEarthworkSurfaceSampler.sampleDesignSurfaceY(
            network, edge.getId(), new Vec2d(10, 0)));
        assertFalse(RoadEarthworkSurfaceSampler.hasResolvableDesignElevation(network, edge.getId()));
    }

    @Test
    void sampleDesignSurfaceYUsesManualProfileWhenActive() {
        RoadNetwork network = RoadGoldenScenarioFactory.r06Sloped().network();
        RoadEdge edge = network.getEdges().values().iterator().next();

        Integer start = RoadEarthworkSurfaceSampler.sampleDesignSurfaceY(
            network, edge.getId(), new Vec2d(0, 0));
        Integer end = RoadEarthworkSurfaceSampler.sampleDesignSurfaceY(
            network, edge.getId(), new Vec2d(30, 0));

        assertNotNull(start);
        assertNotNull(end);
        assertEquals(64, start);
        assertEquals(72, end);
        assertTrue(RoadEarthworkSurfaceSampler.hasResolvableDesignElevation(network, edge.getId()));
    }

    @Test
    void corridorOutlineMatchesRoadWidth() {
        RoadNetwork network = RoadGoldenScenarioFactory.r01StraightFlat().network();
        RoadEdge edge = network.getEdges().values().iterator().next();
        List<Vec2d> centerline = List.of(new Vec2d(0, 0), new Vec2d(30, 0));
        List<Vec2d> polygon = RoadEarthworkCorridorResolver.buildCorridorPolygon(centerline, 4.0);

        assertTrue(polygon.size() >= 4);
        assertTrue(polygon.stream().anyMatch(p -> Math.abs(p.y - 4.0) < 1e-6));
        assertTrue(polygon.stream().anyMatch(p -> Math.abs(p.y + 4.0) < 1e-6));
    }

    @Test
    void listEdgeRefsUpdatesAfterNetworkEdit() {
        RoadNetwork network = RoadGoldenScenarioFactory.r01StraightFlat().network();
        assertEquals(1, RoadEarthworkSurfaceSampler.listEdgeRefs(network).size());

        RoadNode extra = network.createNode(new Vec2d(30, 0));
        RoadNode end = network.createNode(new Vec2d(50, 0));
        network.createEdge(extra.getId(), end.getId(), List.of(extra.getPosition(), end.getPosition()));

        assertEquals(2, RoadEarthworkSurfaceSampler.listEdgeRefs(network).size());
    }

    @Test
    void staleEdgeReferenceReturnsNullSample() {
        RoadNetwork network = RoadGoldenScenarioFactory.r01StraightFlat().network();
        String removedEdgeId = network.getEdges().values().iterator().next().getId();
        network.removeEdge(removedEdgeId);

        assertNull(RoadEarthworkSurfaceSampler.sampleDesignSurfaceY(
            network, removedEdgeId, new Vec2d(0, 0)));
        assertTrue(RoadEarthworkSurfaceSampler.listEdgeRefs(network).isEmpty());
    }
}
