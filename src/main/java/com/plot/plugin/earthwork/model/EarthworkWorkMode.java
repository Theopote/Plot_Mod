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

    /** Builder/Learn：CUT/FILL、世界改动、热力图、Before/After。 */
    public boolean showsBuilderVisuals() {
        return this == BUILDER || this == LEARN;
    }

    /** Learn：平衡曲线、最少工作高度、剖面。Quick 仅在「查看分析」中展开。 */
    public boolean showsLearnVisuals() {
        return this == LEARN;
    }

    public String i18nKey() {
        return "plugin.earthwork.work_mode." + name().toLowerCase();
    }
}
