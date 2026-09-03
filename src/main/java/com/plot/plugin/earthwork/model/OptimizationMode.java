package com.plot.plugin.earthwork.model;

/**
 * 竖向<strong>优化方式</strong>：是否以及如何修改设计标高。
 * <p>
 * 与 {@link BalanceScope} 正交。调配矩阵（Mode A）始终只报告土方怎么搬，不改标高；
 * 本枚举只控制 Mode B。
 */
public enum OptimizationMode {
    /** 不改设计面；仅按 BalanceScope 统计/报告。 */
    NONE,
    /** 在可调分区上施加统一竖向平移 ΔY。 */
    UNIFORM_VERTICAL_SHIFT,
    /** 在 {@link VerticalAdjustmentPolicy} 约束下优化各可调分区标高。 */
    CONSTRAINED_ZONE_OPTIMIZATION;

    public static OptimizationMode fromId(String id) {
        if (id == null || id.isBlank()) {
            return NONE;
        }
        return switch (id.trim().toUpperCase()) {
            case "NONE" -> NONE;
            case "UNIFORM_VERTICAL_SHIFT", "UNIFORM_OFFSET" -> UNIFORM_VERTICAL_SHIFT;
            case "CONSTRAINED_ZONE_OPTIMIZATION",
                 "EARTHWORK_OPTIMIZATION",
                 "ZONE_ALLOCATION" -> CONSTRAINED_ZONE_OPTIMIZATION;
            default -> NONE;
        };
    }

    public boolean modifiesDesign() {
        return this != NONE;
    }

    public boolean isUniformVerticalShift() {
        return this == UNIFORM_VERTICAL_SHIFT;
    }

    public boolean isConstrainedZoneOptimization() {
        return this == CONSTRAINED_ZONE_OPTIMIZATION;
    }
}
