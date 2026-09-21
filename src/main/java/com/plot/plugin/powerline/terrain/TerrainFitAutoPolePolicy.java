package com.plot.plugin.powerline.terrain;

import com.plot.plugin.powerline.model.PowerLineFootprint;

/**
 * 地形自动补塔策略：补塔应适可而止，避免为净空在短档距内密集插杆。
 */
public final class TerrainFitAutoPolePolicy {
    /** 自动补塔沿路最小间距下限（格）。 */
    public static final double ABSOLUTE_MIN_INSERT_SPACING = 10.0;
    /** 相对用户最大档距的最小补塔间距比例。 */
    public static final double RELATIVE_MIN_INSERT_SPACING_RATIO = 0.35;
    /** 预览阶段单条线路最多保留的自动补塔约束数。 */
    public static final int MAX_AUTO_LAYOUT_CONSTRAINTS = 6;

    private TerrainFitAutoPolePolicy() {
    }

    /**
     * 地形补塔与相邻杆塔/约束之间的最小沿路间距（格）。
     * 与用户设置的最大档距联动，避免在已很短的档距内继续二分插杆。
     */
    public static double minimumInsertSpacing(PowerLineFootprint line) {
        if (line == null) {
            return ABSOLUTE_MIN_INSERT_SPACING;
        }
        double maxSpacing = Math.max(
            PowerLineFootprint.MIN_CONFIGURABLE_SPACING,
            line.getMaxPoleSpacing());
        return Math.max(ABSOLUTE_MIN_INSERT_SPACING, maxSpacing * RELATIVE_MIN_INSERT_SPACING_RATIO);
    }
}
