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
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 隧道倒圆角顶衬必须跟随下方路面 loft 趋势，不能整片压成单一绝对标高。
 */
class TunnelFilletRoofFollowsPavementTest {

    private static final int CLEAR_HEIGHT = 5;
    private static final int LINING = 1;
    private static final int BASE_ROAD_Y = 64;
    private static final int HIGH_ROAD_Y = 70;

    @Test
    void roofLiningTracksLoftedPavementAcrossFilletColumns() {
        RoadNetwork network = tunnelCrossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        Map<String, RoadGenerationResult> edgeResults = tunnelEdgeResults(network);
        seedGradedApproachPavement(edgeResults);

        TerrainSampler mountain = surfaceTerrain(90);
        RoadSystemConfig config = new RoadSystemConfig("tunnel-roof-loft");
        config.setDefaultCornerRadius(6.0f);

        TunnelJunctionShellPass.apply(
            network,
            edgeResults,
            Map.of(junction.getId(), BASE_ROAD_Y),
            config,
            IdentityCoordinateService.INSTANCE,
            (path, segments) -> 1.0,
            mountain,
            material -> "minecraft:stone_bricks",
            pos -> "minecraft:stone");

        Set<Integer> roofYs = new HashSet<>();
        Set<Integer> roadYs = new HashSet<>();
        for (RoadGenerationResult result : edgeResults.values()) {
            for (Map.Entry<BlockPos, BlockRecord> entry : result.placementRecords.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockRecord record = entry.getValue();
                if (pos == null || record == null || "minecraft:air".equals(record.newBlockId)) {
                    continue;
                }
                RoadSolidLayer layer = result.placementLayers.get(pos);
                if (layer == RoadSolidLayer.ROAD) {
                    roadYs.add(pos.getY());
                }
                if (layer == RoadSolidLayer.TUNNEL
                        && !"minecraft:air".equals(record.newBlockId)
                        && pos.getY() > BASE_ROAD_Y + 1) {
                    // 顶衬落在路面上方 clearHeight 一带
                    if (pos.getY() >= BASE_ROAD_Y + CLEAR_HEIGHT - 1
                            && pos.getY() <= HIGH_ROAD_Y + CLEAR_HEIGHT + LINING) {
                        roofYs.add(pos.getY());
                    }
                }
            }
        }

        assertTrue(roadYs.size() >= 2, "seeded pavement must span multiple elevations, got " + roadYs);
        assertTrue(roofYs.size() >= 2,
            "roof lining must follow pavement trend (multiple roof Y); got roofYs=" + roofYs
                + " roadYs=" + roadYs);
        // 每条顶衬相对某条路面仍约等于 clearHeight
        boolean hasMatchingOffset = false;
        for (int roadY : roadYs) {
            if (roofYs.contains(roadY + CLEAR_HEIGHT)) {
                hasMatchingOffset = true;
                break;
            }
        }
        assertTrue(hasMatchingOffset,
            "at least one roof Y must equal roadY+clearHeight; roofYs=" + roofYs + " roadYs=" + roadYs);
    }

    private static void seedGradedApproachPavement(Map<String, RoadGenerationResult> edgeResults) {
        // 西向进口低、南向进口高：西南圆角 loft 应出现中间标高
        RoadGenerationResult any = edgeResults.values().iterator().next();
        for (int x = -18; x <= -4; x++) {
            for (int z = -2; z <= 2; z++) {
                plantRoad(any, x, BASE_ROAD_Y, z);
            }
        }
        for (int z = -18; z <= -4; z++) {
            for (int x = -2; x <= 2; x++) {
                plantRoad(any, x, HIGH_ROAD_Y, z);
            }
        }
        // 圆角 gore 附近也铺上两端标高，方便 loft 直接命中列索引
        plantRoad(any, -6, BASE_ROAD_Y, -2);
        plantRoad(any, -2, HIGH_ROAD_Y, -6);
        plantRoad(any, -5, BASE_ROAD_Y, -5);
        plantRoad(any, -4, HIGH_ROAD_Y, -4);
    }

    private static void plantRoad(RoadGenerationResult result, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        result.recordPlacementOverride(pos, "minecraft:air", "minecraft:stone");
        result.placementLayers.put(pos, RoadSolidLayer.ROAD);
    }

    private static Map<String, RoadGenerationResult> tunnelEdgeResults(RoadNetwork network) {
        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        for (var edge : network.getEdges().values()) {
            RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
            result.constructionTypes.add(RoadConstructionType.TUNNEL);
            edgeResults.put(edge.getId(), result);
        }
        return edgeResults;
    }

    private static RoadNetwork tunnelCrossNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("tunnelA");
        Road roadB = network.createRoad("tunnelB");
        TunnelStyle style = new TunnelStyle();
        style.setClearHeight(CLEAR_HEIGHT);
        style.setSideClearance(1);
        style.setLiningThickness(LINING);
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

    private static TerrainSampler surfaceTerrain(int surfaceY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return surfaceY;
            }

            @Override
            public boolean isSolidBlock(int x, int blockY, int z) {
                return blockY < surfaceY;
            }

            @Override
            public boolean isChunkLoaded(int x, int z) {
                return true;
            }

            @Override
            public boolean isChunkLoaded(Vec2d planPoint) {
                return true;
            }

            @Override
            public boolean isGravityAffectedBlock(int x, int y, int z) {
                return false;
            }
        };
    }
}
