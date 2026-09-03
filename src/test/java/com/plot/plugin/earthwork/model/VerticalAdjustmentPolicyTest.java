package com.plot.plugin.earthwork.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalAdjustmentPolicyTest {

    @Test
    void typeDefaultsMatchEngineeringRoles() {
        assertEquals(
            VerticalAdjustmentPolicy.Mode.LOCKED,
            VerticalAdjustmentPolicy.defaultFor(
                GradingZoneType.BUILDING_PAD, true, DesignSurfaceKind.CONSTANT_ELEVATION).getMode());
        assertEquals(
            VerticalAdjustmentPolicy.Mode.DERIVED,
            VerticalAdjustmentPolicy.defaultFor(
                GradingZoneType.EXCAVATION_PIT, true, DesignSurfaceKind.EXCAVATION_PIT).getMode());
        VerticalAdjustmentPolicy road = VerticalAdjustmentPolicy.defaultFor(
            GradingZoneType.ROAD_CORRIDOR, true, DesignSurfaceKind.ROAD_CORRIDOR);
        assertEquals(VerticalAdjustmentPolicy.Mode.LOCKED, road.getMode());
        assertFalse(road.allowsVerticalAdjustment());

        VerticalAdjustmentPolicy landscape = VerticalAdjustmentPolicy.defaultFor(
            GradingZoneType.LANDSCAPE, true, DesignSurfaceKind.BEST_FIT_PLANE);
        assertEquals(VerticalAdjustmentPolicy.Mode.ADJUSTABLE, landscape.getMode());
        assertEquals(-3, landscape.getMinOffset());
        assertEquals(3, landscape.getMaxOffset());
        assertEquals(0.5f, landscape.getWeight(), 1e-6f);

        assertTrue(VerticalAdjustmentPolicy.defaultFor(
            GradingZoneType.FLAT, true, DesignSurfaceKind.LEVEL_PAD).allowsVerticalAdjustment());
        assertFalse(VerticalAdjustmentPolicy.defaultFor(
            GradingZoneType.FLAT, false, DesignSurfaceKind.LEVEL_PAD).allowsVerticalAdjustment());
    }

    @Test
    void weightScalesUniformOnlyThenClamps() {
        VerticalAdjustmentPolicy policy = VerticalAdjustmentPolicy.adjustable(3, 0.5f);
        assertEquals(2, policy.applyProposedOffset(0, 4));
        assertEquals(3, policy.applyProposedOffset(10, 0));
        assertEquals(3, policy.applyProposedOffset(2, 4));
        assertEquals(0, VerticalAdjustmentPolicy.locked().applyProposedOffset(5, 5));
        assertEquals(0, VerticalAdjustmentPolicy.derived().applyProposedOffset(5, 5));
    }

    @Test
    void boundedClampsAllocationWithoutChangingWeightSemantics() {
        VerticalAdjustmentPolicy road = VerticalAdjustmentPolicy.bounded(1, 1.0f);
        assertEquals(1, road.applyProposedOffset(10, 0));
        assertEquals(-1, road.applyProposedOffset(-8, -2));
        assertEquals(1, road.applyProposedOffset(0, 5));
    }
}
