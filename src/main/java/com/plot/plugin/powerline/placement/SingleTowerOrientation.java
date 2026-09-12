package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;

/** 单塔放置朝向：世界坐标正交四方向（0°/90°/180°/270°）。 */
public final class SingleTowerOrientation {
    public static final int QUADRANT_COUNT = 4;

    private SingleTowerOrientation() {
    }

    public static Vec2d tangentForQuadrant(int quadrant) {
        return switch (normalize(quadrant)) {
            case 0 -> new Vec2d(1, 0);
            case 1 -> new Vec2d(0, 1);
            case 2 -> new Vec2d(-1, 0);
            default -> new Vec2d(0, -1);
        };
    }

    public static int rotateClockwise(int quadrant) {
        return normalize(quadrant + 1);
    }

    public static int rotateCounterClockwise(int quadrant) {
        return normalize(quadrant + QUADRANT_COUNT - 1);
    }

    public static int fromWheelDelta(int quadrant, float wheelDelta) {
        if (wheelDelta > 0f) {
            return rotateClockwise(quadrant);
        }
        if (wheelDelta < 0f) {
            return rotateCounterClockwise(quadrant);
        }
        return normalize(quadrant);
    }

    public static String labelKey(int quadrant) {
        return "plugin.powerline.single_tower.direction."
            + switch (normalize(quadrant)) {
                case 0 -> "east_west";
                case 1 -> "north_south";
                case 2 -> "west_east";
                default -> "south_north";
            };
    }

    private static int normalize(int quadrant) {
        return ((quadrant % QUADRANT_COUNT) + QUADRANT_COUNT) % QUADRANT_COUNT;
    }
}
