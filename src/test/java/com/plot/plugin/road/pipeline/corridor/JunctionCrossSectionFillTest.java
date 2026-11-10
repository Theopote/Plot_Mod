package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionCrossSectionFillTest {

    @Test
    void junctionFillIncludesShoulderBandsAndSlopeSubgrade() {
        // FOLLOW：bridgeThreshold=6，填方高度可观测；路口边坡仅从圆角外弧向外生成。
        RoadSystemConfig config = new RoadSystemConfig("junction-fill");
        config.setTerrainStyle(RoadTerrainStyle.FOLLOW);
        config.setIncludeShoulder(true);
        config.setShoulderWidth(1);
        config.setIncludeSlopeBatter(true);
        config.setFillSlopeRatio(1.5f);
        config.setCutSlopeRatio(1.0f);
        config.setDefaultCornerRadius(6.0f);
        config.setRoadWidth(7);
        config.setMaxSlope(100.0f);

        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadJunctionGenerator junctionGenerator = new RoadJunctionGenerator(generator);
        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 64;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y <= 64;
            }
        };

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        road.setWidth(7);
        road.setIncludeSlopeBatter(true);
        var junction = network.createNode(new Vec2d(0, 0));
        junction.setCornerRadius(6.0);
        var north = network.createNode(new Vec2d(0, 40));
        var east = network.createNode(new Vec2d(40, 0));
        var west = network.createNode(new Vec2d(-40, 0));
        network.createEdge(junction.getId(), north.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, 40)), road.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0)), road.getId());
        network.createEdge(junction.getId(), west.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(-40, 0)), road.getId());

        // 填方高度 4 ≤ FOLLOW bridgeThreshold(6)，圆角外弧应写出 SUBGRADE。
        var blocks = junctionGenerator.generateJunction(
            junction, network, terrain, Map.of(junction.getId(), 68));
        var primitives = blocks.getSolids().primitives();

        assertFalse(primitives.isEmpty());
        assertTrue(primitives.stream().anyMatch(p -> p.layer() == RoadSolidLayer.SHOULDER),
            "junction fill should stamp shoulder surface bands");
        assertTrue(primitives.stream().anyMatch(p -> p.layer() == RoadSolidLayer.SUBGRADE),
            "junction fill should stamp fillet-arc slope subgrade beyond shoulders");
    }
}
