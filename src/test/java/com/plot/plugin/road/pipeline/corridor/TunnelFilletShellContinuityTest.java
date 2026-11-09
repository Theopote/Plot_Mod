package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 隧道圆角壳：深覆土下应写入侧墙/顶衬，洞腔内空气，而非仅删除旧衬砌。
 */
class TunnelFilletShellContinuityTest {

    @Test
    void shellPassPlacesRoofLiningAboveFilletCavityUnderMountainCover() {
        RoadNetwork network = tunnelCrossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        for (var edge : network.getEdges().values()) {
            RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
            result.constructionTypes.add(RoadConstructionType.TUNNEL);
            edgeResults.put(edge.getId(), result);
        }

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
            public boolean isChunkLoaded(int x, int z) {
                return true;
            }

            @Override
            public boolean isChunkLoaded(Vec2d planPoint) {
                return true;
            }
        };

        RoadSystemConfig config = new RoadSystemConfig("tunnel-fillet-shell");
        config.setDefaultCornerRadius(4.0f);
        TunnelJunctionShellPass.apply(
            network,
            edgeResults,
            Map.of(junction.getId(), 64),
            config,
            IdentityCoordinateService.INSTANCE,
            (path, segments) -> 1.0,
            mountain,
            material -> material == null || material.isBlank() ? "minecraft:stone_bricks" : material,
            pos -> "minecraft:stone");

        boolean foundRoofLining = false;
        boolean foundCavityAir = false;
        for (RoadGenerationResult result : edgeResults.values()) {
            for (Map.Entry<BlockPos, BlockRecord> entry : result.placementRecords.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockRecord record = entry.getValue();
                if (record == null) {
                    continue;
                }
                RoadSolidLayer layer = result.placementLayers.get(pos);
                Integer priority = result.placementMaterialPriorities.get(pos);
                if (layer == RoadSolidLayer.TUNNEL
                        && priority != null
                        && priority >= RoadSolidMaterialPriority.STRUCTURAL
                        && !"minecraft:air".equals(record.newBlockId)
                        && pos.getY() >= 64) {
                    foundRoofLining = true;
                }
                if ("minecraft:air".equals(record.newBlockId)
                        && pos.getY() > 64
                        && pos.getY() < 80) {
                    foundCavityAir = true;
                }
            }
        }
        assertTrue(foundRoofLining, "fillet shell must place structural lining under mountain cover");
        assertTrue(foundCavityAir, "fillet shell must excavate cavity air inside design volume");
    }

    private static RoadNetwork tunnelCrossNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("tunnelA");
        Road roadB = network.createRoad("tunnelB");
        TunnelStyle style = new TunnelStyle();
        style.setClearHeight(5);
        style.setSideClearance(1);
        style.setLiningThickness(1);
        style.setLiningMaterial("minecraft:stone_bricks");
        roadA.setTunnelStyle(style);
        roadB.setTunnelStyle(style);

        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode n = network.createNode(new Vec2d(0, 40));
        RoadNode s = network.createNode(new Vec2d(0, -40));
        RoadNode e = network.createNode(new Vec2d(40, 0));
        RoadNode w = network.createNode(new Vec2d(-40, 0));
        network.createEdge(junction.getId(), n.getId(), List.of(junction.getPosition(), n.getPosition()), roadA.getId());
        network.createEdge(junction.getId(), s.getId(), List.of(junction.getPosition(), s.getPosition()), roadA.getId());
        network.createEdge(junction.getId(), e.getId(), List.of(junction.getPosition(), e.getPosition()), roadB.getId());
        network.createEdge(junction.getId(), w.getId(), List.of(junction.getPosition(), w.getPosition()), roadB.getId());
        return network;
    }
}
