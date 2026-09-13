package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.SingleTowerStyleFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class StyleEditTargetTest {

    @Test
    void styleEditTargetDefaultsToLine() {
        PowerLinePluginState state = new PowerLinePluginState();
        assertEquals(StyleEditTarget.LINE, state.getStyleEditTarget());
    }

    @Test
    void clearingLineSelectionDoesNotSwitchStyleTarget() {
        PowerLinePluginState state = new PowerLinePluginState();
        PowerLineFootprint line = new PowerLineFootprint(List.of(new com.plot.api.geometry.Vec2d(0, 0), new com.plot.api.geometry.Vec2d(10, 0)));
        state.getProject().addLine(line);
        state.getSelection().select(line.getId(), false);
        state.setStyleEditTarget(StyleEditTarget.LINE);

        state.getSelection().clear();

        assertEquals(StyleEditTarget.LINE, state.getStyleEditTarget());
        assertNotSame(line.getId(), state.getSingleTowerStyle().getId());
        assertEquals(SingleTowerStyleFootprint.STYLE_ID, state.getSingleTowerStyle().getId());
    }
}
