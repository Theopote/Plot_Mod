package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.lamp.RoadLampPlacement;
import com.plot.plugin.road.lamp.RoadLampPresets;
import com.plot.plugin.road.lamp.RoadLampStyle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoadLampDraftTest {
    @Test
    void invalidLevelsRetainEditsAndBlockTheCombinedPlacementUntilCorrected() {
        var style = RoadLampPresets.ALL.getFirst().style();
        var controls = new RoadLampPlacement(style, RoadLampPlacement.Sides.STAGGERED, 24, 1, 4, true, false);
        var lower = new RoadLampStyle.Level(5, RoadLampStyle.Layout.TWIN, RoadLampStyle.Mount.TOP,
            2, 1, RoadLampStyle.Light.LANTERN);
        var upper = new RoadLampStyle.Level(7, RoadLampStyle.Layout.TWIN, RoadLampStyle.Mount.CHAIN,
            2, 2, RoadLampStyle.Light.LANTERN);
        var invalid = new RoadLampDraft("Two levels", 8, style.material(), true, List.of(lower, upper), RoadLampStyle.Detail.TIMBER);
        assertNull(invalid.resolve(controls));
        assertFalse(invalid.hasInvalidName());
        assertEquals(List.of(lower, upper), invalid.levels());

        var raised = new RoadLampStyle.Level(11, upper.layout(), upper.mount(), upper.reach(), upper.chain(), upper.light());
        var corrected = new RoadLampDraft(invalid.name(), 12, invalid.material(), invalid.plinth(), List.of(lower, raised), invalid.detail());
        var applied = corrected.resolve(controls);
        assertNotNull(applied);
        assertEquals(24, applied.spacing());
        assertEquals(RoadLampPlacement.Sides.STAGGERED, applied.sides());
        assertEquals(List.of(lower, raised), applied.style().levels());
    }
}
