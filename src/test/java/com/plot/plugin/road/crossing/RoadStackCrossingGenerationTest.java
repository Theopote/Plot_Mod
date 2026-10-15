package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.tunnel.TunnelLightingMode;
import com.plot.plugin.road.tunnel.TunnelShape;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadStackCrossingGenerationTest {

    @Test
    void adoptedUrbanPresetInheritsGlobalSlopeSettings() {
        RoadNetwork network = new RoadNetwork();
        RoadSystemConfig config = new RoadSystemConfig("stack-gen");
        config.setSelectedPreset("city_main");
        config.setIncludeSlopeBatter(true);
        config.setFillSlopeRatio(1.5f);
        config.setCutSlopeRatio(1.0f);

        network.createRoadForAdopt(config);

        ResolvedCrossSection resolved = network.getRoads().values().iterator().next()
            .getCrossSection().resolve(config);
        assertTrue(resolved.includeSlopeBatter);
        assertEquals(1.5f, resolved.fillSlopeRatio, 1e-3f);
        assertEquals(1.0f, resolved.cutSlopeRatio, 1e-3f);
    }

    @Test
    void figureEightStackCrossingMaterializesBeforePreview() {
        RoadNetwork network = figureEightNetwork();
        RoadStackCrossingReconciler.reconcileStackCrossings(network);

        RoadNetworkGenerator generator = previewGenerator();
        RoadNetworkGenerator.PreviewResult preview = generator.generatePreview(
            network,
            flatTerrain(64));

        long junctionCount = preview.profileNetwork().getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .count();
        assertEquals(1, junctionCount);
        assertFalse(preview.networkResult().getJunctionResults().isEmpty());
    }

    private static RoadNetwork figureEightNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("figure-eight");
        applyTunnelStyle(road);
        var n1 = network.createNode(new Vec2d(0, 0));
        var n2 = network.createNode(new Vec2d(10, 0));
        network.createEdge(
            n1.getId(),
            n2.getId(),
            List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 10),
                new Vec2d(0, 10),
                new Vec2d(10, 0)),
            road.getId());
        return network;
    }

    private static RoadNetworkGenerator previewGenerator() {
        RoadSystemConfig config = new RoadSystemConfig("stack-gen");
        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        return new RoadNetworkGenerator(generator);
    }

    private static void applyTunnelStyle(Road road) {
        TunnelStyle style = new TunnelStyle();
        style.setShape(TunnelShape.RECTANGULAR);
        style.setClearHeight(5);
        style.setSideClearance(1);
        style.setLiningThickness(1);
        style.setLiningMaterial("minecraft:stone_bricks");
        style.setLightingMode(TunnelLightingMode.NONE);
        style.ensureConfigDefaults();
        road.setTunnelStyle(style);
    }

    private static TerrainSampler flatTerrain(int y) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return y;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= 64;
            }
        };
    }
}
