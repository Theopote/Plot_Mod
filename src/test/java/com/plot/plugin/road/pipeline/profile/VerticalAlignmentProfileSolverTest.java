package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VerticalAlignmentProfileSolverTest {

    @Test
    void linearGradeProducesDesignHeights() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        ProfileSolveSupport support = ProfileSolveSupport.fromConfig(config, segments -> 1.0);
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.of(100.0, 90.0)
        ));
        List<PathSegment> segments = List.of(
            new PathSegment(new Vec2d(0, 0), new Vec2d(50, 0)),
            new PathSegment(new Vec2d(50, 0), new Vec2d(100, 0)));

        ProfileSolveResult result = VerticalAlignmentProfileSolver.solveForEdge(
            alignment,
            0.0,
            100.0,
            segments,
            new FlatTerrainSampler(64),
            4.0,
            null,
            null,
            support);

        assertFalse(result.heightInfos().isEmpty());
        assertEquals(80, result.heightInfos().getFirst().targetStart);
        assertEquals(85, result.heightInfos().getFirst().targetEnd);
        assertEquals(85, result.heightInfos().get(1).targetStart);
        assertEquals(90, result.heightInfos().get(1).targetEnd);
    }

    @Test
    void manualEndpointOverridesDesignElevation() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        ProfileSolveSupport support = ProfileSolveSupport.fromConfig(config, segments -> 1.0);
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.of(100.0, 90.0)
        ));
        List<PathSegment> segments = List.of(
            new PathSegment(new Vec2d(0, 0), new Vec2d(100, 0)));

        ProfileSolveResult result = VerticalAlignmentProfileSolver.solveForEdge(
            alignment,
            0.0,
            100.0,
            segments,
            new FlatTerrainSampler(64),
            4.0,
            70,
            95,
            support);

        assertEquals(70, result.heightInfos().getFirst().targetStart);
        assertEquals(95, result.heightInfos().getFirst().targetEnd);
    }

    @Test
    void crestCurveMidpointIsNonLinear() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        ProfileSolveSupport support = ProfileSolveSupport.fromConfig(config, segments -> 1.0);
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 100.0),
            PointOfVerticalIntersection.withCurve(50.0, 110.0, 40.0),
            PointOfVerticalIntersection.of(100.0, 105.0)
        ));
        List<PathSegment> segments = List.of(
            new PathSegment(new Vec2d(0, 0), new Vec2d(100, 0)));

        ProfileSolveResult result = VerticalAlignmentProfileSolver.solveForEdge(
            alignment,
            0.0,
            100.0,
            segments,
            new FlatTerrainSampler(64),
            4.0,
            null,
            null,
            support);

        DesignElevationSource designElevation = new DesignElevationSource(
            alignment,
            0.0,
            100.0,
            100.0);
        int curveMid = designElevation.elevationAtChainage(50.0);
        int linearMid = (result.heightInfos().getFirst().targetStart
            + result.heightInfos().getFirst().targetEnd) / 2;
        assertEquals(109, curveMid);
        assertEquals(102, linearMid);
    }
}
