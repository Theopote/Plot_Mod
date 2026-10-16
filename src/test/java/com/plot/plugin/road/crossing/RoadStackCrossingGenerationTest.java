package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
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
    void applyingSilentUrbanPresetKeepsGlobalSlopeBatterEnabled() {
        RoadSystemConfig config = new RoadSystemConfig("urban-slope");
        config.setIncludeSlopeBatter(true);
        config.setFillSlopeRatio(1.5f);
        config.setCutSlopeRatio(1.0f);

        config.applyStyle(com.plot.plugin.road.style.RoadStyleCatalog.cityMain());

        assertTrue(config.isIncludeSlopeBatter());
        assertEquals(1.5f, config.getFillSlopeRatio(), 1e-3f);
        assertEquals(1.0f, config.getCutSlopeRatio(), 1e-3f);

        RoadNetwork network = new RoadNetwork();
        network.createRoadForAdopt(config);
        ResolvedCrossSection resolved = network.getRoads().values().iterator().next()
            .getCrossSection().resolve(config);
        assertTrue(resolved.includeSlopeBatter);
        assertEquals(1.5f, resolved.fillSlopeRatio, 1e-3f);
    }

    @Test
    void gradeSeparatedStackCrossingKeepsUpperSurfaceAboveUnderpass() {
        RoadNetwork network = figureEightNetwork();
        RoadStackCrossingReconciler.reconcileStackCrossings(network);
        RoadStackCrossing stack = network.getStackCrossings().values().iterator().next();
        network.setStackCrossingGradeSeparation(
            stack.id(),
            CrossingType.GRADE_SEPARATED,
            stack.stationA(),
            stack.stationB(),
            4.0);

        RoadNetworkGenerator generator = previewGenerator();
        RoadNetworkGenerator.PreviewResult preview = generator.generatePreview(
            network,
            flatTerrain(64));

        RoadNetwork profileNetwork = preview.profileNetwork();
        RoadNode junction = profileNetwork.getNodes().values().stream()
            .filter(node -> node.isStackGradeSeparated())
            .findFirst()
            .orElseThrow();
        Integer underpassY = preview.nodeElevations().get(junction.getId());
        assertTrue(underpassY != null);

        int upperSurfaceAtCrossing = 0;
        int expectedUpperY = underpassY + 4;
        for (var edgeResult : preview.edgeResults().values()) {
            for (var entry : edgeResult.placementRecords.entrySet()) {
                var pos = entry.getKey();
                if (Math.abs(pos.getX() - 5) > 2 || Math.abs(pos.getZ() - 5) > 2) {
                    continue;
                }
                if (pos.getY() == expectedUpperY
                        && edgeResult.placementLayers.get(pos) == com.plot.plugin.road.solid.RoadSolidLayer.ROAD) {
                    upperSurfaceAtCrossing++;
                }
            }
        }
        assertTrue(upperSurfaceAtCrossing > 0,
            "stack overpass must place road surface near crossing at underpassY+clearance");
    }

    @Test
    void atGradeStackCrossingFillsJunctionOnSharedPlane() {
        RoadNetwork network = figureEightNetwork();
        RoadStackCrossingReconciler.reconcileStackCrossings(network);

        RoadNetworkGenerator generator = previewGenerator();
        RoadNetworkGenerator.PreviewResult preview = generator.generatePreview(
            network,
            flatTerrain(64));

        assertFalse(preview.networkResult().getJunctionResults().isEmpty());
        RoadNode plazaNode = preview.profileNetwork().getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();
        int plazaY = preview.nodeElevations().getOrDefault(plazaNode.getId(), 64);
        long surfaceAtPlaza = 0;
        long surfaceOffPlane = 0;
        for (var junction : preview.networkResult().getJunctionResults().values()) {
            for (var primitive : junction.getSolids().primitives()) {
                if (primitive.layer() != com.plot.plugin.road.solid.RoadSolidLayer.ROAD) {
                    continue;
                }
                if (primitive.elevation() == plazaY) {
                    surfaceAtPlaza++;
                } else {
                    surfaceOffPlane++;
                }
            }
        }
        assertTrue(surfaceAtPlaza > 0, "at-grade self-crossing must stamp a plaza");
        assertEquals(0, surfaceOffPlane, "at-grade self-crossing plaza must share one elevation");
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
