package com.plot.plugin.road.model.section;

import com.plot.plugin.config.RoadSystemConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        section.getCarriageway().setLaneCount(1);
        section.getCarriageway().setDefaultLaneWidth(6);
        section.getCarriageway().syncDerivedWidth(CenterLineStyle.NONE, false, 0);
        section.getShoulder().setEnabled(true);
        section.getShoulder().setWidth(2);
        section.getSidewalk().setEnabled(false);

        ResolvedCrossSection resolved = section.resolve(config);

        assertEquals(6, resolved.carriagewayWidth);
        assertTrue(resolved.includeShoulder);
        assertEquals(2, resolved.shoulderWidth);
        assertFalse(resolved.includeSidewalk);
    }

    @Test
    void migratesOldSevenWideTwoLaneToLaneWidthThree() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setLaneCount(2);
        section.getCarriageway().setWidth(7);
        section.getMarkings().setCenterLineStyle(CenterLineStyle.SINGLE_DASHED);
        section.getMarkings().setLaneDividers(true);

        ResolvedCrossSection resolved = section.resolve(config);

        assertEquals(List.of(3, 3), resolved.laneWidths);
        assertEquals(7, resolved.carriagewayWidth);
        assertEquals(List.of(0.5), resolved.laneDividerOffsets);
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
        section.getSlopeBatter().setFillMaterial("minecraft:stone");
        section.getBikeLane().setEnabled(true);
        section.getBikeLane().setWidth(2);

        ResolvedCrossSection resolved = section.resolve(config);

        assertTrue(resolved.includeSlopeBatter);
        assertEquals(1.2f, resolved.fillSlopeRatio, 1e-3);
        assertEquals(0.8f, resolved.cutSlopeRatio, 1e-3);
        assertTrue(resolved.includeBikeLane);
        assertEquals(2, resolved.bikeLaneWidth);
        assertEquals("minecraft:stone", resolved.fillSlopeMaterial);
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
    void slopeAnchorUsesOutermostHardBand() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setLaneCount(2);
        section.getCarriageway().setDefaultLaneWidth(3);
        section.getMarkings().setCenterLineStyle(CenterLineStyle.SINGLE_DASHED);
        section.getMarkings().setLaneDividers(true);
        section.getCarriageway().syncDerivedWidth(CenterLineStyle.SINGLE_DASHED, true, 0);
        section.getShoulder().setEnabled(true);
        section.getShoulder().setWidth(2);
        section.getBikeLane().setEnabled(true);
        section.getBikeLane().setWidth(2);
        section.getSidewalk().setEnabled(true);
        section.getSidewalk().setWidth(3);
        section.getSlopeBatter().setEnabled(true);
        section.getSlopeBatter().setFillRatio(1.0f);

        ResolvedCrossSection resolved = section.resolve(config);

        assertEquals(7, resolved.carriagewayWidth);
        assertEquals(resolved.sidewalkCenterOffset(), resolved.slopeAnchorCenterOffset(), 1e-6);
        assertEquals(3, resolved.slopeAnchorBandWidth());
    }

    @Test
    void slopeAnchorCenterOffsetUsesCarriagewayEdgeWithoutShoulder() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setIncludeSidewalk(false);
        RoadCrossSection section = new RoadCrossSection();
        section.getCarriageway().setLaneCount(2);
        section.getCarriageway().setDefaultLaneWidth(3);
        section.getMarkings().setCenterLineStyle(CenterLineStyle.SINGLE_DASHED);
        section.getMarkings().setLaneDividers(true);
        section.getCarriageway().syncDerivedWidth(CenterLineStyle.SINGLE_DASHED, true, 0);
        section.getShoulder().setEnabled(false);
        section.getBikeLane().setEnabled(false);
        section.getSidewalk().setEnabled(false);
        section.getSlopeBatter().setEnabled(true);
        section.getSlopeBatter().setFillRatio(1.0f);

        ResolvedCrossSection resolved = section.resolve(config);

        assertEquals(3.5, resolved.slopeAnchorCenterOffset(), 1e-6);
        assertEquals(0, resolved.slopeAnchorBandWidth());
    }

    @Test
    void nullRoadFieldsFallBackToConfig() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setDefaultLaneWidth(6);

        ResolvedCrossSection resolved = RoadCrossSection.fromConfig(config).resolve(config);

        assertEquals(6, resolved.carriagewayWidth);
        assertTrue(resolved.includeSidewalk);
    }

    @Test
    void nullSlopeBatterEnabledInheritsConfigFlagEvenWhenRatiosPositive() {
        RoadSystemConfig off = new RoadSystemConfig("slope-off");
        off.setIncludeSlopeBatter(false);
        off.setFillSlopeRatio(1.0f);
        off.setCutSlopeRatio(1.0f);

        ResolvedCrossSection resolved = new RoadCrossSection().resolve(off);
        assertFalse(resolved.includeSlopeBatter);
        assertEquals(0f, resolved.fillSlopeRatio, 1e-6);
        assertEquals(0f, resolved.cutSlopeRatio, 1e-6);

        RoadSystemConfig on = new RoadSystemConfig("slope-on");
        on.setIncludeSlopeBatter(true);
        on.setFillSlopeRatio(1.0f);
        on.setCutSlopeRatio(1.0f);
        assertTrue(new RoadCrossSection().resolve(on).includeSlopeBatter);
    }
}
