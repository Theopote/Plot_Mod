package com.plot.plugin.road.model.section;

import com.plot.plugin.config.RoadSystemConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolvedCrossSectionTest {

    @Test
    void roadOverridesTakePrecedenceOverConfig() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(5);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(true);
        config.setSidewalkWidth(1);

        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setWidth(9);
        section.getShoulder().setEnabled(true);
        section.getShoulder().setWidth(2);
        section.getSidewalk().setEnabled(false);

        ResolvedCrossSection resolved = section.resolve(config);

        assertEquals(9, resolved.carriagewayWidth);
        assertTrue(resolved.includeShoulder);
        assertEquals(2, resolved.shoulderWidth);
        assertFalse(resolved.includeSidewalk);
    }

    @Test
    void slopeAndBikeLaneResolveFromSection() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setFillSlopeRatio(2.0f);
        config.setCutSlopeRatio(1.5f);

        RoadCrossSection section = new RoadCrossSection();
        section.getShoulder().setEnabled(true);
        section.getSlopeBatter().setFillRatio(1.2f);
        section.getSlopeBatter().setCutRatio(0.8f);
        section.getSlopeBatter().setFillMaterial("minecraft:gravel");
        section.getBikeLane().setEnabled(true);
        section.getBikeLane().setWidth(2);

        ResolvedCrossSection resolved = section.resolve(config);

        assertTrue(resolved.includeSlopeBatter);
        assertEquals(1.2f, resolved.fillSlopeRatio, 1e-3);
        assertEquals(0.8f, resolved.cutSlopeRatio, 1e-3);
        assertTrue(resolved.includeBikeLane);
        assertEquals(2, resolved.bikeLaneWidth);
        assertEquals("minecraft:gravel", resolved.fillSlopeMaterial);
    }

    @Test
    void slopeEnabledWithoutShoulder() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setIncludeShoulder(false);
        config.setFillSlopeRatio(2.0f);
        config.setCutSlopeRatio(1.5f);

        RoadCrossSection section = new RoadCrossSection();
        section.getShoulder().setEnabled(false);
        section.getSlopeBatter().setEnabled(true);
        section.getSlopeBatter().setFillRatio(2.0f);
        section.getSlopeBatter().setCutRatio(1.5f);

        ResolvedCrossSection resolved = section.resolve(config);

        assertFalse(resolved.includeShoulder);
        assertTrue(resolved.includeSlopeBatter);
        assertEquals(2.0f, resolved.fillSlopeRatio, 1e-3);
    }

    @Test
    void slopeAnchorCenterOffsetUsesCarriagewayEdgeWithoutShoulder() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setWidth(7);
        section.getShoulder().setEnabled(false);
        section.getSlopeBatter().setEnabled(true);
        section.getSlopeBatter().setFillRatio(1.0f);

        ResolvedCrossSection resolved = section.resolve(config);

        assertEquals(3.5, resolved.slopeAnchorCenterOffset(), 1e-6);
    }

    @Test
    void nullRoadFieldsFallBackToConfig() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(6);
        config.setIncludeDrainage(true);

        ResolvedCrossSection resolved = RoadCrossSection.fromConfig(config).resolve(config);

        assertEquals(6, resolved.carriagewayWidth);
        assertTrue(resolved.includeDrain);
    }
}
