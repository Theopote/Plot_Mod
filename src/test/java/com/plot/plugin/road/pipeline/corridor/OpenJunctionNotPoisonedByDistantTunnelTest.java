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
import com.plot.plugin.road.solid.RoadSolidPrimitive;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 开敞平交不得被「相连边远处有隧道施工」毒化：仍须铺装圆角并清障到自然地表。
 */
class OpenJunctionNotPoisonedByDistantTunnelTest {

    @Test
    void openPlazaClearsAndPavesDespiteDistantTunnelOnSameEdge() {
        RoadSystemConfig config = new RoadSystemConfig("open-not-poisoned");
        config.setDefaultCornerRadius(6.0f);
        config.setSelectedMaterial("minecraft:stone");

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("mixed");
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode n = network.createNode(new Vec2d(0, 40));
        RoadNode s = network.createNode(new Vec2d(0, -40));
        RoadNode e = network.createNode(new Vec2d(40, 0));
        RoadNode w = network.createNode(new Vec2d(-40, 0));
        network.createEdge(junction.getId(), n.getId(), List.of(junction.getPosition(), n.getPosition()), road.getId());
        network.createEdge(junction.getId(), s.getId(), List.of(junction.getPosition(), s.getPosition()), road.getId());
        network.createEdge(junction.getId(), e.getId(), List.of(junction.getPosition(), e.getPosition()), road.getId());
        network.createEdge(junction.getId(), w.getId(), List.of(junction.getPosition(), w.getPosition()), road.getId());

        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        for (RoadEdge edge : network.getEdges().values()) {
            RoadGenerationResult result = new RoadGenerationResult(edge.getLength());
            // 整边标记含隧道族（模拟远处洞身），但节点邻域并非隧道进口
            result.constructionTypes.add(RoadConstructionType.ROAD);
            result.constructionTypes.add(RoadConstructionType.ROAD);
            result.constructionTypes.add(RoadConstructionType.TUNNEL);
            result.constructionTypes.add(RoadConstructionType.TUNNEL);
            edgeResults.put(edge.getId(), result);
        }

        int groundY = 70;
        int junctionY = 64;
        TerrainSampler hillside = terrainWithSurface(groundY);

        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadJunctionGenerator junctionGenerator = new RoadJunctionGenerator(generator);
        RoadJunctionGenerator.JunctionBlocks blocks = junctionGenerator.generateJunction(
            junction, network, hillside, Map.of(junction.getId(), junctionY), edgeResults);

        assertFalse(blocks.getSolids().primitives().isEmpty(), "open junction must emit solids");

        boolean hasPavement = false;
        boolean hasClearanceAirAboveRoad = false;
        for (RoadSolidPrimitive p : blocks.getSolids().primitives()) {
            if (p == null) {
                continue;
            }
            if (p.layer() == RoadSolidLayer.ROAD
                    || p.layer() == RoadSolidLayer.SIDEWALK
                    || p.layer() == RoadSolidLayer.SHOULDER) {
                hasPavement = true;
            }
            if ("minecraft:air".equals(p.materialId())
                    && p.elevation() > junctionY
                    && p.elevation() < groundY) {
                hasClearanceAirAboveRoad = true;
            }
        }
        assertTrue(hasPavement, "open plaza must stamp pavement including fillet responsibility");
        assertTrue(hasClearanceAirAboveRoad,
            "open plaza must clear terrain above road toward natural surface; "
                + "distant edge tunnel must not install shallow-cover punch skip");

        // 边级 infer 仍可能算出 cap，但生成路径不得采用（无真实进口）
        Integer inferred = TunnelJunctionSeedResolver.inferTunnelCavityClearCapFromEdges(
            network, new ArrayList<>(network.getEdges().values()), edgeResults, junctionY, config);
        assertTrue(inferred != null, "precondition: whole-edge infer would poison if used");
    }

    private static TerrainSampler terrainWithSurface(int surfaceY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return surfaceY;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
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
            public boolean isRoadClearableDecoration(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean isGravityAffectedBlock(int x, int y, int z) {
                return false;
            }
        };
    }
}
