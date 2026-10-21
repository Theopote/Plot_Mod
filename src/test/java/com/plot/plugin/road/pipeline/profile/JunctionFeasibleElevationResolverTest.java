package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionFeasibleElevationResolverTest {

    @Test
    void resolveFeasibleHeightClampsPreferredIntoIntersection() {
        List<JunctionFeasibleElevationResolver.ApproachConstraint> constraints = List.of(
            new JunctionFeasibleElevationResolver.ApproachConstraint("north", 100.0, 8f),
            new JunctionFeasibleElevationResolver.ApproachConstraint("east", 100.0, 8f));
        Map<String, Integer> heights = Map.of(
            "north", 64,
            "east", 80);

        int resolved = JunctionFeasibleElevationResolver.resolveFeasibleHeight(72, constraints, heights);
        assertEquals(72, resolved);
    }

    @Test
    void resolveFeasibleHeightCompromisesWhenRangesDoNotOverlap() {
        List<JunctionFeasibleElevationResolver.ApproachConstraint> constraints = List.of(
            new JunctionFeasibleElevationResolver.ApproachConstraint("north", 20.0, 8f),
            new JunctionFeasibleElevationResolver.ApproachConstraint("east", 20.0, 8f));
        Map<String, Integer> heights = Map.of(
            "north", 64,
            "east", 80);

        int resolved = JunctionFeasibleElevationResolver.resolveFeasibleHeight(72, constraints, heights);
        assertTrue(resolved >= 66 && resolved <= 78,
            "compromise height should sit between conflicting bounds, got " + resolved);
    }

    @Test
    void refineAdaptiveCrossingReducesJunctionSpreadWithoutManualElevation() {
        RoadNetwork network = new RoadNetwork();
        Road main = network.createRoad("main");
        Road cross = network.createRoad("cross");
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 80));
        RoadNode south = network.createNode(new Vec2d(0, -80));
        RoadNode east = network.createNode(new Vec2d(80, 0));
        network.createEdge(junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), main.getId());
        network.createEdge(junction.getId(), south.getId(),
            List.of(junction.getPosition(), south.getPosition()), main.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(junction.getPosition(), east.getPosition()), cross.getId());

        TerrainSampler sloped = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                if (point.x > 8) {
                    return 80;
                }
                return 64;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= sampleSurfaceY(new Vec2d(x, z));
            }
        };

        RoadSystemConfig config = new RoadSystemConfig("adaptive-crossing");
        RoadNetworkGenerator generator = new RoadNetworkGenerator(new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance()));
        RoadNetworkGenerator.PreviewResult preview = generator.generatePreview(network, sloped);

        int junctionY = preview.nodeElevations().get(junction.getId());
        assertTrue(junctionY >= 68 && junctionY <= 76,
            "junction should compromise between 64 and 80 terrain arms, got " + junctionY);
    }

    @Test
    void refinePullsJunctionTowardFeasibleBand() {
        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 80));
        RoadNode east = network.createNode(new Vec2d(80, 0));

        Map<String, List<Integer>> samples = Map.of(
            junction.getId(), List.of(70, 78));
        Map<String, List<JunctionFeasibleElevationResolver.ApproachConstraint>> constraints = Map.of(
            junction.getId(), List.of(
                new JunctionFeasibleElevationResolver.ApproachConstraint(north.getId(), 100.0, 8f),
                new JunctionFeasibleElevationResolver.ApproachConstraint(east.getId(), 100.0, 8f)));
        Map<String, Integer> initial = new LinkedHashMap<>();
        initial.put(junction.getId(), 74);
        initial.put(north.getId(), 64);
        initial.put(east.getId(), 80);

        Map<String, Integer> refined = JunctionFeasibleElevationResolver.refine(
            network, samples, constraints, initial);

        int junctionY = refined.get(junction.getId());
        assertTrue(junctionY >= 64 && junctionY <= 80, "refined junction Y=" + junctionY);
        assertTrue(Math.abs(junctionY - 72) <= 4, "should stay near feasible midpoint, got " + junctionY);
    }
}
