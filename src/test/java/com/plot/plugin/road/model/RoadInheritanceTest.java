package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.model.section.BridgePierStyle;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.pipeline.crosssection.ResolvedBridgeStructure;
import com.plot.plugin.road.tunnel.TunnelShape;
import com.plot.plugin.road.tunnel.TunnelStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadInheritanceTest {

    private final RoadNetworkBuilder builder = new RoadNetworkBuilder();
    @Test
    void createRoadInheritsGlobalDefaultsDynamically() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(9);
        RoadNetwork network = new RoadNetwork();

        Road road = network.createRoad(config);

        assertNull(road.getWidth());
        assertEquals(9, road.getEffectiveWidth(config));

        config.setRoadWidth(11);
        assertEquals(11, road.getEffectiveWidth(config));
    }

    @Test
    void createRoadFromDefaultsSnapshotsExplicitValues() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(9);
        RoadNetwork network = new RoadNetwork();

        Road road = network.createRoadFromDefaults(config);

        assertEquals(9, road.getWidth());
        config.setRoadWidth(11);
        assertEquals(9, road.getEffectiveWidth(config));
        assertFalse(RoadParameterInheritance.inheritsWidth(road));
    }

    @Test
    void adoptShapeSnapshotsDefaultsAtClaimTime() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(9);
        config.setMaxSlope(8f);
        RoadNetwork network = new RoadNetwork();

        builder.adoptShape(network, new PolylineShape(
            java.util.List.of(new Vec2d(0, 0), new Vec2d(10, 0)), false), config);

        Road road = network.getRoads().values().iterator().next();
        assertEquals(9, road.getWidth());
        assertEquals(8f, road.getMaxSlope());

        config.setRoadWidth(11);
        config.setMaxSlope(12f);
        assertEquals(9, road.getEffectiveWidth(config));
        assertEquals(8f, road.getEffectiveMaxSlope(config));
        assertFalse(RoadParameterInheritance.inheritsMaxSlope(road));
    }

    @Test
    void snapshotGlobalDefaultsAppliesSelectedStyle() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.applyStyle(config.getStyles().getFirst());
        Road road = new Road();

        RoadParameterInheritance.snapshotGlobalDefaults(road, config);

        assertEquals(config.getStyles().getFirst().id, road.getStyleId());
        assertEquals(config.getStyles().getFirst().width, road.getWidth());
    }

    @Test
    void resolveWidthTracksInheritanceLayers() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(7);
        Road road = new Road();

        var inherited = RoadParameterInheritance.resolveWidth(road, config);
        assertTrue(inherited.inherited());
        assertEquals(7, inherited.resolved());

        road.setWidth(9);
        var overridden = RoadParameterInheritance.resolveWidth(road, config);
        assertFalse(overridden.inherited());
        assertEquals(9, overridden.resolved());
    }

    @Test
    void inheritsAnyDetectsNullOverrides() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        Road road = new Road();
        assertTrue(RoadParameterInheritance.inheritsAny(road));

        road.applyDefaults(config);
        assertFalse(RoadParameterInheritance.inheritsAny(road));

        road.setWidth(null);
        assertTrue(RoadParameterInheritance.inheritsAny(road));
    }

    @Test
    void inheritAllDefaultsClearsExplicitOverrides() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(7);
        config.setMaxSlope(10f);

        Road road = new Road();
        road.setWidth(9);
        road.setMaxSlope(5f);
        road.setIncludeShoulder(true);
        road.setStyleId("city_main");

        road.inheritAllDefaults();

        assertNull(road.getWidth());
        assertNull(road.getMaxSlope());
        assertNull(road.getStyleId());
        assertNull(road.getIncludeShoulder());
        assertEquals(7, road.getEffectiveWidth(config));
        assertTrue(road.getEffectiveIncludeShoulder(config));
    }

    @Test
    void tunnelStyleOverrideAndInheritAllDefaults() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.getTunnelStyle().setShape(TunnelShape.RECTANGULAR);
        Road road = new Road();
        TunnelStyle override = new TunnelStyle();
        override.setShape(TunnelShape.HORSESHOE);
        road.setTunnelStyle(override);

        assertEquals(TunnelShape.HORSESHOE, road.getResolvedTunnelStyle(config).shape());

        road.inheritAllDefaults();
        assertNull(road.getStoredTunnelStyle());
        assertEquals(TunnelShape.RECTANGULAR, road.getResolvedTunnelStyle(config).shape());
    }

    @Test
    void bridgeStructureOverrideInheritAndSerialize() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setGenerateBridgePillars(true);
        config.setBridgePierSpacingBlocks(6.0);
        config.setBridgePierMaterial("minecraft:stone_bricks");
        config.setBridgePierStyle(BridgePierStyle.AUTO);
        config.setIncludeBridgeGuardrail(false);
        config.setBridgeGuardrailMaterial("minecraft:oak_fence");
        config.setBridgeGuardrailHeightBlocks(1);

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("bridge-structure-road");
        ResolvedBridgeStructure inherited = ResolvedBridgeStructure.resolve(road, config);
        assertTrue(inherited.generatePillars());
        assertEquals(6.0, inherited.pierSpacingBlocks(), 1e-6);
        assertFalse(inherited.includeGuardrail());
        assertEquals(1, inherited.guardrailHeightBlocks());
        assertEquals(BridgePierStyle.AUTO, inherited.pierStyle());

        road.setGenerateBridgePillars(false);
        road.setBridgePierSpacingBlocks(10.0);
        road.setBridgePierMaterial("minecraft:deepslate_bricks");
        road.setBridgePierStyle(BridgePierStyle.MULTI);
        road.setIncludeBridgeGuardrail(true);
        road.setBridgeGuardrailMaterial("minecraft:iron_bars");
        road.setBridgeGuardrailHeightBlocks(3);

        ResolvedBridgeStructure overridden = ResolvedBridgeStructure.resolve(road, config);
        assertFalse(overridden.generatePillars());
        assertEquals(10.0, overridden.pierSpacingBlocks(), 1e-6);
        assertEquals("minecraft:deepslate_bricks", overridden.pierMaterial());
        assertEquals(BridgePierStyle.MULTI, overridden.pierStyle());
        assertTrue(overridden.includeGuardrail());
        assertEquals("minecraft:iron_bars", overridden.guardrailMaterial());
        assertEquals(3, overridden.guardrailHeightBlocks());

        Road loaded = RoadNetwork.parseSnapshot(network.toJson()).getRoad("bridge-structure-road");
        assertFalse(loaded.getGenerateBridgePillars());
        assertEquals(10.0, loaded.getBridgePierSpacingBlocks(), 1e-6);
        assertEquals("minecraft:deepslate_bricks", loaded.getBridgePierMaterial());
        assertEquals(BridgePierStyle.MULTI, loaded.getBridgePierStyle());
        assertTrue(loaded.getIncludeBridgeGuardrail());
        assertEquals("minecraft:iron_bars", loaded.getBridgeGuardrailMaterial());
        assertEquals(3, loaded.getBridgeGuardrailHeightBlocks());

        loaded.inheritAllDefaults();
        assertNull(loaded.getGenerateBridgePillars());
        assertNull(loaded.getBridgePierSpacingBlocks());
        assertNull(loaded.getBridgePierMaterial());
        assertNull(loaded.getBridgePierStyle());
        assertNull(loaded.getIncludeBridgeGuardrail());
        assertNull(loaded.getBridgeGuardrailMaterial());
        assertNull(loaded.getBridgeGuardrailHeightBlocks());
        assertEquals(6.0, loaded.getEffectiveBridgePierSpacingBlocks(config), 1e-6);
        assertEquals(BridgePierStyle.AUTO, loaded.getEffectiveBridgePierStyle(config));
    }

    @Test
    void inheritAllResetsCrossSectionComponents() {
        RoadCrossSection section = RoadCrossSection.fromConfig(new RoadSystemConfig("road_system"));
        section.getMedian().setEnabled(true);
        section.getBikeLane().setEnabled(true);
        section.getSlopeBatter().setEnabled(false);

        section.inheritAll();

        assertNull(section.getCarriageway().getWidth());
        assertNull(section.getMedian().getEnabled());
        assertNull(section.getBikeLane().getEnabled());
        assertNull(section.getSlopeBatter().getEnabled());
    }
}
