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

    /** 灯位占用的最低方块 Y（链条/檐挂会向下延伸）。 */
    static int levelLowest(RoadLampStyle.Level level) {
        return switch (level.mount()) {
            case CHAIN -> level.y() - level.chain() - 1;
            case HOOD -> level.y() - 1;
            case TOP -> level.y();
        };
    }

    /**
     * 按杆高与相邻层约束钳制各层 Y；若层数在当前杆高下无法排开则自动抬高杆身，
     * 避免草稿长期 invalid、预览停在上一份合法样式。
     */
    static ExpandedLevels reconcileLevels(int height, List<RoadLampStyle.Level> levels) {
        if (levels == null || levels.isEmpty()) {
            return new ExpandedLevels(Math.clamp(height, 5, 24), List.of());
        }
        int h = Math.clamp(height, 5, 24);
        List<RoadLampStyle.Level> packed = levels;
        for (int guard = 0; guard < 24; guard++) {
            packed = packLevels(h, levels);
            if (new RoadLampDraft("x", h, RoadLampStyle.Material.METAL, false, packed, RoadLampStyle.Detail.NONE)
                    .validStyle() != null) {
                return new ExpandedLevels(h, packed);
            }
            if (h >= 24) {
                break;
            }
            h++;
        }
        return new ExpandedLevels(h, packed);
    }

    private static List<RoadLampStyle.Level> packLevels(int height, List<RoadLampStyle.Level> levels) {
        var next = new ArrayList<>(levels);
        var top = next.getLast();
        next.set(next.size() - 1, copyLevel(top, height - 1));

        for (int i = next.size() - 2; i >= 0; i--) {
            var level = next.get(i);
            int maxY = levelLowest(next.get(i + 1)) - 2;
            int floor = minLevelY(0, level.mount(), level.chain());
            int y = Math.clamp(level.y(), floor, Math.max(floor, maxY));
            if (y > maxY) {
                y = maxY;
            }
            next.set(i, copyLevel(level, y));
        }

        int previousY = 0;
        for (int i = 0; i < next.size(); i++) {
            var level = next.get(i);
            boolean isTop = i == next.size() - 1;
            int minY = minLevelY(previousY, level.mount(), level.chain());
            int maxY = isTop ? height - 1 : levelLowest(next.get(i + 1)) - 2;
            int y = isTop ? height - 1 : (minY <= maxY ? Math.clamp(level.y(), minY, maxY) : maxY);
            next.set(i, copyLevel(level, y));
            previousY = y + 1;
        }
        return List.copyOf(next);
    }

    private static RoadLampStyle.Level copyLevel(RoadLampStyle.Level level, int y) {
        return new RoadLampStyle.Level(
            y, level.layout(), level.mount(), level.reach(), level.chain(), level.light());
    }

    static int minLevelY(int previousY, RoadLampStyle.Mount mount, int chain) {
        int minByPrev = switch (mount) {
            case CHAIN -> previousY + chain + 2;
            case HOOD, TOP -> previousY + 2;
        };
        int minByFloor = switch (mount) {
            case CHAIN -> chain + 5;
            case HOOD -> 5;
            case TOP -> 4;
        };
        return Math.max(minByFloor, minByPrev);
    }
}
