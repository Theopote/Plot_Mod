package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadCrossingReconciler;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Crossing 上设置的圆角半径必须进入 Minecraft 生成（不只画布预览）。
 */
class JunctionCrossingCornerRadiusVoxelTest {

    @Test
    void atGradeCrossingCornerRadiusStampsCurbReturnPavement() {
        RoadSystemConfig config = new RoadSystemConfig("crossing-fillet-gen");
        config.setTerrainStyle(RoadTerrainStyle.SMOOTH);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setDefaultCornerRadius(0.0f);
        config.setRoadWidth(6);
        config.setSelectedMaterial("minecraft:white_concrete");

        RoadNetwork network = new RoadNetwork();
        var builder = new com.plot.plugin.road.RoadNetworkBuilder();
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, 5), new Vec2d(40, 5)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(20, 0), new Vec2d(20, 40)), false), config);
        RoadCrossingReconciler.reconcileCrossings(network);
        assertFalse(network.getCrossings().isEmpty());

        RoadCrossing crossing = network.getCrossings().values().iterator().next();
        network.setCrossingCornerRadius(crossing.id(), 6.0);

        TerrainSampler flat = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 64;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= 64;
            }
        };

        RoadNetworkGenerator.PreviewResult preview = new RoadNetworkGenerator(
            new RoadGenerator(config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance()))
            .generatePreview(network, flat);

        assertFalse(preview.networkResult().getJunctionResults().isEmpty(),
            "materialized crossing must generate a junction plaza");

        long curbReturnCells = 0;
        for (var blocks : preview.networkResult().getJunctionResults().values()) {
            if (blocks.getSolids() == null) {
                continue;
            }
            for (var primitive : blocks.getSolids().primitives()) {
                if (primitive.layer() != RoadSolidLayer.ROAD || primitive.elevation() != 64) {
                    continue;
                }
                double lx = primitive.planPoint().x - crossing.position().x;
                double lz = primitive.planPoint().y - crossing.position().y;
                // 街角外扩：两轴都离开核区一定距离
                if (Math.abs(lx) >= 3.0 && Math.abs(lz) >= 3.0
                        && Math.abs(lx) <= 9.0 && Math.abs(lz) <= 9.0) {
                    curbReturnCells++;
                }
            }
        }
        assertTrue(curbReturnCells >= 4,
            "Crossing R=6 must stamp curb-return ROAD cells; found=" + curbReturnCells);
    }
}
