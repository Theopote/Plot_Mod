package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerLineLineSelectorTest {

    @Test
    void emptySelectionUsesNoneEntry() {
        List<PowerLineFootprint> lines = List.of(
            new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0))),
            new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(20, 0))));
        assertEquals(0, PowerLineUiWidgets.resolveLineSelectorIndex("", lines));
        assertEquals(0, PowerLineUiWidgets.resolveLineSelectorIndex(null, lines));
    }

    @Test
    void primarySelectionMapsToComboIndex() {
        PowerLineFootprint first = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLineFootprint second = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(20, 0)));
        List<PowerLineFootprint> lines = List.of(first, second);
        assertEquals(1, PowerLineUiWidgets.resolveLineSelectorIndex(first.getId(), lines));
        assertEquals(2, PowerLineUiWidgets.resolveLineSelectorIndex(second.getId(), lines));
    }

    @Test
    void unknownSelectionFallsBackToNoneEntry() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        assertEquals(0, PowerLineUiWidgets.resolveLineSelectorIndex("missing", List.of(line)));
    }
}
