package com.plot.plugin.road.model.section;

/**
 * 变截面相邻桩号之间的过渡方式。
 */
public enum VariableCrossSectionTransitionMode {
    /** 阶梯：到桩号立刻切换并保持到下一桩号（默认）。 */
    STEP,
    /** 平滑：相邻桩号间对宽度等连续量线性插值；车道数/材质等离散量取较近侧。 */
    SMOOTH;

    public static VariableCrossSectionTransitionMode fromStorage(String raw) {
        if (raw == null || raw.isBlank()) {
            return STEP;
        }
        try {
            return VariableCrossSectionTransitionMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return STEP;
        }
    }
}
