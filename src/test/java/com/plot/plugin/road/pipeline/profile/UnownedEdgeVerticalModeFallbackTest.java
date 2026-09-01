package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class UnownedEdgeVerticalModeFallbackTest {

    @Test
    void unownedEdgeFallsBackToAutoSmoothWithoutDereferencingNullRoadId() {
        RoadNetwork network = new RoadNetwork();
        var start = network.createNode(new Vec2d(0, 0));
        var end = network.createNode(new Vec2d(40, 0));
        RoadEdge edge = network.createEdge(
            start.getId(), end.getId(), List.of(start.getPosition(), end.getPosition()));
        List<PathSegment> segments = List.of(new PathSegment(start.getPosition(), end.getPosition()));
        RoadSystemConfig config = new RoadSystemConfig("unowned-edge");

        ProfileSolveResult result = RoadProfileSolver.solveForEdge(
            segments,
            new FlatTerrainSampler(64),
            network,
            edge,
            config,
            2.5,
            null,
            null,
            ProfileSolveSupport.fromConfig(config, ignored -> 1.0));

        assertFalse(result.heightInfos().isEmpty());
    }
}
