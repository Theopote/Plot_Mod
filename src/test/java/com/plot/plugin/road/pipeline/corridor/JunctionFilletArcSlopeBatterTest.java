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
 * 倒圆外弧应向外打边坡：起伏地形下弧外象限出现 SUBGRADE，且不落在锐角凹口内侧。
 */
class JunctionFilletArcSlopeBatterTest {

    @Test
    void filletOuterArcStampsFillBatterBeyondCurbReturn() {
        RoadSystemConfig config = new RoadSystemConfig("fillet-arc-slope");
        // FOLLOW：bridgeThreshold=6，允许可观测填方高度；SMOOTH(=2) 会 suppressFillBatter。
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
        // 填方高度 4 ≤ FOLLOW bridgeThreshold(6)，否则 emit 会整段跳过边坡
        junction.setManualElevation(64.0);
        junction.setCornerRadius(6.0);
        RoadNode n = network.createNode(new Vec2d(0, 40));
        RoadNode s = network.createNode(new Vec2d(0, -40));
        RoadNode e = network.createNode(new Vec2d(40, 0));
        RoadNode w = network.createNode(new Vec2d(-40, 0));
        network.createEdge(junction.getId(), n.getId(),
            List.of(junction.getPosition(), n.getPosition()), ns.getId());
        network.createEdge(junction.getId(), s.getId(),
            List.of(junction.getPosition(), s.getPosition()), ns.getId());
        network.createEdge(junction.getId(), e.getId(),
            List.of(junction.getPosition(), e.getPosition()), ew.getId());
        network.createEdge(junction.getId(), w.getId(),
            List.of(junction.getPosition(), w.getPosition()), ew.getId());

        TerrainSampler lowTerrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 60;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= 60;
            }
        };

        RoadJunctionGenerator junctionGenerator = new RoadJunctionGenerator(
            new RoadGenerator(config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance()));
        var blocks = junctionGenerator.generateJunction(
            junction, network, lowTerrain, Map.of(junction.getId(), 64));

        int outerNeSubgrade = 0;
        int innerPocketSubgrade = 0;
        for (var primitive : blocks.getSolids().primitives()) {
            if (primitive.layer() != RoadSolidLayer.SUBGRADE) {
                continue;
            }
            double x = primitive.planPoint().x;
            double z = primitive.planPoint().y;
            // 弧外草地象限（路缘外扩之外）
            if (x >= 5.0 && z >= 5.0 && x <= 14.0 && z <= 14.0) {
                outerNeSubgrade++;
            }
            // 锐角凹口内侧（应是铺装 gore，不应靠弧外坡堆起）
            if (x >= 1.5 && z >= 1.5 && x <= 3.0 && z <= 3.0) {
                innerPocketSubgrade++;
            }
        }

        assertTrue(outerNeSubgrade >= 4,
            "fillet arc outer NE quadrant must get fill batter SUBGRADE; outer=" + outerNeSubgrade);
        assertTrue(outerNeSubgrade > innerPocketSubgrade,
            "batter mass should sit outside the curb arc, not in the sharp pocket; outer="
                + outerNeSubgrade + " inner=" + innerPocketSubgrade);
    }
}
