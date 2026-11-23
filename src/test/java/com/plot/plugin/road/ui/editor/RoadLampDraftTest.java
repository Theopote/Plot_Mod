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

    @Test
    void addingLevelsRaisesShortPolesInsteadOfCreatingAnInvalidDraft() {
        var top = new RoadLampStyle.Level(4, RoadLampStyle.Layout.SINGLE, RoadLampStyle.Mount.TOP,
            1, 1, RoadLampStyle.Light.LANTERN);
        var shortPole = List.of(top);

        var two = RoadLampDraft.withLevelCount(5, shortPole, 2);
        assertEquals(8, two.height());
        assertEquals(List.of(4, 7), two.levels().stream().map(RoadLampStyle.Level::y).toList());
        assertNotNull(new RoadLampDraft("Raised", two.height(), RoadLampStyle.Material.METAL, false,
            two.levels(), RoadLampStyle.Detail.NONE).validStyle());

        var three = RoadLampDraft.withLevelCount(two.height(), two.levels(), 3);
        assertEquals(11, three.height());
        assertEquals(List.of(4, 7, 10), three.levels().stream().map(RoadLampStyle.Level::y).toList());
        assertNotNull(new RoadLampDraft("Raised", three.height(), RoadLampStyle.Material.METAL, false,
            three.levels(), RoadLampStyle.Detail.NONE).validStyle());
    }

    @Test
    void reconcileLevelsClampsCollisionAfterHeightShrink() {
        var levels = List.of(
            new RoadLampStyle.Level(4, RoadLampStyle.Layout.TWIN, RoadLampStyle.Mount.TOP, 1, 1, RoadLampStyle.Light.LANTERN),
            new RoadLampStyle.Level(7, RoadLampStyle.Layout.TWIN, RoadLampStyle.Mount.TOP, 1, 1, RoadLampStyle.Light.LANTERN),
            new RoadLampStyle.Level(10, RoadLampStyle.Layout.TWIN, RoadLampStyle.Mount.TOP, 1, 1, RoadLampStyle.Light.LANTERN));
        // 杆高从 11 降到 8 时，旧层高会撞顶；应抬杆或重排到合法组合。
        var reconciled = RoadLampDraft.reconcileLevels(8, levels);
        assertNotNull(new RoadLampDraft(
            "Packed", reconciled.height(), RoadLampStyle.Material.METAL, false,
            reconciled.levels(), RoadLampStyle.Detail.NONE).validStyle());
        assertEquals(reconciled.height() - 1, reconciled.levels().getLast().y());
        assertTrue(reconciled.levels().get(0).y() < reconciled.levels().get(1).y());
        assertTrue(reconciled.levels().get(1).y() < reconciled.levels().get(2).y());
    }

    @Test
    void nonTopLevelMaxYLeavesRoomForTopMount() {
        var top = new RoadLampStyle.Level(10, RoadLampStyle.Layout.TWIN, RoadLampStyle.Mount.TOP,
            1, 1, RoadLampStyle.Light.LANTERN);
        assertEquals(8, RoadLampDraft.levelLowest(top) - 2);
    }
}
