package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionApproachElevationTest {

    @Test
    void atGradeApproachesMeetPlazaWithoutYCliff() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setManualElevation(64.0);
        RoadNode north = network.createNode(new Vec2d(0, 40));
        north.setManualElevation(64.0);
        RoadNode south = network.createNode(new Vec2d(0, -40));
        south.setManualElevation(64.0);
        RoadNode east = network.createNode(new Vec2d(40, 0));
        east.setManualElevation(80.0);
        network.createEdge(junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), road.getId());
        network.createEdge(junction.getId(), south.getId(),
            List.of(junction.getPosition(), south.getPosition()), road.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(junction.getPosition(), east.getPosition()), road.getId());

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

        RoadSystemConfig config = new RoadSystemConfig("approach-cliff");
        RoadNetworkGenerator generator = new RoadNetworkGenerator(new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance()));
        RoadNetworkGenerator.PreviewResult preview = generator.generatePreview(network, sloped);

        int plazaY = preview.nodeElevations().get(junction.getId());
        assertEquals(64, plazaY);

        int minApproachY = Integer.MAX_VALUE;
        int maxApproachY = Integer.MIN_VALUE;
        for (var edgeResult : preview.edgeResults().values()) {
            for (var entry : edgeResult.placementRecords.entrySet()) {
                var pos = entry.getKey();
                if (edgeResult.placementLayers.get(pos) != RoadSolidLayer.ROAD) {
                    continue;
                }
                if ("minecraft:air".equals(entry.getValue().newBlockId)) {
                    continue;
                }
                double dist = Math.hypot(pos.getX(), pos.getZ());
                if (dist < 8.0 || dist > 16.0) {
                    continue;
                }
                minApproachY = Math.min(minApproachY, pos.getY());
                maxApproachY = Math.max(maxApproachY, pos.getY());
            }
        }
        assertTrue(minApproachY != Integer.MAX_VALUE, "must find approach ROAD near plaza");
        assertTrue(Math.abs(minApproachY - plazaY) <= 2,
            "approach ROAD nearest the plaza must meet plaza Y, plaza=" + plazaY
                + " approach=" + minApproachY + ".." + maxApproachY);
        assertTrue(maxApproachY - minApproachY <= 4,
            "approach band must not cliff, " + minApproachY + ".." + maxApproachY);
    }
}
