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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 连通清障不得删除 ShellPass 刚写入的合法 WALL/ROOF 衬砌（共享 expectedTunnelLining / VoxelRole）。
 */
class TunnelShellConnectivityRespectsVoxelRoleTest {

    private static final int ROAD_Y = 64;
    private static final int CLEAR_HEIGHT = 5;
    private static final int LINING = 1;
    private static final int ROOF_Y = ROAD_Y + CLEAR_HEIGHT;

    @Test
    void connectivityDoesNotDeleteFreshShellRoofLining() {
        RoadNetwork network = tunnelCrossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        Map<String, RoadGenerationResult> edgeResults = tunnelEdgeResults(network);
        RoadSystemConfig config = config();
        TerrainSampler mountain = surfaceTerrain(80);

        TunnelJunctionShellPass.apply(
            network, edgeResults, Map.of(junction.getId(), ROAD_Y), config,
            IdentityCoordinateService.INSTANCE, (path, segments) -> 1.0,
            mountain,
            material -> "minecraft:stone_bricks",
            pos -> "minecraft:stone");

        Set<BlockPos> expectedLiningBefore = collectExpectedLining(edgeResults);
        assertFalse(expectedLiningBefore.isEmpty(), "shell must record expected lining");

        boolean hadRoofBefore = hasStructuralLiningAtY(edgeResults, ROOF_Y);
        assertTrue(hadRoofBefore, "shell must place roof lining before connectivity");

        TunnelJunctionConnectivityPass.apply(
            network, edgeResults, Map.of(junction.getId(), ROAD_Y), config,
            IdentityCoordinateService.INSTANCE, (path, segments) -> 1.0,
            pos -> "minecraft:stone",
            null,
            mountain);

        for (BlockPos pos : expectedLiningBefore) {
            assertTrue(
                stillStructuralLining(edgeResults, pos),
                "connectivity must keep shell VoxelRole lining at " + pos);
        }
        assertTrue(hasStructuralLiningAtY(edgeResults, ROOF_Y),
            "roof lining must survive connectivity pass");
    }

    private static boolean stillStructuralLining(
            Map<String, RoadGenerationResult> edgeResults, BlockPos pos) {
        for (RoadGenerationResult result : edgeResults.values()) {
            BlockRecord record = result.placementRecords.get(pos);
            if (record == null || "minecraft:air".equals(record.newBlockId)) {
                continue;
            }
            if (result.expectedTunnelLining.contains(pos)
                    && result.placementLayers.get(pos) == RoadSolidLayer.TUNNEL) {
                Integer priority = result.placementMaterialPriorities.get(pos);
                return priority != null && priority >= RoadSolidMaterialPriority.STRUCTURAL;
            }
        }
        return false;
    }

    private static boolean hasStructuralLiningAtY(
            Map<String, RoadGenerationResult> edgeResults, int y) {
        for (RoadGenerationResult result : edgeResults.values()) {
            for (Map.Entry<BlockPos, BlockRecord> entry : result.placementRecords.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockRecord record = entry.getValue();
                if (pos.getY() != y || record == null || "minecraft:air".equals(record.newBlockId)) {
                    continue;
                }
                if (result.placementLayers.get(pos) == RoadSolidLayer.TUNNEL
                        && result.expectedTunnelLining.contains(pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<BlockPos> collectExpectedLining(Map<String, RoadGenerationResult> edgeResults) {
        java.util.LinkedHashSet<BlockPos> lining = new java.util.LinkedHashSet<>();
        for (RoadGenerationResult result : edgeResults.values()) {
            lining.addAll(result.expectedTunnelLining);
        }
        return lining;
    }

    private static RoadSystemConfig config() {
        RoadSystemConfig config = new RoadSystemConfig("shell-connect-voxel-role");
        config.setDefaultCornerRadius(4.0f);
        return config;
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

            @Override
            public boolean isRoadClearableDecoration(int x, int y, int z) {
                return false;
            }
        };
    }
}
