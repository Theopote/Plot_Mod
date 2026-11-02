package com.plot.plugin.road.model;

/**
 * 铁路路口铺轨目标：装饰视觉 vs 尽量可通行（仍非完整道岔运营系统）。
 */
public enum RailwayJunctionMode {
    /**
     * 默认：直股连通骨架 + 动力/探测装饰标记；外观清晰，不保证矿车任意转向。
     */
    DECORATIVE,
    /**
     * 可通行优先：只保留普通铁轨直股骨架与进口延续，不加动力/探测装饰，减少 shape 冲突。
     */
    PASSABLE;

    public static RailwayJunctionMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return DECORATIVE;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return DECORATIVE;
        }
    }

    public boolean placesDecorMarkers() {
        return this == DECORATIVE;
    }
}
