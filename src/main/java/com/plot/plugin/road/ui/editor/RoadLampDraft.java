package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.lamp.RoadLampPlacement;
import com.plot.plugin.road.lamp.RoadLampStyle;

import java.util.List;

/** The editable structure stays intact while the controls describe an invalid combination. */
record RoadLampDraft(String name, int height, RoadLampStyle.Material material, boolean plinth,
                    List<RoadLampStyle.Level> levels, RoadLampStyle.Detail detail) {
    RoadLampDraft {
        levels = List.copyOf(levels);
    }

    static RoadLampDraft from(RoadLampStyle style) {
        return new RoadLampDraft(style.name(), style.height(), style.material(), style.plinth(), style.levels(), style.detail());
    }

    boolean hasInvalidName() {
        String trimmed = name.strip();
        return trimmed.isEmpty() || trimmed.length() > 80;
    }

    RoadLampStyle validStyle() {
        try {
            return new RoadLampStyle(name, height, material, plinth, levels, detail);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    RoadLampPlacement resolve(RoadLampPlacement placement) {
        var style = validStyle();
        return style == null ? null : new RoadLampPlacement(style, placement.sides(), placement.spacing(),
            placement.inset(), placement.junctionSetback(), placement.enabled(), placement.bridgeAllowed());
    }
}
