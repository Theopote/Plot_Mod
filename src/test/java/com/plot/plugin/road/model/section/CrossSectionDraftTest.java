package com.plot.plugin.road.model.section;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadNetworkManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossSectionDraftTest {

    @Test
    void configRoundTripPreservesCrossSectionFields() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(9);
        config.setLaneCount(3);
        config.setLaneWidths(java.util.List.of(3, 3, 3));
        config.setIncludeBikeLane(true);
        config.setBikeLaneWidth(2);
        config.setIncludeMedian(true);
        config.setMedianWidth(2);
        config.setLaneDividers(true);
        config.setCenterLineStyle(CenterLineStyle.SINGLE_DASHED);
        config.setMarkingMaterial("minecraft:white_concrete");
        config.setStreetlightSpacing(12);
        config.setIncludeSlopeBatter(true);
        config.setFillSlopeRatio(1.5f);
        config.setCutSlopeRatio(1.0f);

        CrossSectionDraft draft = CrossSectionDraft.fromConfig(config);
        RoadSystemConfig copy = new RoadSystemConfig("road_system_copy");
        draft.applyToConfig(copy);

        // 3×3 + 同向缝1 + 中央虚线1 = 11（隔离带不计入行车道宽）
        assertEquals(11, copy.getRoadWidth());
        assertEquals(3, copy.getLaneCount());
        assertEquals(3, copy.getLaneWidths().size());
        assertTrue(copy.isIncludeBikeLane());
        assertEquals(2, copy.getBikeLaneWidth());
        assertTrue(copy.isIncludeMedian());
        assertEquals(CenterLineStyle.SINGLE_DASHED, copy.getCenterLineStyle());
        assertEquals("minecraft:white_concrete", copy.getMarkingMaterial());
        assertEquals(12, copy.getStreetlightSpacing());
        assertTrue(copy.isIncludeSlopeBatter());
    }

    @Test
    void batchDefaultsRoundTrip() {
        RoadNetworkManager.BatchEditDefaults defaults = new RoadNetworkManager.BatchEditDefaults(
            7,
            2,
            MaterialMix.single("minecraft:black_concrete"),
            true,
            1,
            false,
            2,
            "minecraft:stone",
            true,
            1,
            false,
            1,
            0,
            true,
            CenterLineStyle.DOUBLE_SOLID,
            "minecraft:white_concrete",
            true,
            false,
            2f,
            1f,
            "minecraft:dirt",
            "minecraft:stone",
            "minecraft:stone_bricks",
            8f
        );

        CrossSectionDraft draft = CrossSectionDraft.fromBatchDefaults(defaults);
        RoadNetworkManager.BatchEditDefaults roundTrip = draft.toBatchDefaults();

        // 旧宽 7 + 双黄占 2 → 反推 laneWidth=3，派生 W=8
        assertEquals(8, roundTrip.width());
        assertEquals(defaults.laneCount(), roundTrip.laneCount());
        assertEquals(defaults.includeBikeLane(), roundTrip.includeBikeLane());
        assertEquals(defaults.centerLineStyle(), roundTrip.centerLineStyle());
        assertEquals(defaults.maxSlope(), roundTrip.maxSlope());
    }

    @Test
    void toCrossSectionUsesLaneWidths() {
        CrossSectionDraft draft = new CrossSectionDraft();
        draft.setWidth(9);
        draft.setLaneCount(3);
        draft.setLaneWidthAt(0, 3);
        draft.setLaneWidthAt(1, 3);
        draft.setLaneWidthAt(2, 3);

        RoadCrossSection section = draft.toCrossSection();
        assertEquals(3, section.getCarriageway().getEffectiveLaneCount());
        assertEquals(3, section.getCarriageway().getLanes().get(0).getWidth());
    }
}
