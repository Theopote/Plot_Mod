package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import com.plot.plugin.road.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadUniformElevationUtilsTest {

    @Test
    void recommendElevationPrefersUniqueMode() {
        List<Integer> samples = List.of(70, 70, 70, 70, 65, 65, 80);
        var result = RoadUniformElevationUtils.recommendElevation(samples);

        assertEquals(70, result.elevation());
        assertTrue(result.usedMode());
        assertEquals(7, result.sampleCount());
    }

    @Test
    void recommendElevationFallsBackToAverageWhenNoMode() {
        List<Integer> samples = List.of(60, 62, 64, 66, 68);
        var result = RoadUniformElevationUtils.recommendElevation(samples);

        assertFalse(result.usedMode());
        assertEquals(64, result.elevation()); // round(mean=64)
    }

    @Test
    void recommendElevationAllSameUsesMode() {
        List<Integer> samples = List.of(72, 72, 72);
        var result = RoadUniformElevationUtils.recommendElevation(samples);

        assertEquals(72, result.elevation());
        assertTrue(result.usedMode());
    }

    @Test
    void recommendElevationEmptyUsesSeaLevel() {
        var result = RoadUniformElevationUtils.recommendElevation(List.of());
        assertEquals(TerrainSampler.DEFAULT_SEA_LEVEL, result.elevation());
        assertEquals(0, result.sampleCount());
    }

    @Test
    void multimodalTiePicksModeClosestToAverage() {
        // 70 appears twice, 80 appears twice; mean=75 → closer is either equally 5 away, first wins by scan
        // 70,70,80,80,75 → mean=75, modes 70 and 80 both count 2
        List<Integer> samples = List.of(70, 70, 80, 80, 75);
        var result = RoadUniformElevationUtils.recommendElevation(samples);

        assertTrue(result.usedMode());
        // both 70 and 80 are distance 5 from 75; stable pick is whichever is first at min dist
        assertTrue(result.elevation() == 70 || result.elevation() == 80);
    }

    @Test
    void sampleNetworkOnFlatTerrainReturnsThatHeight() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setPathSampleDistance(2.0);
        RoadNetwork network = new RoadNetwork();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(20, 0));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(20, 0)));

        List<Integer> samples = RoadUniformElevationUtils.sampleNetworkGroundHeights(
            network, new FlatTerrainSampler(71), config);

        assertFalse(samples.isEmpty());
        assertTrue(samples.stream().allMatch(h -> h == 71));

        var recommendation = RoadUniformElevationUtils.recommendElevation(samples);
        assertEquals(71, recommendation.elevation());
    }

    @Test
    void sampleNetworkDedupesJunctionEndpointsAcrossEdges() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        // 间距大于边长时仅采起终点；上限为 MAX_PATH_SAMPLE_DISTANCE
        config.setPathSampleDistance(RoadParameterLimits.MAX_PATH_SAMPLE_DISTANCE);
        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode a = network.createNode(new Vec2d(4, 0));
        RoadNode b = network.createNode(new Vec2d(0, 4));
        network.createEdge(
            junction.getId(), a.getId(), List.of(new Vec2d(0, 0), new Vec2d(4, 0)));
        network.createEdge(
            junction.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 4)));

        List<Integer> samples = RoadUniformElevationUtils.sampleNetworkGroundHeights(
            network, new FlatTerrainSampler(66), config);

        // 路口 (0,0) 只计一次：两条边 × 两端 = 3 个唯一点（0,0)/(4,0)/(0,4）
        assertEquals(3, samples.size());
    }

    @Test
    void sampleNetworkReflectsVaryingTerrainMode() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setPathSampleDistance(1.0);
        RoadNetwork network = new RoadNetwork();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)));

        TerrainSampler stepped = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                // most of the path at 65, a short stretch at 90
                return planPoint.x < 8 ? 65 : 90;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return true;
            }
        };

        var recommendation = RoadUniformElevationUtils.recommendForNetwork(network, stepped, config);
        assertEquals(65, recommendation.elevation());
        assertTrue(recommendation.usedMode());
    }
}
