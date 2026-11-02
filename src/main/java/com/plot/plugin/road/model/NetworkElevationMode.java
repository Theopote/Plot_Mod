package com.plot.plugin.road.model;

/**
 * 路网级标高生成模式：逐路设计或全网统一平路。
 */
public enum NetworkElevationMode {
    /** 逐路 {@code FLAT}/{@code EDITED}/{@code AUTO} 权威。 */
    PER_ROAD,
    /** 全网统一标高平路；生成侧纵向最高优先级。 */
    UNIFORM_FLAT;

    public static NetworkElevationMode fromStored(String value) {
        if (value == null || value.isBlank()) {
            return PER_ROAD;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return PER_ROAD;
        }
    }
}
