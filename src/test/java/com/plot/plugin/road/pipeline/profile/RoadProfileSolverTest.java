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
        assertFalse(fitTargets.isEmpty());
        assertEquals(fitTargets.size(), ground.size());

        int mid = fitTargets.size() / 2;
        assertNotEquals(fitTargets.getFirst(), fitTargets.get(mid),
            "FIT_TERRAIN should vary along interior stations on undulating terrain");

        double fitGroundError = meanAbsoluteError(fitTargets, ground);
        double smoothGroundError = meanAbsoluteError(smooth.profileTargetHeights(), smooth.profileGroundHeights());
        assertTrue(fitGroundError < smoothGroundError,
            () -> "FIT_TERRAIN should track terrain more closely than AUTO_SMOOTH");
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
