package com.plot.plugin.road.tunnel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelSectionDisplayModelTest {

    private static final int ROAD_Y = 16;
    private static final int ENVELOPE = 5;

    @ParameterizedTest
    @EnumSource(TunnelShape.class)
    void displayRangeStartsAtRoadY(TunnelShape shape) {
        TunnelSectionDisplayModel model = TunnelSectionDisplayModel.build(
            shape, ENVELOPE, 1, 6, 1, ROAD_Y, TunnelLightingMode.NONE, false);
        assertEquals(ROAD_Y, model.displayMinY());
        assertTrue(model.displayMaxY() >= ROAD_Y);
        for (TunnelSectionDisplayModel.Cell cell : model.cells()) {
            assertTrue(cell.y() >= ROAD_Y, shape + " cell below road: " + cell);
        }
    }

    @ParameterizedTest
    @EnumSource(TunnelShape.class)
    void roadLayerCoversEnvelope(TunnelShape shape) {
        TunnelSectionDisplayModel model = TunnelSectionDisplayModel.build(
            shape, ENVELOPE, 1, 6, 1, ROAD_Y, TunnelLightingMode.NONE, false);
        long roadCells = model.cells().stream()
            .filter(c -> c.kind() == TunnelSectionDisplayModel.DisplayKind.ROAD)
            .count();
        assertEquals(ENVELOPE, roadCells, shape.name());
    }

    @Test
    void circularAndEllipticalHideInvertInDisplayButKeepMaskInvert() {
        TunnelCrossSectionMask circularMask = TunnelCrossSectionMask.build(
            TunnelShape.CIRCULAR, ENVELOPE, 1, 4, 1, ROAD_Y, false);
        boolean maskInvert = false;
        for (int y = circularMask.minY(); y < ROAD_Y; y++) {
            if (circularMask.kindAt(0, y) != TunnelCrossSectionMask.CellKind.OUTSIDE) {
                maskInvert = true;
                break;
            }
        }
        assertTrue(maskInvert);

        TunnelSectionDisplayModel model = TunnelSectionDisplayModel.build(
            TunnelShape.CIRCULAR, ENVELOPE, 1, 4, 1, ROAD_Y, TunnelLightingMode.NONE, false);
        assertEquals(ROAD_Y, model.displayMinY());
        assertFalse(model.cells().stream().anyMatch(c -> c.y() < ROAD_Y));
    }

    @Test
    void lightsAndAccentsOverlayWhenEnabled() {
        TunnelSectionDisplayModel dark = TunnelSectionDisplayModel.build(
            TunnelShape.RECTANGULAR, ENVELOPE, 1, 6, 1, ROAD_Y, TunnelLightingMode.NONE, false);
        assertTrue(dark.cells().stream().noneMatch(c ->
            c.kind() == TunnelSectionDisplayModel.DisplayKind.WALL_LIGHT
                || c.kind() == TunnelSectionDisplayModel.DisplayKind.CEILING_LIGHT
                || c.kind() == TunnelSectionDisplayModel.DisplayKind.ACCENT));

        TunnelSectionDisplayModel lit = TunnelSectionDisplayModel.build(
            TunnelShape.RECTANGULAR, ENVELOPE, 1, 6, 1, ROAD_Y,
            TunnelLightingMode.WALL_AND_CEILING, true);
        assertTrue(lit.cells().stream().anyMatch(c ->
            c.kind() == TunnelSectionDisplayModel.DisplayKind.WALL_LIGHT
                || c.kind() == TunnelSectionDisplayModel.DisplayKind.CEILING_LIGHT));
        assertTrue(lit.cells().stream().anyMatch(c ->
            c.kind() == TunnelSectionDisplayModel.DisplayKind.ACCENT));
    }

    @Test
    void circularEffectiveClearHeightIsClamped() {
        TunnelSectionDisplayModel model = TunnelSectionDisplayModel.build(
            TunnelShape.CIRCULAR, ENVELOPE, 1, 12, 1, ROAD_Y, TunnelLightingMode.NONE, false);
        int half = (ENVELOPE + 2) / 2;
        assertEquals(
            TunnelProfile.effectiveCircularClearHeight(half, 12),
            model.effectiveClearHeight());
    }
}
