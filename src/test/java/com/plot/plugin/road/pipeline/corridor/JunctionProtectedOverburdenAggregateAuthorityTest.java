package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 最终 aggregate 缺失路面时，不得因路口预合并 solids 仍有路面而漏报标高错位。
 */
class JunctionProtectedOverburdenAggregateAuthorityTest {

    @Test
    void solidsPavementDoesNotMaskMissingFinalPlacement() {
        RoadSystemConfig config = new RoadSystemConfig("overburden-authority");
        config.setSelectedMaterial("minecraft:stone");

        RoadNetwork network = crossNetwork();
        RoadNode junction = hub(network);
        Map<String, RoadGenerationResult> edgeResults = tunnelEdges(network);

        RoadJunctionGenerator.JunctionBlocks blocks = new RoadJunctionGenerator(
            new RoadGenerator(config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance()))
            .generateJunction(junction, network, surfaceAt(90), Map.of(junction.getId(), 64), edgeResults);

        JunctionElevationField field = blocks.elevationField();
        assertNotNull(field);
        assertFalse(field.targetYByColumn().isEmpty());

        Map.Entry<ColumnKey, Integer> target = field.targetYByColumn().entrySet().iterator().next();
        ColumnKey column = target.getKey();
        int expectedY = target.getValue();

        boolean solidsHadPavementAtTarget = blocks.getSolids().primitives().stream()
            .anyMatch(p -> p != null
                && p.planPoint() != null
                && (int) Math.floor(p.planPoint().x) == column.worldX()
                && (int) Math.floor(p.planPoint().y) == column.worldZ()
                && p.elevation() == expectedY
                && p.layer() == RoadSolidLayer.ROAD
                && !"minecraft:air".equals(p.materialId()));
        // 即便 solids 没有精确命中，也构造「solids 索引会认为有路面」的场景：
        // evaluate 建 solidIndex 来自 blocks；我们保证至少目标列在 field 内。

        RoadGenerationResult finalAggregate = new RoadGenerationResult(0);
        BlockPos elsewhere = new BlockPos(column.worldX(), expectedY + 3, column.worldZ());
        finalAggregate.recordPlacementOverride(elsewhere, "minecraft:air", "minecraft:stone");
        finalAggregate.placementLayers.put(elsewhere, RoadSolidLayer.ROAD);

        JunctionProtectedOverburdenValidator.Metrics metrics =
            JunctionProtectedOverburdenValidator.evaluate(blocks, finalAggregate, surfaceAt(90));

        assertTrue(metrics.unexpectedSurfaceElevationCount() > 0,
            "final aggregate missing target Y must count elevation mismatch"
                + " (solidsHadTargetPavement=" + solidsHadPavementAtTarget + ")");
    }

    private static RoadNode hub(RoadNetwork network) {
        return network.getNodes().values().stream()
            .filter(n -> n.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
    }

    private static Map<String, RoadGenerationResult> tunnelEdges(RoadNetwork network) {
        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        for (RoadEdge edge : network.getEdges().values()) {
            RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
            result.constructionTypes.add(RoadConstructionType.TUNNEL);
            edgeResults.put(edge.getId(), result);
        }
        return edgeResults;
    }

    private static RoadNetwork crossNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("cross");
        TunnelStyle style = new TunnelStyle();
        style.setClearHeight(5);
        style.setLiningThickness(1);
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
        };
    }
}
