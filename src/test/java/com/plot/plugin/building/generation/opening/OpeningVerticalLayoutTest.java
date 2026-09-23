package com.plot.plugin.building.generation.opening;

import com.plot.plugin.building.model.spec.OpeningSpec;
import com.plot.plugin.building.generation.opening.OpeningPlacementResolver.ResolvedOpening;
import org.junit.jupiter.api.Test;

import java.util.List;

import com.plot.api.geometry.Vec2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpeningVerticalLayoutTest {

    private static final List<Vec2d> BASE = List.of(
        new Vec2d(0, 0),
        new Vec2d(8, 0),
        new Vec2d(8, 8),
        new Vec2d(0, 8)
    );

    @Test
    void zeroSillStartsOnFloorSlabTop() {
        ResolvedOpening resolved = OpeningPlacementResolver.resolve(
            OpeningSpec.window(0, 0.5, 0, 1, 2, 0), BASE, 64, 4);
        assertNotNull(resolved);
        assertEquals(65, resolved.startY());
    }

    @Test
    void sillIsMeasuredAboveFloorSlabTop() {
        ResolvedOpening resolved = OpeningPlacementResolver.resolve(
            OpeningSpec.window(0, 0.5, 0, 1, 2, 1), BASE, 64, 4);
        assertNotNull(resolved);
        assertEquals(66, resolved.startY());
    }

    @Test
    void doorStartsOnFloorSlabBlock() {
        ResolvedOpening resolved = OpeningPlacementResolver.resolve(
            OpeningSpec.door(0, 0.5, 0, 1, 2), BASE, 64, 4);
        assertNotNull(resolved);
        assertEquals(64, resolved.startY());
    }

    @Test
    void maxWindowHeightAccountsForSlabTopOffset() {
        assertEquals(2, OpeningVerticalLayout.maxWindowHeight(4, 1));
        assertEquals(3, OpeningVerticalLayout.maxWindowHeight(4, 0));
    }
}
