package com.plot.plugin.earthwork.model;

/**
 * 土方插件工作模式：快速平整（任务导向）与建造（完整功能）。
 * <p>
 * 旧配置中的 {@code LEARN} 读入时映射为 {@link #BUILDER}，避免丢失高级设置入口。
 */
public enum EarthworkWorkMode {
    QUICK,
    BUILDER;

    public static EarthworkWorkMode fromId(String id) {
        if (id == null || id.isBlank()) {
            return QUICK;
        }
        String normalized = id.trim().toUpperCase();
        // 已取消独立学习模式：旧 LEARN 配置并入建造
        if ("LEARN".equals(normalized)) {
            return BUILDER;
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return QUICK;
        }
    }

    /** 建造模式使用区域/编辑/生成三标签。 */
    public boolean showsEngineeringTabs() {
        return this == BUILDER;
    }

    /**
     * 建造模式展示工程分析：方量报告、材料参数、跨场地汇总、导出等。
     */
    public boolean showsEngineeringMetrics() {
        return this == BUILDER;
    }

    /** 建造：挖填条、世界改动、热力图、Before/After。 */
    public boolean showsBuilderVisuals() {
        return this == BUILDER;
    }

    /** 建造：平衡曲线、推荐高度、剖面等分析视图。 */
    public boolean showsAnalysisVisuals() {
        return this == BUILDER;
    }

    /** 建造：允许多场地 PROJECT 统计范围。 */
    public boolean allowsProjectBalanceScope() {
        return this == BUILDER;
    }

    /** 建造：约束分区竖向优化（CONSTRAINED_ZONE_OPTIMIZATION）。 */
    public boolean allowsConstrainedZoneOptimization() {
        return this == BUILDER;
    }

    public String i18nKey() {
        return "plugin.earthwork.work_mode." + name().toLowerCase();
    }
}
