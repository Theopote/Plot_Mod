package com.plot.plugin.earthwork.model;

/**
 * 土方插件工作模式。默认 Quick：主路径只做框选、高度、边缘、预览。
 */
public enum EarthworkWorkMode {
    QUICK,
    BUILDER,
    LEARN;

    public static EarthworkWorkMode fromId(String id) {
        if (id == null || id.isBlank()) {
            return QUICK;
        }
        try {
            return valueOf(id.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return QUICK;
        }
    }

    public boolean showsEngineeringTabs() {
        return this == BUILDER || this == LEARN;
    }

    public boolean showsLearningMetrics() {
        return this == LEARN;
    }

    /** 标高—方量曲线、剖面、面板热力图等完整分析仪表盘。 */
    public boolean showsInsightDashboard() {
        return this == LEARN;
    }

    public String i18nKey() {
        return "plugin.earthwork.work_mode." + name().toLowerCase();
    }
}
