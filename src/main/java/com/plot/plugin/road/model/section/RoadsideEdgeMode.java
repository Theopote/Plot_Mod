package com.plot.plugin.road.model.section;

/**
 * 路肩外侧边缘处理：无 / 放坡 / 挡土墙（竖直收边）。
 */
public enum RoadsideEdgeMode {
    NONE,
    SLOPE_BATTER,
    RETAINING_WALL;

    public boolean isSlopeBatter() {
        return this == SLOPE_BATTER;
    }

    public boolean isRetainingWall() {
        return this == RETAINING_WALL;
    }

    public boolean engagesEarthwork() {
        return this == SLOPE_BATTER || this == RETAINING_WALL;
    }

    public static RoadsideEdgeMode fromId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return valueOf(id.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * 旧存档 {@code enabled} 布尔迁移：true→边坡，false→无；null 表示继承。
     */
    public static RoadsideEdgeMode fromLegacyEnabled(Boolean enabled) {
        if (enabled == null) {
            return null;
        }
        return enabled ? SLOPE_BATTER : NONE;
    }
}
