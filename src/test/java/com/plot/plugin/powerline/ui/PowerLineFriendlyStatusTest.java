package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineFriendlyStatusTest {

    @Test
    void straightLineWithNormalSpacingIsOk() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
        PowerLineUiPresets.applySpacing(line, PowerLineUiPresets.SpacingDensity.NORMAL);

        PowerLineFriendlyStatus.SpacingEvaluation evaluation =
            PowerLineFriendlyStatus.evaluateSpacing(line);

        assertEquals(PowerLineFriendlyStatus.SpacingKind.OK, evaluation.kind());
    }

    @Test
    void nullLineReportsInvalidSettings() {
        assertEquals(
            PowerLineFriendlyStatus.SpacingKind.INVALID_SETTINGS,
            PowerLineFriendlyStatus.evaluateSpacing(null).kind());
    }

    @Test
    void tightMinSpacingCanFlagTooClose() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10)));
        line.setMaxPoleSpacing(20.0);
        line.setMinPoleSpacing(15.0);

        assertEquals(
            PowerLineFriendlyStatus.SpacingKind.TOO_CLOSE,
            PowerLineFriendlyStatus.evaluateSpacing(line).kind());
    }

    @Test
    void cornerPathHasSitesAtMandatoryPoints() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10)));

        assertEquals(
            PowerLineFriendlyStatus.CornerKind.OK,
            PowerLineFriendlyStatus.evaluateCornerPoles(line).kind());
    }

    @Test
    void straightPathCoversEndpoints() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));

        assertEquals(
            PowerLineFriendlyStatus.CornerKind.OK,
            PowerLineFriendlyStatus.evaluateCornerPoles(line).kind());
    }
}
