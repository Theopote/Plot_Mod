package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.lamp.RoadLampPlacement;
import com.plot.plugin.road.lamp.RoadLampStyle;

import java.util.ArrayList;
import java.util.List;

/** The editable structure stays intact while the controls describe an invalid combination. */
record RoadLampDraft(String name, int height, RoadLampStyle.Material material, boolean plinth,
                    List<RoadLampStyle.Level> levels, RoadLampStyle.Detail detail) {
    private static final int MIN_LEVEL_Y = 4;
    private static final int DEFAULT_LEVEL_GAP = 3;

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

    /**
     * 调整层数：新增下层默认落在上一层下方 {@value #DEFAULT_LEVEL_GAP} 格；
     * 若低于合法最低灯位则整体抬高杆高与既有层，避免「加一层立刻无效」。
     */
    static ExpandedLevels withLevelCount(int height, List<RoadLampStyle.Level> levels, int count) {
        int target = Math.max(1, Math.min(3, count));
        var next = new ArrayList<>(levels);
        int h = height;
        while (next.size() > target) {
            next.removeFirst();
        }
        while (next.size() < target) {
            int newY = next.getFirst().y() - DEFAULT_LEVEL_GAP;
            if (newY < MIN_LEVEL_Y) {
                int raise = Math.min(24 - h, MIN_LEVEL_Y - newY);
                if (raise > 0) {
                    h += raise;
                    for (int i = 0; i < next.size(); i++) {
                        var level = next.get(i);
                        next.set(i, new RoadLampStyle.Level(
                            level.y() + raise, level.layout(), level.mount(), level.reach(), level.chain(), level.light()));
                    }
                    newY += raise;
                }
                newY = Math.max(newY, MIN_LEVEL_Y);
            }
            next.addFirst(new RoadLampStyle.Level(
                newY, RoadLampStyle.Layout.TWIN, RoadLampStyle.Mount.TOP, 1, 1, RoadLampStyle.Light.LANTERN));
        }
        return new ExpandedLevels(h, List.copyOf(next));
    }

    record ExpandedLevels(int height, List<RoadLampStyle.Level> levels) {
    }
}
