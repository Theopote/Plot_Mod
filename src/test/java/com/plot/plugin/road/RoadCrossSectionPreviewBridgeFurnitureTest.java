package com.plot.plugin.road;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.section.BridgePierStyle;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.crosssection.ResolvedBridgeStructure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCrossSectionPreviewBridgeFurnitureTest {

    @Test
    void furnitureAppearsOnlyWhenPillarsOrGuardrailEnabled() {
        RoadSystemConfig config = new RoadSystemConfig("preview-bridge");
        config.setRoadWidth(5);
        config.setGenerateBridgePillars(false);
        config.setIncludeBridgeGuardrail(false);
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(config);

        var hidden = RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromResolved(
            section, 10f, -1f, ResolvedBridgeStructure.fromConfig(config, 5));
        assertNull(hidden.bridgeFurniture);

        config.setGenerateBridgePillars(true);
        config.setBridgePierStyle(BridgePierStyle.DOUBLE);
        config.setBridgePierDiameterBlocks(1);
        config.setBridgePierOffsetBlocks(2);
        var withPiers = RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromResolved(
            section, 10f, -1f, ResolvedBridgeStructure.fromConfig(config, 5));
        assertNotNull(withPiers.bridgeFurniture);
        assertTrue(withPiers.bridgeFurniture.showPillars());
        assertFalse(withPiers.bridgeFurniture.showGuardrail());

        config.setGenerateBridgePillars(false);
        config.setIncludeBridgeGuardrail(true);
        var withRail = RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromResolved(
            section, 10f, -1f, ResolvedBridgeStructure.fromConfig(config, 5));
        assertNotNull(withRail.bridgeFurniture);
        assertFalse(withRail.bridgeFurniture.showPillars());
        assertTrue(withRail.bridgeFurniture.showGuardrail());
    }

    @Test
    void roadResolveFeedsPreviewLayout() {
        RoadSystemConfig config = new RoadSystemConfig("preview-bridge-road");
        config.setRoadWidth(5);
        config.setGenerateBridgePillars(true);
        Road road = new Road("r1");
        road.setIncludeBridgeGuardrail(true);
        ResolvedBridgeStructure resolved = ResolvedBridgeStructure.resolve(road, config);
        assertTrue(resolved.generatePillars());
        assertTrue(resolved.includeGuardrail());
    }
}
