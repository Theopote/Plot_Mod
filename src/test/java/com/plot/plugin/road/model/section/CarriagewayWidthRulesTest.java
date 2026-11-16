package com.plot.plugin.road.model.section;

import com.plot.plugin.road.RoadParameterLimits;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CarriagewayWidthRulesTest {

    @Test
    void twoLanesThreeWideWithSingleCenterIsSeven() {
        assertEquals(
            7,
            CarriagewayWidthRules.derivedCarriagewayWidth(
                2, 3, CenterLineStyle.SINGLE_DASHED, true, 0));
        assertEquals(1, CarriagewayWidthRules.dividerBlocks(2, CenterLineStyle.SINGLE_DASHED, true));
    }

    @Test
    void twoLanesWithDoubleSolidCenterUsesTwoBlocks() {
        assertEquals(2, CarriagewayWidthRules.dividerBlocks(2, CenterLineStyle.DOUBLE_SOLID, true));
        assertEquals(
            8,
            CarriagewayWidthRules.derivedCarriagewayWidth(
                2, 3, CenterLineStyle.DOUBLE_SOLID, true, 0));
    }

    @Test
    void fourLanesWithWhiteSeamsAndDoubleCenter() {
        // 4 车道：中心双黄 2 + 两侧同向缝各 1 → 4 格分隔；4×3+4=16
        assertEquals(4, CarriagewayWidthRules.dividerBlocks(4, CenterLineStyle.DOUBLE_SOLID, true));
        assertEquals(
            16,
            CarriagewayWidthRules.derivedCarriagewayWidth(
                4, 3, CenterLineStyle.DOUBLE_SOLID, true, 0));
    }

    @Test
    void laneDividersWithoutCenterCountAllSeams() {
        assertEquals(3, CarriagewayWidthRules.dividerBlocks(4, CenterLineStyle.NONE, true));
        assertEquals(0, CarriagewayWidthRules.dividerBlocks(4, CenterLineStyle.NONE, false));
    }

    @Test
    void medianAddsToDerivedWidth() {
        assertEquals(
            9,
            CarriagewayWidthRules.derivedCarriagewayWidth(
                2, 3, CenterLineStyle.SINGLE_DASHED, true, 2));
    }

    @Test
    void inferUniformLaneWidthPrefersThreeForLegacySevenWideTwoLane() {
        assertEquals(
            3,
            CarriagewayWidthRules.inferUniformLaneWidth(
                7, 2, CenterLineStyle.SINGLE_DASHED, true, 0));
    }

    @Test
    void clampLaneTravelWidthHonorsAbsoluteBounds() {
        assertEquals(1, RoadParameterLimits.clampLaneTravelWidth(0));
        assertEquals(6, RoadParameterLimits.clampLaneTravelWidth(9));
        assertEquals(3, RoadParameterLimits.clampLaneTravelWidth(3));
        assertEquals(List.of(3, 3), CarriagewayWidthRules.uniformLaneWidths(2, 3));
    }
}
