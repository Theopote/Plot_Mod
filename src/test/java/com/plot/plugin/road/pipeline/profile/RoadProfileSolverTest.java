package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void adoptedTerrainAdaptiveRoadTracksInteriorTerrain() {
        RoadNetwork network = new RoadNetwork();
        RoadNetworkBuilder builder = new RoadNetworkBuilder();
        RoadSystemConfig config = new RoadSystemConfig("test");
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)), false), config);

        Road road = network.getRoads().values().iterator().next();
        RoadEdge edge = network.getEdges().values().iterator().next();
        assertEquals(RoadVerticalMode.FIT_TERRAIN, road.getVerticalMode());

        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 64 + (int) Math.round(4.0 * Math.sin(point.x / 15.0));
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return false;
            }
        };

        List<PathSegment> segments = sampledSegments(new Vec2d(0, 0), new Vec2d(100, 0), 10.0);
        ProfileSolveSupport support = ProfileSolveSupport.fromConfig(config, ignored -> 1.0);
        ProfileSolveResult result = RoadProfileSolver.solveForEdge(
            segments, terrain, network, edge, config, 2.5, null, null, support);

        List<Integer> targets = result.profileTargetHeights();
        assertFalse(targets.isEmpty());
        int mid = targets.size() / 2;
        assertNotEquals(targets.getFirst(), targets.get(mid),
            "adopted road should follow interior terrain without manual vertical mode change");
    }

    @Test
    void fitTerrainTracksInteriorTerrainVariation() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("adaptive");
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(100, 0));
        RoadEdge edge = network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)),
            road.getId());

        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 64 + (int) Math.round(4.0 * Math.sin(point.x / 15.0));
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return false;
            }
        };

        List<PathSegment> segments = sampledSegments(new Vec2d(0, 0), new Vec2d(100, 0), 10.0);
        RoadSystemConfig config = new RoadSystemConfig("test");
        ProfileSolveSupport support = ProfileSolveSupport.fromConfig(config, ignored -> 1.0);

        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        ProfileSolveResult fit = RoadProfileSolver.solveForEdge(
            segments, terrain, network, edge, config, 2.5, null, null, support);

        road.setVerticalMode(RoadVerticalMode.AUTO_SMOOTH);
        ProfileSolveResult smooth = RoadProfileSolver.solveForEdge(
            segments, terrain, network, edge, config, 2.5, null, null, support);

        List<Integer> fitTargets = fit.profileTargetHeights();
        List<Integer> ground = fit.profileGroundHeights();
        List<Integer> guide = fit.profileGuideLine();
        assertFalse(fitTargets.isEmpty());
        assertEquals(fitTargets.size(), ground.size());
        assertEquals(fitTargets.size(), guide.size());

        int mid = fitTargets.size() / 2;
        assertNotEquals(fitTargets.getFirst(), fitTargets.get(mid),
            "FIT_TERRAIN should vary along interior stations on undulating terrain");

        double targetGuideError = meanAbsoluteError(fitTargets, guide);
        double targetGroundError = meanAbsoluteError(fitTargets, ground);
        assertTrue(targetGuideError <= targetGroundError + 1.5,
            () -> "v2 targets should follow smoothed trend, not chase raw ground spikes");

        double fitGroundError = meanAbsoluteError(fitTargets, ground);
        double smoothGroundError = meanAbsoluteError(smooth.profileTargetHeights(), smooth.profileGroundHeights());
        assertTrue(fitGroundError < smoothGroundError,
            () -> "FIT_TERRAIN should track terrain more closely than AUTO_SMOOTH");
    }

    @Test
    void fitTerrainGuideUsesSmoothedTrendNotRawGroundCopy() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("step-road");
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(100, 0));
        RoadEdge edge = network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)),
            road.getId());
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);

        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return point.x < 50.0 ? 60 : 75;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return false;
            }
        };

        List<PathSegment> segments = sampledSegments(new Vec2d(0, 0), new Vec2d(100, 0), 10.0);
        RoadSystemConfig config = new RoadSystemConfig("test");
        ProfileSolveSupport support = ProfileSolveSupport.fromConfig(config, ignored -> 1.0);
        ProfileSolveResult result = RoadProfileSolver.solveForEdge(
            segments, terrain, network, edge, config, 2.5, null, null, support);

        List<Integer> ground = result.profileGroundHeights();
        List<Integer> guide = result.profileGuideLine();
        assertEquals(ground.size(), guide.size());
        assertNotEquals(ground, guide, "FIT_TERRAIN guide should be terrain trend, not raw ground copy");

        double rawJump = maxAdjacentDelta(ground);
        double guideJump = maxAdjacentDelta(guide);
        assertTrue(guideJump < rawJump,
            () -> "guide trend jump " + guideJump + " should be smoother than raw jump " + rawJump);
    }

    @Test
    void fitTerrainReportsInfeasibleManualEndpointConstraints() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("infeasible-end");
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(40, 0));
        RoadEdge edge = network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0)),
            road.getId());
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setMaxSlope(8.0f);

        List<PathSegment> segments = sampledSegments(new Vec2d(0, 0), new Vec2d(40, 0), 20.0);
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setMaxSlope(8.0f);
        ProfileSolveSupport support = ProfileSolveSupport.fromConfig(config, ignored -> 1.0);
        ProfileSolveResult result = RoadProfileSolver.solveForEdge(
            segments, new FlatTerrainSampler(60), network, edge, config, 2.5, 58, 80, support);

        assertFalse(result.manualEndpointConstraintFeasible());
        assertTrue(result.heightInfos().getLast().targetEnd < 80);
    }

    @Test
    void fitTerrainSmoothsSuddenTerrainStep() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("step-target");
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(100, 0));
        RoadEdge edge = network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)),
            road.getId());
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setMaxSlope(8.0f);

        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return point.x < 50.0 ? 60 : 75;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return false;
            }
        };

        List<PathSegment> segments = sampledSegments(new Vec2d(0, 0), new Vec2d(100, 0), 10.0);
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setMaxSlope(8.0f);
        ProfileSolveSupport support = ProfileSolveSupport.fromConfig(config, ignored -> 1.0);
        ProfileSolveResult result = RoadProfileSolver.solveForEdge(
            segments, terrain, network, edge, config, 2.5, null, null, support);

        List<Integer> targets = result.profileTargetHeights();
        double maxStep = maxAdjacentDelta(targets);
        assertTrue(maxStep < 15.0,
            () -> "FIT_TERRAIN target should spread the 15 m terrain step, got jump " + maxStep);
        assertTrue(maxStep <= 2.0,
            () -> "segment grade limit should keep per-station rise near slope budget, got " + maxStep);
        assertTrue(countLongFlatRuns(targets, 3) == 0,
            "target profile should not contain long flat runs before a sudden jump");
    }

    private static int countLongFlatRuns(List<Integer> elevations, int minRunLength) {
        int longest = 0;
        int current = 1;
        for (int i = 1; i < elevations.size(); i++) {
            if (elevations.get(i).equals(elevations.get(i - 1))) {
                current++;
            } else {
                longest = Math.max(longest, current);
                current = 1;
            }
        }
        longest = Math.max(longest, current);
        return longest >= minRunLength ? longest : 0;
    }

    private static double maxAdjacentDelta(List<Integer> elevations) {
        double max = 0.0;
        for (int i = 1; i < elevations.size(); i++) {
            max = Math.max(max, Math.abs(elevations.get(i) - elevations.get(i - 1)));
        }
        return max;
    }

    private static double meanAbsoluteError(List<Integer> targets, List<Integer> ground) {
        double sum = 0.0;
        int count = Math.min(targets.size(), ground.size());
        for (int i = 0; i < count; i++) {
            sum += Math.abs(targets.get(i) - ground.get(i));
        }
        return count > 0 ? sum / count : Double.POSITIVE_INFINITY;
    }

    private static List<PathSegment> sampledSegments(Vec2d start, Vec2d end, double step) {
        List<PathSegment> result = new ArrayList<>();
        Vec2d delta = end.subtract(start);
        int count = Math.max(1, (int) Math.round(start.distance(end) / step));
        for (int i = 0; i < count; i++) {
            result.add(new PathSegment(
                start.add(delta.multiply((double) i / count)),
                start.add(delta.multiply((double) (i + 1) / count))));
        }
        return result;
    }
}
