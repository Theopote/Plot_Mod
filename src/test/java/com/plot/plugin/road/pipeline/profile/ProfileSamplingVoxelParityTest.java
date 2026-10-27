package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadTestFixtures;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileSamplingVoxelParityTest {

    @Test
    void manualProfileSamplingBuildSamplesMatchVoxelCompile() {
        RoadNetwork network = manualStraightRoad(80.0, 72.0);
        assertSamplingMatchesCompile(network, "sampling-parity-manual", 2, 78);
    }

    @Test
    void slopedEditedProfileSamplingMatchesVoxelCompile() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("slope-parity");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(40.0, 72.0),
            PointOfVerticalIntersection.of(80.0, 64.0))));
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(80, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());
        assertSamplingMatchesCompile(network, "sampling-parity-slope", 5, 75);
    }

    private static void assertSamplingMatchesCompile(
            RoadNetwork network,
            String configId,
            double minStation,
            double maxStation) {
        Road road = network.getRoads().values().iterator().next();
        RoadTestFixtures.confirmEditedProfileGeometry(network, road);

        RoadSystemConfig config = baseConfig(configId);
        RoadGenerator generator = generator(config);
        FlatTerrainSampler terrain = new FlatTerrainSampler(64);

        VoxelProfileCompileResult compile = RoadVoxelProfileCompiler.compile(
            network,
            road,
            config,
            generator,
            terrain,
            Map.of(),
            ProfileCompileOptions.defaults(),
            null,
            road.getVerticalAlignment());
        assertTrue(compile.feasible());

        RoadNetworkGenerator networkGenerator = new RoadNetworkGenerator(generator);
        RoadNetworkGenerator.ProfileSamplingResult sampling =
            networkGenerator.calculateProfileSampling(network, terrain);
        RoadGenerationResult samplingEdge = sampling.edgeResults().values().iterator().next();

        assertFalse(samplingEdge.profileBuildSamples.isEmpty());
        for (BuildHeightSample sample : compile.samples()) {
            if (sample.station() < minStation || sample.station() > maxStation) {
                continue;
            }
            int buildY = nearestBuildSampleY(samplingEdge, sample.station());
            assertEquals(sample.buildY(), buildY,
                "sampling vs compile at station " + sample.station());
        }
    }

    private static int nearestBuildSampleY(RoadGenerationResult edge, double station) {
        List<BuildHeightSample> samples = edge.profileBuildSamples;
        BuildHeightSample best = samples.getFirst();
        double bestDist = Math.abs(best.station() - station);
        for (BuildHeightSample candidate : samples) {
            double dist = Math.abs(candidate.station() - station);
            if (dist < bestDist) {
                best = candidate;
                bestDist = dist;
            }
        }
        return best.buildY();
    }

    private static RoadNetwork manualStraightRoad(double length, double elevation) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("parity");
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, elevation),
            PointOfVerticalIntersection.of(length, elevation))));
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(length, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());
        return network;
    }

    private static RoadSystemConfig baseConfig(String id) {
        RoadSystemConfig config = new RoadSystemConfig(id);
        config.setRoadWidth(5);
        config.setIncludeShoulder(false);
        config.setIncludeSlopeBatter(false);
        config.setMaxSlope(40.0f);
        return config;
    }

    private static RoadGenerator generator(RoadSystemConfig config) {
        return new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
    }
}
