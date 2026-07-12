package com.plot.plugin.road.model;

/**
 * 路口标线生成策略：自动（按启发式）/ 强制开启 / 强制关闭。
 */
public enum JunctionMarkingSetting {
    AUTO,
    ON,
    OFF;

    public boolean resolve(boolean autoEnabled) {
        return switch (this) {
            case ON -> true;
            case OFF -> false;
            case AUTO -> autoEnabled;
        };
    }

    public static JunctionMarkingSetting fromString(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return AUTO;
        }
    }
}
