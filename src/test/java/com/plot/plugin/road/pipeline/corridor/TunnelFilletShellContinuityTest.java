package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 隧道圆角壳：深覆土封顶连续；浅覆土不打穿地表；洞腔内旧衬砌按 CAVITY_AIR 清除。
 */
class TunnelFilletShellContinuityTest {

    private static final int ROAD_Y = 64;
    private static final int CLEAR_HEIGHT = 5;
    private static final int LINING = 1;
    private static final int ROOF_Y = ROAD_Y + CLEAR_HEIGHT;

    @Test
    void deepCoverPlacesRoofAtDesignElevationAndClearsCavity() {
        RoadNetwork network = tunnelCrossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        Map<String, RoadGenerationResult> edgeResults = tunnelEdgeResults(network);

        // 预置洞腔内旧衬砌柱，必须被 CAVITY_AIR 清掉
        BlockPos staleColumn = new BlockPos(0, ROAD_Y + 2, 0);
        for (RoadGenerationResult result : edgeResults.values()) {
            result.recordPlacementOverride(staleColumn, "minecraft:stone", "minecraft:stone_bricks");
            result.placementLayers.put(staleColumn, RoadSolidLayer.TUNNEL);
            result.placementMaterialPriorities.put(staleColumn, RoadSolidMaterialPriority.STRUCTURAL);
            result.tunnelBlocks.add(staleColumn);
        }

        int surfaceY = 80;
        TerrainSampler mountain = surfaceTerrain(surfaceY);
        RoadSystemConfig config = config();

        TunnelJunctionShellPass.apply(
            network, edgeResults, Map.of(junction.getId(), ROAD_Y), config,
            IdentityCoordinateService.INSTANCE, (path, segments) -> 1.0,
            mountain,
            material -> material == null || material.isBlank() ? "minecraft:stone_bricks" : material,
            pos -> "minecraft:stone");

        boolean roofAtDesign = false;
        boolean cavityAir = false;
        boolean surfaceUntouched = true;
        for (RoadGenerationResult result : edgeResults.values()) {
            for (Map.Entry<BlockPos, BlockRecord> entry : result.placementRecords.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockRecord record = entry.getValue();
                if (record == null) {
                    continue;
                }
                if (pos.getY() >= surfaceY
                        && !"minecraft:air".equals(record.newBlockId)
                        && result.placementLayers.get(pos) == RoadSolidLayer.TUNNEL) {
                    surfaceUntouched = false;
                }
                RoadSolidLayer layer = result.placementLayers.get(pos);
                Integer priority = result.placementMaterialPriorities.get(pos);
                if (pos.getY() == ROOF_Y
                        && layer == RoadSolidLayer.TUNNEL
                        && priority != null
                        && priority >= RoadSolidMaterialPriority.STRUCTURAL
                        && !"minecraft:air".equals(record.newBlockId)) {
                    roofAtDesign = true;
                }
                if ("minecraft:air".equals(record.newBlockId)
                        && pos.getY() > ROAD_Y
                        && pos.getY() < ROOF_Y) {
                    cavityAir = true;
                }
            }
            BlockRecord stale = result.placementRecords.get(staleColumn);
            assertTrue(stale != null && "minecraft:air".equals(stale.newBlockId),
                "old structural lining inside CAVITY_AIR must be cleared");
        }
        assertTrue(roofAtDesign, "design roof elevation must receive continuous lining");
        assertTrue(cavityAir, "enclosed cavity must excavate air below roof");
        assertTrue(surfaceUntouched, "natural surface and above must not receive tunnel lining");
    }

    @Test
    void shallowCoverDoesNotExcavateOrSealRoof() {
        RoadNetwork network = tunnelCrossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        Map<String, RoadGenerationResult> edgeResults = tunnelEdgeResults(network);

        // 地表仅 67：低于 roofY(69)+衬砌，不得封闭开挖
        int shallowSurface = ROAD_Y + 3;
        TerrainSampler shallow = surfaceTerrain(shallowSurface);
        RoadSystemConfig config = config();

        assertFalse(TunnelJunctionShellPass.columnHasEnclosedCover(
            shallow,
            new Vec2d(0, 0),
            new ColumnKey(0, 0),
            ROAD_Y,
            CLEAR_HEIGHT,
            LINING,
            RoadConstructionHeuristics.tunnelThreshold(config)));

        TunnelJunctionShellPass.apply(
            network, edgeResults, Map.of(junction.getId(), ROAD_Y), config,
            IdentityCoordinateService.INSTANCE, (path, segments) -> 1.0,
            shallow,
            material -> "minecraft:stone_bricks",
            pos -> "minecraft:stone");

        for (RoadGenerationResult result : edgeResults.values()) {
            assertTrue(result.placementRecords.isEmpty(),
                "shallow-cover plaza must not receive shell excavation or lining");
        }
    }

    private static RoadSystemConfig config() {
        RoadSystemConfig config = new RoadSystemConfig("tunnel-fillet-shell");
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

    private static TerrainSampler surfaceTerrain(int surfaceY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return surfaceY;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y < surfaceY;
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
}
