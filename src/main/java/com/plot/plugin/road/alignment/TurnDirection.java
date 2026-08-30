package com.plot.plugin.road.alignment;

/**
 * 圆曲线转向。
 */
public enum TurnDirection {
    LEFT,
    RIGHT;

    public double sign() {
        return this == LEFT ? 1.0 : -1.0;
    }

    public static TurnDirection fromStored(String value) {
        if (value == null || value.isBlank()) {
            return LEFT;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return LEFT;
        }
    }
}
