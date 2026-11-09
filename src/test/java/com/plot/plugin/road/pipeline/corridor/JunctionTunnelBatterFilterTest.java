package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 隧道进口不得从广场中心向外打开敞路肩边坡。
 */
class JunctionTunnelBatterFilterTest {

    @Test
    void tunnelOnlyJunctionDoesNotStampOpenShoulderBatters() {
        RoadSystemConfig config = new RoadSystemConfig("tunnel-batter-filter");
        config.setTerrainStyle(RoadTerrainStyle.SMOOTH);
        config.setIncludeSlopeBatter(true);
        config.setFillSlopeRatio(1.5f);
        config.setCutSlopeRatio(1.0f);
        config.setDefaultCornerRadius(4.0f);
        config.setRoadWidth(7);
        config.setMaxSlope(100.0f);

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("tunnel");
        road.setWidth(7);
        road.setIncludeSlopeBatter(true);
        TunnelStyle style = new TunnelStyle();
        style.setClearHeight(5);
        style.setSideClearance(1);
        style.setLiningThickness(1);
        road.setTunnelStyle(style);

        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setManualElevation(64.0);
        junction.setCornerRadius(4.0);
        RoadNode n = network.createNode(new Vec2d(0, 40));
        RoadNode s = network.createNode(new Vec2d(0, -40));
        RoadNode e = network.createNode(new Vec2d(40, 0));
        RoadNode w = network.createNode(new Vec2d(-40, 0));
        network.createEdge(junction.getId(), n.getId(),
            List.of(junction.getPosition(), n.getPosition()), road.getId());
        network.createEdge(junction.getId(), s.getId(),
            List.of(junction.getPosition(), s.getPosition()), road.getId());
        network.createEdge(junction.getId(), e.getId(),
            List.of(junction.getPosition(), e.getPosition()), road.getId());
        network.createEdge(junction.getId(), w.getId(),
            List.of(junction.getPosition(), w.getPosition()), road.getId());

        TerrainSampler mountain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 80;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= 80;
            }

            @Override
            public boolean isChunkLoaded(Vec2d planPoint) {
                return true;
            }
        };

        // 无边结果时 collectTunnelApproaches 为空 → 仍会打边坡。
        // 直接调用 stampApproachSlopeBatters 并传入全部边为隧道进口。
        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        var materials = new JunctionCrossSectionFill.MaterialResolver() {
            @Override
            public String resolve(String materialId) {
                return generator.getBlockIdFromMaterial(materialId);
            }

            @Override
            public net.minecraft.util.math.BlockPos canvasToBlockPos(Vec2d planPoint) {
                return generator.toBlockPos(planPoint, 0);
            }
        };
        var columnResolver = new com.plot.plugin.road.RoadTerrainClearanceUtils.BlockColumnResolver() {
            @Override
            public int worldX(Vec2d planPoint) {
                return generator.toBlockPos(planPoint, 0).getX();
            }

            @Override
            public int worldZ(Vec2d planPoint) {
                return generator.toBlockPos(planPoint, 0).getZ();
            }
        };

        var solids = new com.plot.plugin.road.solid.RoadSolidModel();
        Set<String> allEdges = network.getEdges().keySet();
        JunctionCrossSectionFill.stampApproachSlopeBatters(
            solids,
            junction.getPosition(),
            64,
            junction.getId(),
            network.getEdges().values().stream().toList(),
            network,
            config,
            materials,
            columnResolver,
            1.0,
            mountain,
            8,
            null,
            java.util.List.of(),
            null,
            allEdges);

        long subgrade = solids.primitives().stream()
            .filter(p -> p.layer() == RoadSolidLayer.SUBGRADE)
            .count();
        assertEquals(0L, subgrade, "tunnel approach edges must not emit open shoulder batters");
        assertTrue(allEdges.size() >= 4);
    }
}
