package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RoadProfileSolverTest {

    @Test
    void solveStandaloneProducesTargetHeightsOnFlatTerrain() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        ProfileSolveSupport support = ProfileSolveSupport.fromConfig(config, segments -> 1.0);
        List<PathSegment> segments = List.of(
            new PathSegment(new Vec2d(0, 0), new Vec2d(20, 0)));

        ProfileSolveResult result = RoadProfileSolver.solveStandalone(
            segments,
            new FlatTerrainSampler(64),
            4.0,
            support);

        assertFalse(result.heightInfos().isEmpty());
        assertEquals(64, result.heightInfos().getFirst().targetStart);
        assertEquals(64, result.heightInfos().getFirst().targetEnd);
    }

    @Test
    void solveWithManualElevationPinsBothEndpoints() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        ProfileSolveSupport support = ProfileSolveSupport.fromConfig(config, segments -> 1.0);
        List<PathSegment> segments = List.of(
            new PathSegment(new Vec2d(0, 0), new Vec2d(20, 0)));

        ProfileSolveResult result = RoadProfileSolver.solveWithManualElevation(
            segments,
            new FlatTerrainSampler(64),
            4.0,
            70,
            support);

        assertEquals(70, result.heightInfos().getFirst().targetStart);
        assertEquals(70, result.heightInfos().getFirst().targetEnd);
    }
}
