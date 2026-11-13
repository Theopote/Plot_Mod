package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 壳层顶衬必须读取路口权威高程场，不得本地改写后与圆角路面差一格。
 */
class TunnelShellUsesJunctionElevationFieldTest {

    private static final int CLEAR = 5;
    private static final int ROAD_Y = 64;

    @Test
    void shellRoofTracksAuthoredJunctionElevationField() {
        RoadSystemConfig config = new RoadSystemConfig("shell-authored-y");
        config.setDefaultCornerRadius(6.0f);
        config.setSelectedMaterial("minecraft:stone");

        RoadNetwork network = crossNetwork();
        RoadNode junction = hub(network);
        Map<String, RoadGenerationResult> edgeResults = tunnelEdges(network);
        TerrainSampler mountain = surfaceAt(90);

        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadJunctionGenerator.JunctionBlocks blocks = new RoadJunctionGenerator(generator)
            .generateJunction(junction, network, mountain, Map.of(junction.getId(), ROAD_Y), edgeResults);
        assertFalse(blocks.elevationField().targetYByColumn().isEmpty(), "precondition: authored field");

        TunnelJunctionShellPass.apply(
            network,
            edgeResults,
            Map.of(junction.getId(), ROAD_Y),
            config,
            IdentityCoordinateService.INSTANCE,
            (path, segments) -> 1.0,
            mountain,
            material -> "minecraft:stone_bricks",
            pos -> "minecraft:stone",
            Map.of(junction.getId(), blocks));

        int matched = 0;
        int exact = 0;
        JunctionElevationField field = blocks.elevationField();
        for (RoadGenerationResult result : edgeResults.values()) {
            for (Map.Entry<BlockPos, BlockRecord> entry : result.placementRecords.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockRecord record = entry.getValue();
                if (pos == null || record == null || "minecraft:air".equals(record.newBlockId)) {
                    continue;
                }
                if (result.placementLayers.get(pos) != RoadSolidLayer.TUNNEL) {
                    continue;
                }
                ColumnKey column = new ColumnKey(pos.getX(), pos.getZ());
                Integer roadY = field.targetYByColumn().get(column);
                if (roadY == null) {
                    continue;
                }
                int offset = pos.getY() - roadY;
                if (offset < CLEAR - 1 || offset > CLEAR + 1) {
                    continue;
                }
                matched++;
                if (offset == CLEAR) {
                    exact++;
                }
            }
        }
        assertTrue(matched >= 4, "need roof lining on authored columns, matched=" + matched);
        assertTrue(exact >= 2, "roofs must sit at roadY+clearHeight for authored field; exact=" + exact);

        // 同列：壳层不得使用与权威场不同的路面基准（差 ≥2 视为独立改写）。
        for (var entry : field.targetYByColumn().entrySet()) {
            ColumnKey column = entry.getKey();
            Integer authored = entry.getValue();
            if (column == null || authored == null) {
                continue;
            }
            Integer resolved = TunnelJunctionShellPass.resolveShellElevationField(
                blocks, field.footprint(), null, null, ROAD_Y).surfaceY(column);
            assertEquals(authored, resolved,
                "resolveShellElevationField must reuse authored pavement Y; col=" + column);
        }
    }

    private static RoadNode hub(RoadNetwork network) {
        return network.getNodes().values().stream()
            .filter(n -> n.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
    }

    private static Map<String, RoadGenerationResult> tunnelEdges(RoadNetwork network) {
        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        for (var edge : network.getEdges().values()) {
            RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
            result.constructionTypes.add(RoadConstructionType.TUNNEL);
            edgeResults.put(edge.getId(), result);
        }
        return edgeResults;
    }

    private static RoadNetwork crossNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("tunnel");
        TunnelStyle style = new TunnelStyle();
        style.setClearHeight(CLEAR);
        style.setLiningThickness(1);
        style.setLiningMaterial("minecraft:stone_bricks");
        road.setTunnelStyle(style);
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode n = network.createNode(new Vec2d(0, 40));
        RoadNode s = network.createNode(new Vec2d(0, -40));
        RoadNode e = network.createNode(new Vec2d(40, 0));
        RoadNode w = network.createNode(new Vec2d(-40, 0));
        network.createEdge(junction.getId(), n.getId(), List.of(junction.getPosition(), n.getPosition()), road.getId());
        network.createEdge(junction.getId(), s.getId(), List.of(junction.getPosition(), s.getPosition()), road.getId());
        network.createEdge(junction.getId(), e.getId(), List.of(junction.getPosition(), e.getPosition()), road.getId());
        network.createEdge(junction.getId(), w.getId(), List.of(junction.getPosition(), w.getPosition()), road.getId());
        return network;
    }

    private static TerrainSampler surfaceAt(int surfaceY) {
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
        };
    }
}
