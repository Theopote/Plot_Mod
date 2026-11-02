package com.plot.plugin.road.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadWorldTerrainProbeTest {

    @Test
    void geometryFingerprintIgnoresSurfaceHeights() {
        RoadNetwork network = straightRoad();
        long before = RoadWorldTerrainProbe.geometryFingerprint(network);
        // Moving a node changes geometry.
        network.getNodes().values().iterator().next().setPosition(new Vec2d(1, 0));
        long afterMove = RoadWorldTerrainProbe.geometryFingerprint(network);
        assertNotEquals(before, afterMove);
    }

    @Test
    void surfaceFingerprintChangesWhenMidspanTerrainChangesButEndsStay() {
        RoadNetwork network = straightRoad();
        RoadSystemConfig config = new RoadSystemConfig("probe");
        config.setPathSampleDistance(5.0);

        Map<String, Integer> heights = new HashMap<>();
        // Flat corridor at Y=64
        for (int x = 0; x <= 40; x++) {
            heights.put(key(x, 0), 64);
        }
        MutableTerrain terrain = new MutableTerrain(heights);

        long before = RoadWorldTerrainProbe.surfaceFingerprint(network, terrain, config);

        // Raise a mid-span mound; endpoints (0,0) and (40,0) stay 64
        heights.put(key(20, 0), 72);
        long after = RoadWorldTerrainProbe.surfaceFingerprint(network, terrain, config);

        assertNotEquals(before, after, "mid-span terrain change must alter surface fingerprint");
        assertEquals(64, terrain.sampleSurfaceY(new Vec2d(0, 0)));
        assertEquals(64, terrain.sampleSurfaceY(new Vec2d(40, 0)));
    }

    @Test
    void probeStationsIncludeInteriorPointsNotOnlyNodes() {
        RoadNetwork network = straightRoad();
        List<Vec2d> stations = RoadWorldTerrainProbe.collectProbeStations(network, 10.0, 512);
        assertTrue(stations.size() >= 5, "expected densified stations, got " + stations.size());
        boolean hasInterior = stations.stream().anyMatch(p -> Math.abs(p.x - 20) < 1e-6);
        assertTrue(hasInterior, "mid-span station missing: " + stations);
    }

    @Test
    void probeStationsRespectMaxCap() {
        RoadNetwork network = straightRoad();
        List<Vec2d> stations = RoadWorldTerrainProbe.collectProbeStations(network, 1.0, 8);
        assertEquals(8, stations.size());
    }

    private static RoadNetwork straightRoad() {
        RoadNetwork network = new RoadNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(40, 0));
        Road road = network.createRoad("r1");
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(40, 0)), road.getId());
        return network;
    }

    private static String key(int x, int z) {
        return x + "," + z;
    }

    private static final class MutableTerrain implements TerrainSampler {
        private final Map<String, Integer> heights;

        MutableTerrain(Map<String, Integer> heights) {
            this.heights = heights;
        }

        @Override
        public int sampleSurfaceY(Vec2d planPoint) {
            int x = (int) Math.round(planPoint.x);
            int z = (int) Math.round(planPoint.y);
            return heights.getOrDefault(key(x, z), 64);
        }

        @Override
        public boolean isSolidBlock(int worldX, int y, int worldZ) {
            return y <= heights.getOrDefault(key(worldX, worldZ), 64);
        }
    }
}
