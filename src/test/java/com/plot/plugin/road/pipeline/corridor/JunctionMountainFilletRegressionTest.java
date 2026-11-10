package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 山地平交圆角：低地填方截断后应有挡墙/坡体接到地面，避免只剩半坡堆块。
 */
class JunctionMountainFilletRegressionTest {

    @Test
    void hillsideFilletEmitsSupportToLowGround() {
        RoadSystemConfig config = new RoadSystemConfig("mountain-fillet");
        config.setTerrainStyle(RoadTerrainStyle.FOLLOW);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setIncludeSlopeBatter(true);
        config.setFillSlopeRatio(1.5f);
        config.setCutSlopeRatio(1.0f);
        config.setDefaultCornerRadius(6.0f);
        config.setRoadWidth(7);
        config.setSelectedMaterial("minecraft:white_concrete");
        config.setMaxSlope(100.0f);

        RoadNetwork network = new RoadNetwork();
        Road ns = network.createRoad("ns");
        ns.setWidth(7);
        ns.setIncludeSlopeBatter(true);
        Road ew = network.createRoad("ew");
        ew.setWidth(7);
        ew.setIncludeSlopeBatter(true);

        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setManualElevation(64.0);
        junction.setCornerRadius(6.0);
        for (Vec2d p : List.of(new Vec2d(0, 40), new Vec2d(0, -40), new Vec2d(40, 0), new Vec2d(-40, 0))) {
            RoadNode leaf = network.createNode(p);
            leaf.setManualElevation(64.0);
            Road road = Math.abs(p.x) > Math.abs(p.y) ? ew : ns;
            network.createEdge(junction.getId(), leaf.getId(),
                List.of(junction.getPosition(), leaf.getPosition()), road.getId());
        }

        // 路口周边低地：大填方，半径跑距易截断
        TerrainSampler valley = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 52;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= 52;
            }
        };

        var blocks = new RoadJunctionGenerator(
            new RoadGenerator(config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance()))
            .generateJunction(junction, network, valley, Map.of(junction.getId(), 64));

        long support = 0;
        long floatingHigh = 0;
        for (var primitive : blocks.getSolids().primitives()) {
            if (primitive.layer() != RoadSolidLayer.SUBGRADE
                    && primitive.layer() != RoadSolidLayer.RETAINING_WALL) {
                continue;
            }
            double x = primitive.planPoint().x;
            double z = primitive.planPoint().y;
            if (x < 4.0 || z < 4.0 || x > 16.0 || z > 16.0) {
                continue;
            }
            support++;
            // 无挡墙时，孤立高位 SUBGRADE（远离地面）视为半坡悬空风险信号
            if (primitive.layer() == RoadSolidLayer.SUBGRADE && primitive.elevation() >= 60) {
                floatingHigh++;
            }
        }
        long walls = blocks.getSolids().primitives().stream()
            .filter(p -> p.layer() == RoadSolidLayer.RETAINING_WALL)
            .count();

        assertTrue(support >= 4, "NE fillet quadrant needs fill/retaining support; support=" + support);
        assertTrue(walls > 0 || floatingHigh == 0,
            "truncated hillside fillet must retain or avoid floating high subgrade; walls="
                + walls + " floatingHigh=" + floatingHigh);
    }
}
