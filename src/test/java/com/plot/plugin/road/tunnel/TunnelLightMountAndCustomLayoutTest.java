package com.plot.plugin.road.tunnel;

import com.plot.plugin.road.ui.TunnelCrossSectionPreviewRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelLightMountAndCustomLayoutTest {

    private static final int ROAD_Y = 64;
    private static final int ENVELOPE = 5;
    private static final int SIDE = 1;
    private static final int CLEAR = 6;
    private static final int LINING = 1;

    @ParameterizedTest
    @EnumSource(TunnelShape.class)
    void protrudingDefaultsOccupyAir(TunnelShape shape) {
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            shape, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y, false);
        List<TunnelLightPlacementResolver.LightPosition> lights = TunnelLightPlacementResolver.resolve(
            shape, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y,
            TunnelLightingMode.WALL_AND_CEILING,
            TunnelLightMount.PROTRUDING,
            null);
        for (var light : lights) {
            assertEquals(TunnelCrossSectionMask.CellKind.AIR, mask.kindAt(light.lateral(), light.y()), shape.name());
        }
    }

    @ParameterizedTest
    @EnumSource(TunnelShape.class)
    void embeddedDefaultsOccupyInnerFaceLining(TunnelShape shape) {
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            shape, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y, false);
        List<TunnelLightPlacementResolver.LightPosition> lights = TunnelLightPlacementResolver.resolve(
            shape, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y,
            TunnelLightingMode.WALL_AND_CEILING,
            TunnelLightMount.EMBEDDED,
            null);
        assertFalse(lights.isEmpty(), shape.name());
        for (var light : lights) {
            assertEquals(
                TunnelCrossSectionMask.CellKind.LINING,
                mask.kindAt(light.lateral(), light.y()),
                shape.name());
            assertTrue(
                TunnelLightPlacementResolver.isValidLightCell(
                    mask, light.lateral(), light.y(), ROAD_Y, TunnelLightMount.EMBEDDED),
                shape + " embedded light must sit on inner-face lining");
        }
    }

    @Test
    void customAnchorsReplaceModeDefaults() {
        TunnelLightAnchor only = new TunnelLightAnchor(
            0, 3, TunnelLightPlacementResolver.LightType.CEILING);
        List<TunnelLightPlacementResolver.LightPosition> lights = TunnelLightPlacementResolver.resolve(
            TunnelShape.RECTANGULAR, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y,
            TunnelLightingMode.WALL_AND_CEILING,
            TunnelLightMount.PROTRUDING,
            List.of(only));
        assertEquals(1, lights.size());
        assertEquals(0, lights.getFirst().lateral());
        assertEquals(ROAD_Y + 3, lights.getFirst().y());
    }

    @Test
    void invalidCustomAnchorsAreDropped() {
        TunnelLightAnchor outside = new TunnelLightAnchor(
            99, 3, TunnelLightPlacementResolver.LightType.WALL);
        List<TunnelLightPlacementResolver.LightPosition> lights = TunnelLightPlacementResolver.resolve(
            TunnelShape.RECTANGULAR, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y,
            TunnelLightingMode.WALL_AND_CEILING,
            TunnelLightMount.PROTRUDING,
            List.of(outside));
        assertTrue(lights.isEmpty());
    }

    @Test
    void emptyCustomLayoutMeansNoLightsEvenWhenModeHasDefaults() {
        List<TunnelLightPlacementResolver.LightPosition> lights = TunnelLightPlacementResolver.resolve(
            TunnelShape.RECTANGULAR, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y,
            TunnelLightingMode.WALL_AND_CEILING,
            TunnelLightMount.PROTRUDING,
            List.of());
        assertTrue(lights.isEmpty());
    }

    @Test
    void toggleSeedsCustomLayoutAndAddsCell() {
        TunnelStyle style = new TunnelStyle();
        style.setShape(TunnelShape.RECTANGULAR);
        style.setClearHeight(CLEAR);
        style.setSideClearance(SIDE);
        style.setLiningThickness(LINING);
        style.setLightingMode(TunnelLightingMode.NONE);
        style.setLightMount(TunnelLightMount.PROTRUDING);

        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            TunnelShape.RECTANGULAR, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y, false);
        int lateral = 0;
        int y = ROAD_Y + 3;
        assertEquals(TunnelCrossSectionMask.CellKind.AIR, mask.kindAt(lateral, y));

        assertTrue(TunnelLightPlacementResolver.toggleLightOnStyle(
            style, ENVELOPE, ROAD_Y, lateral, y));
        assertTrue(style.hasCustomLightLayout());
        assertEquals(1, style.getStoredLightAnchors().size());

        assertTrue(TunnelLightPlacementResolver.toggleLightOnStyle(
            style, ENVELOPE, ROAD_Y, lateral, y));
        assertTrue(style.hasCustomLightLayout());
        assertTrue(style.getStoredLightAnchors().isEmpty());
    }

    @Test
    void displayModelShowsEmbeddedLightsOnLiningCells() {
        TunnelSectionDisplayModel model = TunnelSectionDisplayModel.build(
            TunnelShape.RECTANGULAR, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y,
            TunnelLightingMode.WALL_AND_CEILING,
            TunnelLightMount.EMBEDDED,
            null,
            false);
        assertTrue(model.cells().stream().anyMatch(c ->
            c.kind() == TunnelSectionDisplayModel.DisplayKind.WALL_LIGHT
                || c.kind() == TunnelSectionDisplayModel.DisplayKind.CEILING_LIGHT));
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            TunnelShape.RECTANGULAR, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y, false);
        for (TunnelSectionDisplayModel.Cell cell : model.cells()) {
            if (cell.kind() == TunnelSectionDisplayModel.DisplayKind.WALL_LIGHT
                    || cell.kind() == TunnelSectionDisplayModel.DisplayKind.CEILING_LIGHT) {
                assertEquals(TunnelCrossSectionMask.CellKind.LINING, mask.kindAt(cell.lateral(), cell.y()));
            }
        }
    }

    @Test
    void persistenceRoundTripsMountAndAnchors() {
        TunnelStyle style = new TunnelStyle();
        style.setShape(TunnelShape.ARCH);
        style.setLightingMode(TunnelLightingMode.WALL_BANDS);
        style.setLightMount(TunnelLightMount.EMBEDDED);
        style.setLightAnchors(List.of(
            new TunnelLightAnchor(-2, 2, TunnelLightPlacementResolver.LightType.WALL),
            new TunnelLightAnchor(2, 2, TunnelLightPlacementResolver.LightType.WALL)));

        TunnelStylePersistence data = TunnelStylePersistence.from(style);
        assertNotNull(data);
        assertEquals("EMBEDDED", data.lightMount);
        assertEquals(2, data.lightAnchors.size());

        TunnelStyle restored = TunnelStylePersistence.fromData(data);
        assertNotNull(restored);
        assertEquals(TunnelLightMount.EMBEDDED, restored.getLightMount());
        assertTrue(restored.hasCustomLightLayout());
        assertEquals(2, restored.getStoredLightAnchors().size());
        assertEquals(style.getStoredLightAnchors(), restored.getStoredLightAnchors());
    }

    @Test
    void previewHitTestMapsMouseToCell() {
        TunnelCrossSectionPreviewRenderer.PreviewGridLayout layout =
            new TunnelCrossSectionPreviewRenderer.PreviewGridLayout(
                100f, 200f, 10f, -3, 24, 7, 9);
        TunnelCrossSectionPreviewRenderer.CellHit hit =
            TunnelCrossSectionPreviewRenderer.hitTestCell(layout, 100f + 25f, 200f + 15f);
        assertNotNull(hit);
        assertEquals(-1, hit.lateral()); // col 2 -> -3+2
        assertEquals(23, hit.y()); // row 1 -> 24-1
    }
}
